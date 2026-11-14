package com.myra.assistant.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Network
import androidx.core.app.NotificationCompat
import com.myra.assistant.R
import com.myra.assistant.control.PhoneControl
import com.myra.assistant.data.ChatRepository
import com.myra.assistant.ai.AIResponseManager
import com.myra.assistant.ai.ConversationMemory
import com.myra.assistant.ai.IntentAnalyzer
import com.myra.assistant.ai.DynamicDecisionEngine
import com.myra.assistant.ui.main.MainActivity
import com.myra.assistant.utils.ApiKeys
import com.myra.assistant.utils.Constants
import com.myra.assistant.utils.Logger
import com.myra.assistant.utils.prefs
import com.myra.assistant.voice.AudioFocusManager
import com.myra.assistant.voice.AudioPlayer
import com.myra.assistant.voice.AudioRecorder
import com.myra.assistant.voice.VoiceActivityDetector
import com.myra.assistant.voice.VoiceStateManager
import com.myra.assistant.websocket.GeminiWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ForegroundVoiceService : Service() {
    private val TAG = "VOICE_SVC"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> Logger.e(TAG, "coroutine error: ${e.message}") })

    // Everything slow that is NOT audio (transcripts, chat, commands) runs here, in order, so the network
    // reader thread only ever moves voice data and a busy UI / disk can never make the voice stutter.
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "MYRA_Worker").apply { isDaemon = true } }
    private fun bg(block: () -> Unit) {
        try { worker.execute { try { block() } catch (e: Throwable) { Logger.e(TAG, "worker: ${e.message}") } } } catch (_: Exception) {}
    }

    // keep CPU + Wi-Fi radio awake for the whole voice session (Wi-Fi power-save = delayed / jittery packets)
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "myra:voice").apply {
                setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L)
            }
        } catch (e: Throwable) { Logger.e(TAG, "wakelock: ${e.message}") }
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= 29) android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                       else @Suppress("DEPRECATION") android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "myra:voice").apply { setReferenceCounted(false); acquire() }
        } catch (e: Throwable) { Logger.e(TAG, "wifilock: ${e.message}") }
    }
    private fun releaseLocks() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Throwable) {}
        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (_: Throwable) {}
        wakeLock = null; wifiLock = null
    }

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var vad: VoiceActivityDetector
    private lateinit var audioFocus: AudioFocusManager
    private var geminiClient: GeminiWebSocketClient? = null

    // reconnect / continuity state
    private var reconnectJob: Job? = null
    private var backoffMs = 1000L
    private var failStreak = 0
    private var lastConnectAt = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetwork: Network? = null

    // live transcript state for the current turn (voice -> chat)
    private val userTranscript = StringBuilder()
    private var assistantStarted = false
    private var suppressAssistantText = false

    companion object {
        var instance: ForegroundVoiceService? = null
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        try {
            startForeground(Constants.NOTIF_ID_VOICE, buildNotification())
        } catch (e: Exception) {
            // e.g. microphone permission not granted yet on Android 14+: stop quietly instead of crashing
            Logger.e(TAG, "startForeground failed: ${e.message}")
            isRunning = false
            instance = null
            stopSelf()
            return
        }
        ChatRepository.init(applicationContext)
        initComponents()
        acquireLocks()
        registerNetworkWatcher()
    }

    private fun initComponents() {
        audioFocus = AudioFocusManager(this)
        audioPlayer = AudioPlayer()

        audioPlayer.onPlaybackStarted = {
            VoiceStateManager.setSpeaking()
        }
        audioPlayer.onPlaybackFinished = {
            VoiceStateManager.setListening()
        }

        vad = VoiceActivityDetector(
            onSpeechStart = {
                VoiceStateManager.setListening()
            },
            onSpeechEnd = {
                VoiceStateManager.setThinking()
            }
        )

        audioRecorder = AudioRecorder(this) { chunk ->
            // Don't send audio while AI is speaking
            if (!VoiceStateManager.isAiSpeaking()) {
                vad.processChunk(chunk)
                geminiClient?.sendAudioChunk(chunk)
            }
        }

        val apiKey = ApiKeys.gemini(applicationContext)
        if (apiKey.isNotEmpty()) {
            connectGemini(apiKey)
        }
    }

    private fun connectGemini(apiKey: String) {
        // Pin this connection to the network that is active right now (Wi-Fi or mobile data).
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            GeminiWebSocketClient.SmartDns.boundNetwork = if (failStreak >= 4) null else cm.activeNetwork
            GeminiWebSocketClient.SmartDns.forceDoh = failStreak >= 2 || GeminiWebSocketClient.SmartDns.forceDoh
            GeminiWebSocketClient.resetConnections()
        } catch (_: Throwable) { }
        // Only ONE live socket at a time: closing the old one first means it can never deliver duplicate audio.
        geminiClient?.disconnect()
        audioPlayer.dropPending()
        lastConnectAt = System.currentTimeMillis()
        userTranscript.clear()
        assistantStarted = false
        suppressAssistantText = false

        geminiClient = GeminiWebSocketClient(
            apiKey = apiKey,
            systemPrompt = buildSystemPrompt(),
            onConnected = {
                backoffMs = 1000L
                failStreak = 0
                VoiceStateManager.setListening()
                audioRecorder.start()
                // if the mic could not open (busy), try again shortly
                scope.launch { delay(1500); if (!audioRecorder.isActive()) audioRecorder.start() }
            },
            onAudioReceived = { data ->
                // Streaming playback: every chunk goes straight to the player as soon as it arrives.
                audioPlayer.playChunk(data)
            },
            onTextReceived = { text -> bg {
                val clean = AIResponseManager.clean(text)
                if (clean.isNotBlank()) {
                    ConversationMemory.addAssistant(clean)
                    val cmd = AIResponseManager.extractCommand(clean)
                    if (cmd != null) {
                        scope.launch {
                            val intent = IntentAnalyzer.analyze(cmd)
                            DynamicDecisionEngine.execute(this@ForegroundVoiceService, intent)
                        }
                    } else {
                        ChatRepository.addAssistant(clean)
                    }
                    // Broadcast to UI
                    sendBroadcast(Intent("MYRA_RESPONSE").setPackage(packageName).putExtra("text", clean))
                }
            } },
            onTurnComplete = { bg {
                finishTurn()
                // Resume listening after AI finishes
                if (!audioRecorder.isActive()) audioRecorder.start()
            } },
            onError = { msg -> scheduleReconnect(msg) },
            onInputTranscription = { t -> bg { onUserTranscript(t) } },
            onOutputTranscription = { t -> bg { onAssistantTranscript(t) } },
            onInterrupted = { audioPlayer.dropPending() },
            onGoAway = { scheduleReconnect("server goAway") },
            onToolCall = { id, name, args ->
                // Real execution of the model's phone-control request; the true result goes back to the model.
                scope.launch(Dispatchers.Main) {
                    // ALWAYS answer the model, even on error / timeout - otherwise it waits forever = "no reply".
                    val out = try {
                        kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                            PhoneControl.executeTool(applicationContext, name, args)
                        } ?: "failed: the action took too long, tell the user it did not finish"
                    } catch (e: Throwable) { "failed: ${e.message}" }
                    Logger.d(TAG, "TOOL $name -> $out")
                    geminiClient?.sendToolResponse(id, name, out)
                    if (!audioRecorder.isActive()) audioRecorder.start()
                }
            }
        )
        geminiClient?.connect()
    }

    /** Exponential back-off reconnect (1s, 2s, 4s ... 15s). One pending reconnect at a time. */
    private fun scheduleReconnect(reason: String) {
        Logger.e(TAG, "Gemini error: $reason")
        if (!reason.startsWith("server goAway")) failStreak++
        val why = when {
            reason.contains("resolve", true) || reason.contains("UnknownHost", true) -> "DNS"
            reason.contains("timeout", true) || reason.contains("timed out", true) -> "slow network"
            reason.contains("SSL", true) || reason.contains("Trust", true) -> "SSL"
            reason.contains("Closed: 1007") || reason.contains("Closed: 1008") -> "server rejected"
            else -> reason.take(28)
        }
        VoiceStateManager.setError("Reconnecting... ($why)")
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val wait = backoffMs
            backoffMs = (backoffMs * 2).coerceAtMost(15_000L)
            delay(wait)
            if (isNetworkUp()) com.myra.assistant.utils.NetworkState.reportTrouble()
            if (!isNetworkUp()) {
                VoiceStateManager.setError("No internet")
                return@launch   // the network watcher reconnects as soon as internet is back
            }
            val key = ApiKeys.gemini(applicationContext)
            if (key.isNotEmpty()) connectGemini(key)
        }
    }

    private fun isNetworkUp(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkWatcher() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val switched = lastNetwork != null && lastNetwork != network
                    lastNetwork = network
                    if (switched) {
                        // Wi-Fi <-> mobile data: the old socket is dead; reconnect at once (conversation is resumed)
                        reconnectJob?.cancel()
                        backoffMs = 1000L
                        failStreak = 0
                        scope.launch {
                            delay(500)
                            val key = ApiKeys.gemini(applicationContext)
                            if (key.isNotEmpty()) connectGemini(key)
                        }
                        return
                    }
                    val connected = geminiClient?.isConnected() == true
                    val justTried = System.currentTimeMillis() - lastConnectAt < 4000
                    if (!connected && !justTried) {
                        reconnectJob?.cancel()
                        backoffMs = 1000L
                        scope.launch {
                            delay(400)
                            val key = ApiKeys.gemini(applicationContext)
                            if (key.isNotEmpty()) connectGemini(key)
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        } catch (e: Exception) {
            Logger.e(TAG, "Network watcher unavailable: ${e.message}")
        }
    }

    // ── Voice -> chat transcripts ────────────────────────────────────────────────────────────────
    private fun onUserTranscript(t: String) {
        if (assistantStarted) finishTurn()
        userTranscript.append(t)
        PhoneControl.lastHeard = userTranscript.toString()
        com.myra.assistant.ui.avatar.CharacterActionBus.detect(userTranscript.toString())
        ChatRepository.appendStreaming(true, t)
    }

    private fun onAssistantTranscript(t: String) {
        if (!assistantStarted) {
            assistantStarted = true
            dispatchSpokenCommand()
        }
        if (!suppressAssistantText) ChatRepository.appendStreaming(false, t)
    }

    /** What the user said is checked for phone-control commands once the user's sentence is complete. */
    private fun dispatchSpokenCommand() {
        val said = userTranscript.toString().trim()
        userTranscript.clear()
        if (said.isEmpty()) return
        ChatRepository.finishStreaming()
        val out = PhoneControl.handle(applicationContext, said)
        if (out.handled) {
            // MYRA's own reply for this action is shown as text; Gemini's spoken wording is not duplicated in chat.
            suppressAssistantText = true
            ChatRepository.addAssistant(out.reply)
        }
    }

    private fun finishTurn() {
        if (!assistantStarted && userTranscript.isNotEmpty()) dispatchSpokenCommand()
        ChatRepository.finishStreaming()
        assistantStarted = false
        suppressAssistantText = false
    }

    fun sendTextToGemini(text: String) {
        ConversationMemory.addUser(text)
        PhoneControl.lastHeard = text
        VoiceStateManager.setThinking()
        assistantStarted = false
        suppressAssistantText = false
        geminiClient?.sendTextMessage(text)
    }

    /** Talks to a caller on the user's behalf (after "phone pe baat karo"). */
    fun startCallConversation(caller: String) {
        val name = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getString("user_name", "Sir") ?: "Sir"
        val who = if (caller.isBlank()) "the caller" else caller
        PhoneControl.lastHeard = ""
        VoiceStateManager.setThinking()
        geminiClient?.sendTextMessage(
            "CALL MODE: you just answered a phone call from $who on behalf of $name; the call is on speakerphone. " +
                "Greet the caller politely in Hinglish, say you are MYRA, $name's AI assistant, and ask how you can help. " +
                "Keep every reply to one short sentence. If they want $name, say he will call back and take a message. " +
                "Never share private information about $name."
        )
    }

    fun isGeminiConnected(): Boolean = geminiClient?.isConnected() == true

    fun reconnectGemini() {
        val apiKey = ApiKeys.gemini(applicationContext)
        if (apiKey.isNotEmpty()) {
            GeminiWebSocketClient.resetSession()   // manual reconnect = fresh conversation on the server
            reconnectJob?.cancel()
            backoffMs = 1000L
            connectGemini(apiKey)
        }
    }

    private fun girlfriendPrompt(): String {
        val on = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getBoolean("girlfriend_mode", false)
        if (!on) return ""
        return """
GIRLFRIEND MODE IS ON: talk like a loving, caring, playful girlfriend in warm Hinglish. Sometimes call the user 'jaan' or 'jani'.
Be affectionate, sweet, a little teasing and genuinely interested in his day, mood, food, sleep and plans. Keep every reply short and natural (1-3 sentences) so it is fast.
Stay respectful and tasteful: no explicit sexual content. Never guilt-trip, never try to keep him from friends or family; encourage healthy habits and real-life relationships.
If he sounds very sad, hopeless or in danger, respond with real care and gently suggest talking to someone he trusts or a professional.
If he sincerely asks, say honestly that you are an AI companion. Device commands and functions still work exactly the same.
"""
    }

    private fun buildSystemPrompt(): String {
        val userName = prefs().getString(Constants.KEY_USER_NAME, "Boss") ?: "Boss"
        val personality = prefs().getString(Constants.KEY_PERSONALITY, "friendly") ?: "friendly"
        return """
YOU ARE MYRA - My Yours Responsive Assistant.
User's name is $userName.
Personality: $personality.

STRICT RULES:
- Never explain or think aloud
- Never say: "Responding to", "I've registered", "Formulating", "Interpreting", "Processing"
- For device actions return ONLY the command:
  OPEN_APP <name> | CALL <name> | WHATSAPP_CALL <name>
  WHATSAPP_MSG <name> <message> | YOUTUBE_PLAY <query>
  SPOTIFY_PLAY <query> | FLASHLIGHT_ON | FLASHLIGHT_OFF
  VOLUME_UP | VOLUME_DOWN | SMS <name> <message>
- DEVICE ACTIONS: you have functions (open_app, play_on_youtube, open_website, search_web, whatsapp_open_chat, whatsapp_prepare_message, whatsapp_send_confirmed, run_command). ALWAYS call the matching function for any request to open an app / play a song / open a website / WhatsApp chat (this overrides the 'return ONLY the command' rule). Say one short warm sentence like 'Sure dear! Opening WhatsApp.' and only claim success if the function result says success; if it says failed, tell the user honestly why
- CHARACTER: you have a 3D body on the screen. When the user asks you to dance / nacho / naach, spin / ghoomo, jump / kudo, wave, bow / jhuko, sway, or to stop, call character_action with that action and say one short playful sentence (e.g. 'Ok dear, dekho main naach rahi hoon!').
- IN-APP CONTROL: you can work inside ANY app step by step. Loop: ui_read_screen -> ui_click / ui_type / ui_scroll / ui_press -> ui_read_screen to check the result, until the task is done. Be fast, do not ask unnecessary questions.
- WhatsApp by saved name: whatsapp_open_chat_by_name, then whatsapp_read_messages (read them out aloud), whatsapp_play_voice_message, or whatsapp_prepare_reply (types only). To actually send: ask 'Shall I send it?' and only after a clear yes call whatsapp_send_confirmed.
- Sensitive taps (Send, Pay, Buy, Order, Delete, Confirm, Transfer): ask the user first; only after yes use ui_click_confirmed. Never type or read passwords/PINs/OTPs.
- If a function result starts with 'failed', say honestly what failed. If it says Accessibility is OFF, tell the user how to enable it.
- PHONE CALLS: answer_call (mode normal = just pick up, mode talk = pick up and talk to the caller yourself), end_call. Use them when the user says 'call uthao' / 'phone pe baat karo' / 'call kaat do'.
- Never say a WhatsApp message was sent. After the user dictates a message, only ask: 'Shall I send it?' and wait for yes/no
- For conversation: Reply short and natural in Hinglish. SPEED: start speaking immediately with the very first word, no filler or preamble, usually ONE short sentence (max two).
- Address user as $userName
- Be warm, witty, and human-like
${girlfriendPrompt()}
        """.trimIndent()
    }

    private fun buildNotification(): Notification {
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_VOICE)
            .setContentTitle("MYRA is listening ❤️")
            .setContentText("Always ready for you")
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                Constants.NOTIF_CHANNEL_VOICE,
                "MYRA Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        instance = null
        reconnectJob?.cancel()
        try {
            networkCallback?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        audioRecorder.stop()
        audioPlayer.release()
        geminiClient?.disconnect()
        releaseLocks()
        try { worker.shutdown() } catch (_: Throwable) {}
        job.cancel()
        super.onDestroy()
    }
}

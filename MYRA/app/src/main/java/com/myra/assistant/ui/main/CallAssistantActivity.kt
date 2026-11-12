package com.myra.assistant.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myra.assistant.R
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.utils.LiveAudioManager
import java.util.Locale



class CallAssistantActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var callerNameText: TextView
    private lateinit var statusText: TextView
    private var waveformView: WaveformView? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var callerName = "Unknown Caller"
    private var phoneNumber = ""
    private var userName = "Sir"
    private var personality = "gf"
    private var isWhatsAppCall = false

    // State management — FIXED: Proper flags
    private var isDecisionMade = false
    private var isListening = false
    private var announcementPlayed = false
    private var isSpeaking = false
    private var isCallAnswered = false

    private lateinit var liveClient: GeminiLiveClient
    private lateinit var liveAudioManager: LiveAudioManager
    private var isLiveConnected = false

    private val TAG = "MYRA_CALL_UI"

    // Broadcast receiver: call cut hone par finish
    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                CallMonitorService.ACTION_CALL_ENDED -> {
                    Log.d(TAG, "Call ended → closing assistant UI")
                    if (!isDecisionMade) {
                        // Call ended before user decision — just finish
                        safeFinish()
                    }
                }
                CallMonitorService.ACTION_CALL_ACTIVE -> {
                    Log.d(TAG, "Call active → closing assistant UI")
                    isCallAnswered = true
                    safeFinish()
                }
                CallMonitorService.ACTION_CALL_RINGING -> {
                    Log.d(TAG, "Call ringing received")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen pe bhi dikhao
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_call_assistant)

        // Intent se data lo — FIXED: WhatsApp call detection
        callerName  = intent.getStringExtra("CALLER_NAME") ?: "Unknown Caller"
        phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""
        userName    = intent.getStringExtra("USER_NAME") ?: getPrefsValue("user_name", "Sir")
        personality = intent.getStringExtra("PERSONALITY") ?: getPrefsValue("personality_mode", "gf")
        isWhatsAppCall = intent.getBooleanExtra("IS_WHATSAPP_CALL", false)

        Log.d(TAG, "CallAssistant started: caller=$callerName, whatsapp=$isWhatsAppCall, personality=$personality")

        initViews()

        // Initialize Live Audio Manager FIRST (before Gemini)
        liveAudioManager = LiveAudioManager(this)

        // Setup Gemini Live FIRST for natural voice
        setupGeminiLive()

        // TTS as fallback only
        tts = TextToSpeech(this, this)

        // Broadcast register karo
        val filter = IntentFilter().apply {
            addAction(CallMonitorService.ACTION_CALL_ENDED)
            addAction(CallMonitorService.ACTION_CALL_ACTIVE)
            addAction(CallMonitorService.ACTION_CALL_RINGING)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(callStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(callStateReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Receiver register failed: ${e.message}")
        }

        // Phone state monitor
        startCallStateMonitor()
    }

    private fun initViews() {
        callerNameText = findViewById(R.id.callerNameText)
        statusText     = findViewById(R.id.callStatusText)
        waveformView   = findViewById<WaveformView?>(R.id.callWaveform)

        callerNameText.text = callerName
        statusText.text = "MYRA preparing..."
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.ENGLISH
            }
            isTtsReady = true
        } else {
            Log.e(TAG, "TTS init failed")
        }
    }

    /**
     * ✅ FIXED: WebSocket natural voice for announcement
     * No more robotic TTS — uses Gemini Live audio streaming
     */
    private fun startAnnouncement() {
        if (isDecisionMade || announcementPlayed) {
            if (!isDecisionMade) startListening()
            return
        }

        announcementPlayed = true
        val msg = buildAnnouncementText()

        statusText.text = "Announcing..."
        Log.d(TAG, "Announcing via WebSocket: $msg")

        // ✅ Use WebSocket natural voice instead of TTS
        speakViaWebSocket(msg)

        // Announcement khatam hone ke baad listen karo
        // WebSocket audio duration estimate
        val estimatedDuration = (msg.length * 80L).coerceIn(3000L, 8000L)
        handler.postDelayed({
            if (!isDecisionMade && !isSpeaking) startListening()
        }, estimatedDuration)
    }

    private fun buildAnnouncementText(): String {
        val isKnownContact = !isNumberLike(callerName)

        return if (isWhatsAppCall) {
            // WhatsApp call announcement
            when (personality) {
                "gf" -> when {
                    isKnownContact ->
                        "$userName, $callerName ka WhatsApp call aa raha hai. Uthana hai ya nahi?"
                    else ->
                        "$userName, ek anjaan number se WhatsApp call aa rahi hai. Uthana hai ya nahi?"
                }
                "professional" -> when {
                    isKnownContact ->
                        "$userName, incoming WhatsApp call from $callerName. Should I answer or decline?"
                    else ->
                        "$userName, unknown WhatsApp caller. Should I answer or decline?"
                }
                else ->
                    "$userName, $callerName ka WhatsApp call aa raha hai. Kya karna hai?"
            }
        } else {
            // Normal call announcement
            when (personality) {
                "gf" -> when {
                    isKnownContact ->
                        "$userName, $callerName ka call aa raha hai. Uthana hai ya nahi?"
                    else ->
                        "$userName, ek anjaan number se call aa rahi hai. Uthana hai ya nahi?"
                }
                "professional" -> when {
                    isKnownContact ->
                        "$userName, incoming call from $callerName. Should I answer or decline?"
                    else ->
                        "$userName, unknown caller. Should I answer or decline?"
                }
                else ->
                    "$userName, $callerName ka call aa raha hai. Kya karna hai?"
            }
        }
    }

    /**
     * ✅ FIXED: Setup Gemini Live with proper prompt for call scenario
     */
    private fun setupGeminiLive() {
        val apiKey = com.myra.assistant.utils.ApiKeys.gemini(this)
        if (apiKey.isEmpty()) {
            Log.e(TAG, "No API key found")
            return
        }

        val callType = if (isWhatsAppCall) "WhatsApp call" else "phone call"

        val prompt = """
            You are MYRA, a caring AI assistant for $userName. Personality: $personality.

            SITUATION: Incoming $callType from $callerName.
            
            TASKS:
            1. Announce the incoming call naturally in Hinglish.
            2. If the user asks questions (e.g., "Who is calling?", "What should I do?"), answer them naturally and helpfully.
            3. Guide the user to say "Answer" or "Reject" to handle the call.

            STYLE: Natural, warm, human-like tone. Respond in Hinglish. Keep it concise.
        """.trimIndent()

        liveClient = GeminiLiveClient(apiKey, prompt, object : GeminiLiveClient.LiveListener {
            override fun onAudioReceived(data: ByteArray) {
                isSpeaking = true
                liveAudioManager.playChunk(data)
                runOnUiThread {
                    statusText.text = "Speaking... 💬"
                    waveformView?.startAnimation()
                }
            }

            override fun onTextReceived(text: String) {
                Log.d(TAG, "Gemini Text: $text")
            }

            override fun onConnected() {
                isLiveConnected = true
                Log.d(TAG, "Gemini Live Connected ✅")
                // Start announcement once connected
                handler.postDelayed({ startAnnouncement() }, 500)
            }

            override fun onTurnComplete() {
                isSpeaking = false
                runOnUiThread {
                    waveformView?.stopAnimation()
                    if (!isDecisionMade) startListening()
                }
            }

            override fun onError(msg: String) {
                isLiveConnected = false
                Log.e(TAG, "Gemini Error: $msg")
                // Fallback to TTS
                handler.postDelayed({
                    if (!announcementPlayed) startAnnouncement()
                }, 500)
            }
        })
        liveClient.start()
    }

    /**
     * ✅ NEW: Speak via WebSocket (natural voice)
     */
    private fun speakViaWebSocket(text: String) {
        if (isLiveConnected) {
            isSpeaking = true
            liveClient.sendTextMessage(text)
            Log.d(TAG, "Speaking via WebSocket: $text")
            return
        }
        // Fallback to TTS
        speakTTS(text)
    }

    /**
     * Fallback TTS (Disabled - only WebSocket natural voice allowed)
     */
    private fun speakTTS(text: String) {
        // Robotic TTS removed as per user request
        Log.d(TAG, "Robotic TTS skipped for: $text")
    }

    private fun startListening() {
        if (isDecisionMade || isListening || isSpeaking) return
        isListening = true

        statusText.text = "Sun rahi hoon... (bolo: Uthao / Reject)"
        Log.d(TAG, "Starting voice recognition")

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            handler.postDelayed({ repeatAnnouncement() }, 1000)
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle?) {
                isListening = false
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = texts?.firstOrNull()?.lowercase()?.trim() ?: ""
                Log.d(TAG, "User said: '$spoken'")

                if (spoken.isEmpty()) {
                    if (!isDecisionMade) repeatAnnouncement()
                    return
                }
                processCommand(spoken)
            }

            override fun onError(errorCode: Int) {
                isListening = false
                val errorMsg = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "TIMEOUT"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
                    else -> "ERROR_$errorCode"
                }
                Log.w(TAG, "Speech error: $errorMsg")

                if (!isDecisionMade) {
                    handler.postDelayed({ if (!isDecisionMade) startListening() }, 1500)
                }
            }

            override fun onReadyForSpeech(p0: Bundle?) {
                statusText.text = "Bol do... 🎙️"
            }
            override fun onBeginningOfSpeech() {
                statusText.text = "Sun rahi hoon... 👂"
                waveformView?.startAnimation()
            }
            override fun onRmsChanged(rmsdB: Float) {
                waveformView?.setAmplitude(rmsdB)
            }
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {
                statusText.text = "Processing..."
                waveformView?.stopAnimation()
            }
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // ✅ Multi-language support
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-US", "en-IN", "bn-IN", "ta-IN", "te-IN", "mr-IN", "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN"))
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
        }
        speechRecognizer?.startListening(intent)
    }

    /**
     * ✅ FIXED: Proper answer/reject detection with multi-language support
     */
    private fun processCommand(spoken: String) {
        val lower = spoken.lowercase().trim()
        Log.d(TAG, "Processing command: '$lower'")

        // ANSWER keywords — MULTI-LANGUAGE SUPPORT
        val answerWords = listOf(
            // Hindi/Hinglish
            "utha", "uthao", "uthau", "answer", "pick", "receive", "accept",
            "haan", "han", "yes", "theek", "theek hai", "okay", "ok", "lo",
            "phone uthao", "uthao phone", "receive karo", "le lo", "uthane",
            "baat karo", "hello", "halo", "bolo", "sunao",
            // English
            "answer", "pick up", "pick", "receive", "accept", "yes", "yeah", "yep",
            "sure", "okay", "ok", "go ahead", "talk", "speak",
            // Tamil
            "eduthukko", "pesu", "sari",
            // Telugu
            "attuko", "matladu", "sare",
            // Bengali
            "tulun", "kotha bolo", "thik",
            // Marathi
            "uthav", "bol", "thik",
            // Gujarati
            "uthavo", "bolo", "thik",
            // Kannada
            "ettuko", "matadu", "sari",
            // Malayalam
            "eduthu", "samsarik", "sheri",
            // Punjabi
            "chukko", "gall karo", "thik",
            // Urdu
            "uthavo", "baat karo", "thik"
        )

        // REJECT keywords — MULTI-LANGUAGE SUPPORT
        val rejectWords = listOf(
            // Hindi/Hinglish
            "reject", "decline", "cut", "kat", "kaat", "nahi", "na", "no",
            "mat", "band karo", "drop", "dismiss", "nahi uthana",
            "nahi uthao", "chhod do", "ignore", "kat do", "kaat do",
            "busy hoon", "baad mein", "later", "ruk", "rukna",
            // English
            "reject", "decline", "cut", "drop", "dismiss", "no", "nope", "nah",
            "don't", "dont", "stop", "end", "busy", "later", "not now",
            // Tamil
            "venam", "cut pannu", "apram",
            // Telugu
            "vaddu", "cut chey", "tarvata",
            // Bengali
            "na", "kat", " pore",
            // Marathi
            "nako", "kat", "nanter",
            // Gujarati
            "nahi", "kat", "pachi",
            // Kannada
            "beda", "kat", "melake",
            // Malayalam
            "venda", "kat", "pinnne",
            // Punjabi
            "nahi", "kat", "baad",
            // Urdu
            "nahi", "kat", "baad mein"
        )

        // "phone pe baat karo" = pick up AND let MYRA talk to the caller
        val talkWords = listOf(
            "baat karo", "bat karo", "baat kar", "phone pe baat", "phone par baat", "call pe baat", "tum baat", "aap baat",
            "talk to", "speak to", "you talk", "talk to them", "baat karna"
        )
        val isTalk = talkWords.any { lower.contains(it) }

        // Decision logic — FIXED: Better detection
        val hasAnswer = answerWords.any { lower.contains(it) }
        val hasReject = rejectWords.any { lower.contains(it) }

        // Priority: If both detected, check which appears first
        val answerIndex = answerWords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
        val rejectIndex = rejectWords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE

        val isAnswer = hasAnswer && (!hasReject || answerIndex < rejectIndex)
        val isReject = hasReject && (!hasAnswer || rejectIndex < answerIndex)

        Log.d(TAG, "Decision: isAnswer=$isAnswer, isReject=$isReject, answerPos=$answerIndex, rejectPos=$rejectIndex")

        when {
            isAnswer && !isReject -> performAnswer(isTalk)
            isReject -> performReject()
            else -> {
                // ✅ ENHANCED: If not answer/reject, let Gemini handle the query naturally
                if (isLiveConnected) {
                    Log.d(TAG, "Passing unrecognized command to Gemini: $spoken")
                    isSpeaking = true
                    runOnUiThread { statusText.text = "Thinking... 🤔" }
                    liveClient.sendTextMessage(spoken)
                } else {
                    val confusion = when (personality) {
                        "gf"  -> "Jaan, samajh nahi aaya. 'Uthao' ya 'Nahi' bolo."
                        else  -> "Please say 'Answer' or 'Reject'."
                    }
                    speakViaWebSocket(confusion)
                    handler.postDelayed({ if (!isDecisionMade) repeatAnnouncement() }, 4000)
                }
            }
        }
    }

    /**
     * ✅ FIXED: Call ANSWER — proper logic, no confusion with reject
     */
    private fun performAnswer(talkMode: Boolean = false) {
        if (isDecisionMade) return
        isDecisionMade = true
        isCallAnswered = true
        stopListening()

        val confirmMsg = when (personality) {
            "gf"  -> "Ji $userName, call utha rahi hoon! 📞"
            else  -> "Answering the call, $userName."
        }
        speakViaWebSocket(confirmMsg)
        statusText.text = "Answering... 📞"
        Log.d(TAG, "=== ANSWERING CALL ===")

        handler.postDelayed({
            var success = false

            if (isWhatsAppCall) {
                // WhatsApp call answer
                success = answerWhatsAppCall()
            } else {
                // Normal call answer
                success = answerNormalCall()
            }

            if (!success) {
                speakViaWebSocket("Sorry $userName, call nahi uth payi")
            } else if (talkMode) {
                com.myra.assistant.control.CallControl.beginCallConversation(applicationContext, callerName)
            }

            handler.postDelayed({ safeFinish() }, 2000)
        }, 1500)
    }

    /**
     * ✅ NEW: Separate method for normal call answer
     */
    private fun answerNormalCall(): Boolean {
        var success = false

        // Method 1: TelecomManager acceptRingingCall
        try {
            if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    telecom.acceptRingingCall()
                    Log.d(TAG, "✅ Call accepted via TelecomManager.acceptRingingCall()")
                    success = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TelecomManager answer failed: ${e.message}")
        }

        // Method 2: Accessibility fallback
        if (!success) {
            try {
                val answered = com.myra.assistant.service.SmartAccessibilityEngine.click(
                    text = "Answer"
                ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                    contentDesc = "Answer"
                ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                    text = "Accept"
                ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                    contentDesc = "Accept"
                )
                Log.d(TAG, "Accessibility answer: $answered")
                success = answered
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility answer failed: ${e.message}")
            }
        }

        return success
    }

    /**
     * ✅ NEW: Answer WhatsApp call via accessibility
     */
    private fun answerWhatsAppCall(): Boolean {
        var success = false

        try {
            // Try to click WhatsApp answer button
            success = com.myra.assistant.service.SmartAccessibilityEngine.click(
                text = "Answer"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                contentDesc = "Answer"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                text = "Accept"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                contentDesc = "Accept call"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                id = "com.whatsapp:id/incoming_call_answer"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                id = "com.whatsapp.w4b:id/incoming_call_answer"
            )

            Log.d(TAG, "WhatsApp call answer: $success")
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp answer failed: ${e.message}")
        }

        return success
    }

    /**
     * ✅ FIXED: Call REJECT — proper endCall(), no confusion
     */
    private fun performReject() {
        if (isDecisionMade) return
        isDecisionMade = true
        stopListening()

        val confirmMsg = when (personality) {
            "gf"  -> "Theek hai $userName, call reject kar diya. ❌"
            else  -> "Call declined, $userName."
        }
        speakViaWebSocket(confirmMsg)
        statusText.text = "Rejecting... ❌"
        Log.d(TAG, "=== REJECTING CALL ===")

        handler.postDelayed({
            var success = false

            if (isWhatsAppCall) {
                // WhatsApp call reject
                success = rejectWhatsAppCall()
            } else {
                // Normal call reject
                success = rejectNormalCall()
            }

            if (!success) {
                speakViaWebSocket("Sorry $userName, call reject nahi ho paya")
            }

            handler.postDelayed({ safeFinish() }, 2000)
        }, 1500)
    }

    /**
     * ✅ NEW: Separate method for normal call reject
     */
    private fun rejectNormalCall(): Boolean {
        var success = false

        // Method 1: TelecomManager endCall
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    @Suppress("DEPRECATION")
                    telecom.endCall()
                    Log.d(TAG, "✅ Call rejected via TelecomManager.endCall()")
                    success = true
                } else {
                    Log.w(TAG, "Permission ANSWER_PHONE_CALLS missing")
                }
            } else {
                Log.w(TAG, "TelecomManager.endCall() requires API 28")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TelecomManager reject failed: ${e.message}")
        }

        // Method 2: Accessibility fallback
        if (!success) {
            try {
                val rejected = com.myra.assistant.service.SmartAccessibilityEngine.click(
                    text = "Decline"
                ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                    text = "Reject"
                ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                    contentDesc = "Decline"
                ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                    contentDesc = "Reject call"
                )
                Log.d(TAG, "Accessibility reject: $rejected")
                success = rejected
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility reject failed: ${e.message}")
            }
        }

        return success
    }

    /**
     * ✅ NEW: Reject WhatsApp call via accessibility
     */
    private fun rejectWhatsAppCall(): Boolean {
        var success = false

        try {
            success = com.myra.assistant.service.SmartAccessibilityEngine.click(
                text = "Decline"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                contentDesc = "Decline"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                text = "Reject"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                id = "com.whatsapp:id/incoming_call_decline"
            ) || com.myra.assistant.service.SmartAccessibilityEngine.click(
                id = "com.whatsapp.w4b:id/incoming_call_decline"
            )

            Log.d(TAG, "WhatsApp call reject: $success")
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp reject failed: ${e.message}")
        }

        return success
    }

    private fun repeatAnnouncement() {
        announcementPlayed = false
        startAnnouncement()
    }

    private fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        waveformView?.stopAnimation()
    }

    private fun startCallStateMonitor() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val monitor = object : Runnable {
            override fun run() {
                if (isDecisionMade || isCallAnswered) return
                if (tm.callState == TelephonyManager.CALL_STATE_IDLE && !isWhatsAppCall) {
                    Log.d(TAG, "Phone idle — closing call assistant")
                    safeFinish()
                    return
                }
                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(monitor, 3000)
    }

    private fun safeFinish() {
        if (!isFinishing && !isDestroyed) {
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
            try { speechRecognizer?.destroy() } catch (_: Exception) {}

            // ✅ FIXED: Reset state properly
            isDecisionMade = false
            isListening = false
            announcementPlayed = false
            isSpeaking = false

            finish()
        }
    }

    private fun isNumberLike(str: String) =
        str.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }

    private fun getPrefsValue(key: String, default: String): String {
        return getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
            .getString(key, default) ?: default
    }

    override fun onBackPressed() {
        speakViaWebSocket("Pehle decision lo!")
        // super.onBackPressed() // Removed to block back button during call decision
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(callStateReceiver) } catch (_: Exception) {}
        tts?.shutdown()
        if (::liveClient.isInitialized) liveClient.disconnect()
        liveAudioManager.stop()
        speechRecognizer?.destroy()

        // ✅ FIXED: Reset all states on destroy
        isDecisionMade = false
        isListening = false
        announcementPlayed = false
        isSpeaking = false
        isCallAnswered = false

        Log.d(TAG, "CallAssistantActivity destroyed")
    }
}
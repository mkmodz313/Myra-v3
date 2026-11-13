package com.myra.assistant.websocket

import android.util.Base64
import android.util.Log
import com.myra.assistant.utils.Constants
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Live WebSocket client.
 *
 * Same model / voice / API key / audio format as before. Added (all optional, default off for old callers):
 *  - input/output transcription callbacks (voice -> chat text)
 *  - session resumption (conversation continuity across reconnects)
 *  - stale-socket protection (a replaced socket can never deliver duplicate audio)
 *  - goAway / interrupted / close handling
 */
class GeminiWebSocketClient(
    private val apiKey: String,
    private val systemPrompt: String,
    private val onConnected: () -> Unit,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onTextReceived: (String) -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onError: (String) -> Unit,
    private val onInputTranscription: ((String) -> Unit)? = null,
    private val onOutputTranscription: ((String) -> Unit)? = null,
    private val onInterrupted: (() -> Unit)? = null,
    private val onGoAway: (() -> Unit)? = null,
    private val onToolCall: ((id: String, name: String, args: JSONObject) -> Unit)? = null
) {
    companion object {
        private const val TAG = "GEMINI_WS"

        /** Shared client: TLS sessions / connection pool are reused, so reconnects are faster. */
        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .dns(object : Dns {   // IPv4 first: some mobile networks have a broken IPv6 route (slow / failed connects)
                override fun lookup(hostname: String): List<java.net.InetAddress> =
                    Dns.SYSTEM.lookup(hostname).sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
            })
            .pingInterval(10, TimeUnit.SECONDS)   // a dead connection is noticed in ~10 s
            .retryOnConnectionFailure(true)
            .build()

        /** Latest resumption handle issued by the server (lets a reconnect continue the same conversation). */
        @Volatile var resumptionHandle: String? = null

        /**
         * Transcription + session resumption are optional setup fields. If the server ever rejects the setup
         * twice in a row, they are switched off automatically so the original voice pipeline keeps working.
         */
        @Volatile var extrasEnabled: Boolean = true

        /** Function calling (open apps / YouTube / WhatsApp by voice). Switched off automatically if the server rejects the setup. */
        @Volatile var toolsEnabled: Boolean = true

        /** Faster answers: no hidden "thinking" delay + quicker end-of-speech detection. Auto-disabled if the server rejects them. */
        @Volatile var thinkingTuning: Boolean = true
        @Volatile var vadTuning: Boolean = true
        @Volatile private var setupRejections = 0

        fun resetSession() { resumptionHandle = null }
    }

    private var webSocket: WebSocket? = null
    @Volatile private var isSetupComplete = false
    @Volatile private var socketOpened = false
    @Volatile private var generation = 0

    private val url = "${Constants.GEMINI_WS_BASE}?key=$apiKey"

    fun connect() {
        val myGen = ++generation
        isSetupComplete = false
        socketOpened = false
        try { webSocket?.cancel() } catch (_: Exception) {}

        val request = Request.Builder()
            .url(url)
            .addHeader("Origin", "https://generativelanguage.googleapis.com")
            .build()

        webSocket = sharedClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (myGen != generation) { ws.cancel(); return }
                Log.d(TAG, "Connected ✅")
                socketOpened = true
                sendSetup(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (myGen != generation) return
                handleResponse(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (myGen != generation) return
                try { handleResponse(bytes.utf8()) } catch (e: Exception) { Log.e(TAG, "Binary parse error") }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (myGen != generation) return
                noteFailureBeforeSetup()
                isSetupComplete = false
                Log.e(TAG, "WS Failure: ${t.message}")
                onError("Connection failed: ${t.message}")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (myGen != generation) return
                noteFailureBeforeSetup()
                isSetupComplete = false
                Log.d(TAG, "WS Closed: $code $reason")
                // A close we did not ask for (server timeout, bad setup...) must trigger a reconnect.
                onError("Closed: $code $reason")
            }
        })
    }

    private fun noteFailureBeforeSetup() {
        // Network drops (socket never opened) must NOT disable features; only a rejected setup counts.
        if (socketOpened && !isSetupComplete) {
            setupRejections++
            val threshold = if (thinkingTuning || vadTuning) 1 else 2
            if (setupRejections >= threshold) {
                setupRejections = 0
                when {
                    thinkingTuning -> { Log.w(TAG, "Setup rejected - disabling thinking tuning"); thinkingTuning = false }
                    vadTuning -> { Log.w(TAG, "Setup rejected - disabling VAD tuning"); vadTuning = false }
                    toolsEnabled -> { Log.w(TAG, "Setup rejected twice - disabling function calling"); toolsEnabled = false }
                    extrasEnabled -> {
                        Log.w(TAG, "Setup rejected twice - disabling transcription/resumption")
                        extrasEnabled = false
                        resumptionHandle = null
                    }
                }
            }
        }
    }

    private fun sendSetup(ws: WebSocket) {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", Constants.GEMINI_MODEL)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    if (thinkingTuning) put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", Constants.GEMINI_VOICE)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                if (vadTuning) put("realtimeInputConfig", JSONObject().put("automaticActivityDetection", JSONObject()
                    .put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH")
                    .put("endOfSpeechSensitivity", "END_SENSITIVITY_HIGH")
                    .put("prefixPaddingMs", 20)
                    .put("silenceDurationMs", 350)))
                if (toolsEnabled && onToolCall != null) put("tools", buildTools())
                if (extrasEnabled) {
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
                    put("sessionResumption", JSONObject().apply {
                        resumptionHandle?.let { put("handle", it) }
                    })
                }
            })
        }
        ws.send(setup.toString())
        Log.d(TAG, "Setup sent ✅ (extras=$extrasEnabled, tools=$toolsEnabled, thinking=$thinkingTuning, vad=$vadTuning, resumed=${resumptionHandle != null})")
    }

    private fun fn(name: String, description: String, vararg params: Pair<String, String>): JSONObject {
        val o = JSONObject().put("name", name).put("description", description)
        if (params.isNotEmpty()) {
            val props = JSONObject()
            val req = JSONArray()
            params.forEach { (k, d) ->
                props.put(k, JSONObject().put("type", "STRING").put("description", d))
                req.put(k)
            }
            o.put("parameters", JSONObject().put("type", "OBJECT").put("properties", props).put("required", req))
        }
        return o
    }

    /** Phone-control functions. The app executes them for real and returns the true result to the model. */
    private fun buildTools(): JSONArray = JSONArray().put(
        JSONObject().put("functionDeclarations", JSONArray()
            .put(fn("open_app", "Open an app on the user's phone (WhatsApp, YouTube, Facebook, Instagram, Chrome, Google, Gallery, Camera, Settings, Calculator or any installed app).",
                "app_name" to "Name of the app, e.g. WhatsApp"))
            .put(fn("play_on_youtube", "Open YouTube and play/search a song or video.",
                "query" to "Song or video to search, e.g. 'Tum Hi Ho'"))
            .put(fn("open_website", "Open a website in the browser.", "url" to "Website, e.g. google.com"))
            .put(fn("search_web", "Search Google on the phone.", "query" to "What to search"))
            .put(fn("whatsapp_open_chat", "Open the WhatsApp chat of a phone number (international format).",
                "phone_number" to "Number with country code, e.g. +923001234567"))
            .put(fn("whatsapp_prepare_message", "Prepare (NOT send) a WhatsApp message for the chat opened last. After this, ask the user to confirm.",
                "message" to "The exact message text"))
            .put(fn("whatsapp_send_confirmed", "Send the prepared WhatsApp message. Call ONLY after the user clearly said yes/confirm."))
            .put(fn("whatsapp_open_chat_by_name", "Open the WhatsApp chat of a saved contact by the name the user saved it under. Takes a few seconds.",
                "contact_name" to "Contact name exactly as the user says it"))
            .put(fn("whatsapp_read_messages", "Read the latest visible messages of the currently open WhatsApp chat so you can read them out loud or answer questions about them.",
                "count" to "How many recent messages, e.g. 5"))
            .put(fn("whatsapp_play_voice_message", "Play the latest visible voice message in the open WhatsApp chat."))
            .put(fn("whatsapp_prepare_reply", "Type a reply into the open WhatsApp chat box WITHOUT sending it. Then ask the user to confirm and call whatsapp_send_confirmed.",
                "message" to "Reply text"))
            .put(fn("ui_read_screen", "Read what is on the screen of the app that is currently open (labels, buttons [tap], inputs [input]). Use this before and after tapping to see what happened."))
            .put(fn("ui_click", "Tap an option/button/tab/item on the current screen by its visible text or description.",
                "text" to "Visible label to tap"))
            .put(fn("ui_click_confirmed", "Tap a sensitive option (Send, Pay, Buy, Delete, Confirm...). ONLY after the user clearly said yes.",
                "text" to "Visible label to tap"))
            .put(fn("ui_type", "Type text into the focused or first text box on screen (search bars, message boxes). Never passwords.",
                "text" to "Text to type"))
            .put(fn("ui_scroll", "Scroll the current screen.", "direction" to "up or down"))
            .put(fn("ui_press", "Press a system key.", "key" to "back, home, recents or notifications"))
            .put(fn("answer_call", "Answer the incoming phone call. mode 'normal' = just pick up (the user talks); mode 'talk' = pick up and talk to the caller yourself on speakerphone.",
                "mode" to "normal or talk"))
            .put(fn("end_call", "End or decline the current phone call."))
            .put(fn("character_action", "Make MYRA's 3D character on the screen perform a body action. Use it whenever the user asks you to dance, spin, jump, wave, bow or sway, or to stop.",
                "action" to "One of: dance, spin, jump, wave, bow, sway, stop"))
            .put(fn("run_command", "Other device actions. Command is one of: VOLUME_UP, VOLUME_DOWN, FLASHLIGHT_ON, FLASHLIGHT_OFF, CALL <name>, WHATSAPP_CALL <name>.",
                "command" to "The command"))
        )
    )

    fun sendToolResponse(id: String, name: String, output: String) {
        try {
            val msg = JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(
                JSONObject().put("id", id).put("name", name).put("response", JSONObject().put("result", output))
            )))
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Tool response error: ${e.message}")
        }
    }

    fun sendAudioChunk(pcm: ByteArray) {
        if (!isSetupComplete) return
        try {
            // Hand-built JSON: avoids allocating JSONObject trees for every ~32 ms mic chunk.
            val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
            webSocket?.send(
                "{\"realtimeInput\":{\"mediaChunks\":[{\"mimeType\":\"audio/pcm;rate=16000\",\"data\":\"$b64\"}]}}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Audio send error: ${e.message}")
        }
    }

    fun sendTextMessage(text: String) {
        if (!isSetupComplete) return
        try {
            val msg = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", text)))
                    }))
                    put("turnComplete", true)
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Text send error: ${e.message}")
        }
    }

    private fun handleResponse(json: String) {
        try {
            val obj = JSONObject(json)

            // Setup complete
            if (obj.has("setupComplete")) {
                isSetupComplete = true
                setupRejections = 0
                Log.d(TAG, "Gemini Ready ✅")
                onConnected()
                return
            }

            obj.optJSONObject("sessionResumptionUpdate")?.let { upd ->
                val handle = upd.optString("newHandle", "")
                if (upd.optBoolean("resumable", true) && handle.isNotEmpty()) resumptionHandle = handle
            }

            obj.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
                for (i in 0 until calls.length()) {
                    val c = calls.getJSONObject(i)
                    onToolCall?.invoke(c.optString("id"), c.optString("name"), c.optJSONObject("args") ?: JSONObject())
                }
                return
            }

            if (obj.has("goAway")) {
                Log.w(TAG, "Server goAway - reconnecting with resumption")
                onGoAway?.invoke()
                return
            }

            val serverContent = obj.optJSONObject("serverContent") ?: return

            if (serverContent.optBoolean("interrupted", false)) {
                onInterrupted?.invoke()
            }

            serverContent.optJSONObject("inputTranscription")?.optString("text", "")?.let {
                if (it.isNotEmpty()) onInputTranscription?.invoke(it)
            }

            val modelTurn = serverContent.optJSONObject("modelTurn")
            val parts = modelTurn?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    // Audio: handed to the player immediately, chunk by chunk (streaming playback)
                    if (part.has("inlineData")) {
                        val data = part.getJSONObject("inlineData").getString("data")
                        onAudioReceived(Base64.decode(data, Base64.DEFAULT))
                    }
                    // Text (internal "thought" parts are never shown or spoken)
                    if (part.has("text") && !part.optBoolean("thought", false)) {
                        onTextReceived(part.getString("text"))
                    }
                }
            }

            serverContent.optJSONObject("outputTranscription")?.optString("text", "")?.let {
                if (it.isNotEmpty()) onOutputTranscription?.invoke(it)
            }

            if (serverContent.optBoolean("turnComplete", false)) {
                onTurnComplete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message} | ${json.take(200)}")
        }
    }

    fun disconnect() {
        generation++   // invalidates every callback of the socket being closed
        try { webSocket?.close(1000, "Bye") } catch (_: Exception) {}
        webSocket = null
        isSetupComplete = false
    }

    fun isConnected() = isSetupComplete
}

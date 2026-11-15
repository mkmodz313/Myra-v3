package com.myra.assistant.ai

import com.myra.assistant.models.CommandType
import com.myra.assistant.models.VoiceCommand

object IntentAnalyzer {

    private val OPEN_PATTERNS = listOf("open", "kholo", "khol", "launch", "start", "chalo", "chala")
    private val CALL_PATTERNS = listOf("call", "phone", "ring", "dial")
    private val WHATSAPP_CALL = listOf("whatsapp call", "video call")
    private val MSG_PATTERNS = listOf("message", "msg", "send", "bhejo", "likho")
    private val YOUTUBE_PATTERNS = listOf("youtube", "video play", "play on youtube")
    private val SPOTIFY_PATTERNS = listOf("spotify", "play music", "gaana", "song")
    private val VOLUME_UP = listOf("volume up", "louder", "badhao", "tez karo")
    private val VOLUME_DOWN = listOf("volume down", "lower", "kam karo", "dhima karo")
    private val FLASHLIGHT_ON = listOf("flashlight on", "torch on", "light on")
    private val FLASHLIGHT_OFF = listOf("flashlight off", "torch off", "light off")

    fun analyze(text: String): VoiceCommand {
        val lower = text.lowercase().trim()

        // Structured command passthrough
        val structured = parseStructured(text)
        if (structured != null) return structured

        // Natural language analysis
        return when {
            FLASHLIGHT_ON.any { lower.contains(it) } ->
                VoiceCommand(text, CommandType.FLASHLIGHT_ON)

            FLASHLIGHT_OFF.any { lower.contains(it) } ->
                VoiceCommand(text, CommandType.FLASHLIGHT_OFF)

            VOLUME_UP.any { lower.contains(it) } ->
                VoiceCommand(text, CommandType.VOLUME_UP)

            VOLUME_DOWN.any { lower.contains(it) } ->
                VoiceCommand(text, CommandType.VOLUME_DOWN)

            WHATSAPP_CALL.any { lower.contains(it) } -> {
                val name = extractAfter(lower, WHATSAPP_CALL)
                VoiceCommand(text, CommandType.WHATSAPP_CALL, mapOf("name" to name))
            }

            CALL_PATTERNS.any { lower.startsWith(it) || lower.contains(" $it ") } -> {
                val name = extractAfter(lower, CALL_PATTERNS)
                VoiceCommand(text, CommandType.CALL, mapOf("name" to name))
            }

            MSG_PATTERNS.any { lower.contains(it) } -> {
                val parts = lower.split(" to ", " ko ")
                val name = if (parts.size > 1) parts[1].split(" ")[0] else ""
                VoiceCommand(text, CommandType.WHATSAPP_MSG, mapOf("name" to name, "message" to text))
            }

            OPEN_PATTERNS.any { lower.startsWith(it) || lower.contains("$it ") } -> {
                val appName = extractAfter(lower, OPEN_PATTERNS)
                VoiceCommand(text, CommandType.OPEN_APP, mapOf("app" to appName))
            }

            YOUTUBE_PATTERNS.any { lower.contains(it) } -> {
                val song = extractAfter(lower, YOUTUBE_PATTERNS)
                VoiceCommand(text, CommandType.YOUTUBE_PLAY, mapOf("query" to song))
            }

            SPOTIFY_PATTERNS.any { lower.contains(it) } -> {
                val song = extractAfter(lower, SPOTIFY_PATTERNS)
                VoiceCommand(text, CommandType.SPOTIFY_PLAY, mapOf("query" to song))
            }

            else -> VoiceCommand(text, CommandType.CONVERSATION)
        }
    }

    private fun parseStructured(text: String): VoiceCommand? {
        val t = text.trim()
        return when {
            t.startsWith("OPEN_APP", true) -> {
                val app = t.removePrefix("OPEN_APP").removePrefix(":").trim()
                VoiceCommand(t, CommandType.OPEN_APP, mapOf("app" to app))
            }
            t.startsWith("CALL", true) && !t.startsWith("WHATSAPP_CALL", true) -> {
                val name = t.removePrefix("CALL").trim()
                VoiceCommand(t, CommandType.CALL, mapOf("name" to name))
            }
            t.startsWith("WHATSAPP_CALL", true) -> {
                val name = t.removePrefix("WHATSAPP_CALL").trim()
                VoiceCommand(t, CommandType.WHATSAPP_CALL, mapOf("name" to name))
            }
            t.startsWith("WHATSAPP_MSG", true) -> {
                val rest = t.removePrefix("WHATSAPP_MSG").trim()
                val parts = rest.split(" ", limit = 2)
                VoiceCommand(t, CommandType.WHATSAPP_MSG, mapOf(
                    "name" to (parts.getOrNull(0) ?: ""),
                    "message" to (parts.getOrNull(1) ?: "")
                ))
            }
            t.startsWith("YOUTUBE_PLAY", true) -> {
                val q = t.removePrefix("YOUTUBE_PLAY").trim()
                VoiceCommand(t, CommandType.YOUTUBE_PLAY, mapOf("query" to q))
            }
            t.startsWith("SPOTIFY_PLAY", true) -> {
                val q = t.removePrefix("SPOTIFY_PLAY").trim()
                VoiceCommand(t, CommandType.SPOTIFY_PLAY, mapOf("query" to q))
            }
            t.equals("FLASHLIGHT_ON", true) -> VoiceCommand(t, CommandType.FLASHLIGHT_ON)
            t.equals("FLASHLIGHT_OFF", true) -> VoiceCommand(t, CommandType.FLASHLIGHT_OFF)
            t.equals("VOLUME_UP", true) -> VoiceCommand(t, CommandType.VOLUME_UP)
            t.equals("VOLUME_DOWN", true) -> VoiceCommand(t, CommandType.VOLUME_DOWN)
            else -> null
        }
    }

    private fun extractAfter(text: String, patterns: List<String>): String {
        for (p in patterns) {
            val idx = text.indexOf(p)
            if (idx != -1) {
                return text.substring(idx + p.length).trim()
            }
        }
        return ""
    }
}

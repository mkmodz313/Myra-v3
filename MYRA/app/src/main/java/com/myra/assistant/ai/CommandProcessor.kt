package com.myra.assistant.ai

import com.myra.assistant.models.CommandType

object CommandProcessor {
    fun process(input: String): String {
        val cmd = IntentAnalyzer.analyze(input)
        return when (cmd.type) {
            CommandType.OPEN_APP -> "OPEN_APP ${cmd.args["app"] ?: ""}"
            CommandType.CALL -> "CALL ${cmd.args["name"] ?: ""}"
            CommandType.WHATSAPP_CALL -> "WHATSAPP_CALL ${cmd.args["name"] ?: ""}"
            CommandType.WHATSAPP_MSG -> "WHATSAPP_MSG ${cmd.args["name"] ?: ""} ${cmd.args["message"] ?: ""}"
            CommandType.YOUTUBE_PLAY -> "YOUTUBE_PLAY ${cmd.args["query"] ?: ""}"
            CommandType.SPOTIFY_PLAY -> "SPOTIFY_PLAY ${cmd.args["query"] ?: ""}"
            CommandType.VOLUME_UP -> "VOLUME_UP"
            CommandType.VOLUME_DOWN -> "VOLUME_DOWN"
            CommandType.FLASHLIGHT_ON -> "FLASHLIGHT_ON"
            CommandType.FLASHLIGHT_OFF -> "FLASHLIGHT_OFF"
            else -> input
        }
    }
}

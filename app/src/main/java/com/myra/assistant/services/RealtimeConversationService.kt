package com.myra.assistant.services

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Convenience helper to manage the ForegroundVoiceService lifecycle.
 */
object RealtimeConversationService {

    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ForegroundVoiceService::class.java)
        )
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, ForegroundVoiceService::class.java))
    }

    fun isRunning() = ForegroundVoiceService.isRunning

    fun sendMessage(text: String) {
        ForegroundVoiceService.instance?.sendTextToGemini(text)
    }

    fun reconnect() {
        ForegroundVoiceService.instance?.reconnectGemini()
    }
}

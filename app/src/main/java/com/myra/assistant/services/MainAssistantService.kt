package com.myra.assistant.services

import android.content.Context

/**
 * Facade for the primary assistant service layer.
 * Routes to ForegroundVoiceService + SmartAccessibilityEngine.
 */
object MainAssistantService {

    fun initialize(context: Context) {
        RealtimeConversationService.start(context)
    }

    fun shutdown(context: Context) {
        RealtimeConversationService.stop(context)
    }

    fun speak(text: String) {
        RealtimeConversationService.sendMessage(text)
    }

    fun isActive() = RealtimeConversationService.isRunning()
}

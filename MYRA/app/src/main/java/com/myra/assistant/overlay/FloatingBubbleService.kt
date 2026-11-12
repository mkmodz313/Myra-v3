package com.myra.assistant.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Alias service — actual floating bubble is handled by MyraOverlayService.
 * This exists for the project structure as specified in the prompt.
 */
class FloatingBubbleService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FloatingAssistantController.show(this)
        stopSelf()
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

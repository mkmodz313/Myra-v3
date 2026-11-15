package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.myra.assistant.ui.main.MainActivity

class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private var pressCount = 0
        private var lastPressTime = 0L
        private const val DOUBLE_PRESS_WINDOW = 700L
        private const val TAG = "MYRA_POWER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_SCREEN_ON -> detectDoublePress(context)
        }
    }

    private fun detectDoublePress(context: Context) {
        val now = System.currentTimeMillis()

        if (now - lastPressTime <= DOUBLE_PRESS_WINDOW) {
            pressCount++

            if (pressCount >= 2) {
                pressCount = 0
                launchMyra(context)
            }

        } else {
            pressCount = 1
        }

        lastPressTime = now
    }

    private fun launchMyra(context: Context) {
        Log.d(TAG, "Double power press detected")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity: ${e.message}")
        }
    }
}
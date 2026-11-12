package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.myra.assistant.services.ForegroundVoiceService
import com.myra.assistant.utils.Constants

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val apiKey = com.myra.assistant.utils.ApiKeys.gemini(context)
            if (apiKey.isNotEmpty()) {
                try { ContextCompat.startForegroundService(context, Intent(context, ForegroundVoiceService::class.java)) } catch (e: Exception) { }
            }
        }
    }
}

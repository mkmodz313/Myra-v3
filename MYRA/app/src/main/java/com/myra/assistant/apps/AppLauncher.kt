package com.myra.assistant.apps

import android.content.Context
import android.content.Intent
import com.myra.assistant.utils.Logger

object AppLauncher {
    private val TAG = "LAUNCHER"

    fun launch(context: Context, appName: String): Boolean {
        val intent = InstalledAppsManager.getLaunchIntent(context, appName) ?: run {
            Logger.w(TAG, "App not found: $appName")
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            Logger.d(TAG, "Launched: $appName")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Launch failed: ${e.message}")
            return false
        }
    }
}

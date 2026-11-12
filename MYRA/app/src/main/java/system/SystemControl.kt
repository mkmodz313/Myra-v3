package com.myra.assistant.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast

class SystemController(private val context: Context) {

    fun openApp(spokenName: String) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val cleanedInput = spokenName
            .lowercase()
            .replace("open", "")
            .replace("khol", "")
            .replace("launch", "")
            .trim()

        for (app in apps) {
            val appLabel = pm.getApplicationLabel(app)
                .toString()
                .lowercase()

            if (
                appLabel == cleanedInput ||
                appLabel.contains(cleanedInput) ||
                cleanedInput.contains(appLabel)
            ) {

                val intent = pm.getLaunchIntentForPackage(app.packageName)

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            }
        }

        Toast.makeText(
            context,
            "$spokenName not installed",
            Toast.LENGTH_SHORT
        ).show()
    }
}
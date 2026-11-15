package com.myra.assistant.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.myra.assistant.models.AppModel
import com.myra.assistant.utils.Logger

object InstalledAppsManager {
    private val TAG = "APPS"
    private var cachedApps: List<AppModel>? = null

    fun getAllApps(context: Context): List<AppModel> {
        if (cachedApps != null) return cachedApps!!

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        cachedApps = apps.mapNotNull { info ->
            try {
                val label = pm.getApplicationLabel(info).toString()
                val launchIntent = pm.getLaunchIntentForPackage(info.packageName)
                if (launchIntent != null) {
                    AppModel(
                        name = label.lowercase(),
                        packageName = info.packageName,
                        label = label
                    )
                } else null
            } catch (e: Exception) { null }
        }.sortedBy { it.label }

        Logger.d(TAG, "Loaded ${cachedApps?.size} apps")
        return cachedApps!!
    }

    fun findApp(context: Context, query: String): AppModel? {
        val normalized = query.lowercase().trim()
        val apps = getAllApps(context)

        // Exact match first
        apps.find { it.name == normalized || it.packageName.contains(normalized) }
            ?.let { return it }

        // Contains match
        apps.find { it.name.contains(normalized) || normalized.contains(it.name) }
            ?.let { return it }

        // Partial word match
        val words = normalized.split(" ")
        return apps.find { app ->
            words.any { word -> app.name.contains(word) }
        }
    }

    fun getLaunchIntent(context: Context, query: String): Intent? {
        val app = findApp(context, query) ?: return null
        return context.packageManager.getLaunchIntentForPackage(app.packageName)
    }

    fun invalidateCache() { cachedApps = null }
}

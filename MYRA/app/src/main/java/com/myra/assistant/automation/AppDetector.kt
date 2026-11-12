package com.myra.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

object AppDetector {

    private const val TAG = "MYRA_APP_DETECTOR"

    data class InstalledApp(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean
    )

    private fun normalizeName(value: String): String {
        return value
            .lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getLaunchableApps(
        service: AccessibilityService,
        includeSystem: Boolean = true
    ): List<InstalledApp> {

        val pm = service.packageManager
        val apps = mutableListOf<InstalledApp>()
        val seen = mutableSetOf<String>()

        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = pm.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL
            )

            for (resolveInfo in resolveInfos) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val packageName = appInfo.packageName
                if (!seen.add(packageName)) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) continue

                val label = pm.getApplicationLabel(appInfo).toString()
                apps.add(
                    InstalledApp(
                        packageName = packageName,
                        label = label,
                        isSystemApp = isSystem
                    )
                )
            }

            Log.d(TAG, "Found ${apps.size} launchable apps")

        } catch (e: Exception) {
            Log.e(TAG, "Error getting launchable apps: ${e.message}")
        }

        return apps
    }

    /**
     * Get all installed applications.
     */
    private fun getAllInstalledApps(
        service: AccessibilityService,
        includeSystem: Boolean = true
    ): List<InstalledApp> {

        val pm = service.packageManager
        val apps = mutableListOf<InstalledApp>()

        try {
            val packages = pm.getInstalledApplications(0)

            for (appInfo in packages) {
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) continue

                val label = pm.getApplicationLabel(appInfo).toString()
                apps.add(
                    InstalledApp(
                        packageName = appInfo.packageName,
                        label = label,
                        isSystemApp = isSystem
                    )
                )
            }

            Log.d(TAG, "Found ${apps.size} installed apps")

        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps: ${e.message}")
        }

        return apps
    }

    fun getInstalledApps(
        service: AccessibilityService
    ): List<InstalledApp> {
        return getAllInstalledApps(service, includeSystem = true)
    }

    /**
     * Find an app by name (fuzzy match)
     */
    fun findAppByName(
        service: AccessibilityService,
        appName: String
    ): InstalledApp? {

        val query = normalizeName(appName)
        if (query.isBlank()) return null

        val apps = getLaunchableApps(service, includeSystem = true)

        val exact = apps.find {
            normalizeName(it.label) == query ||
                normalizeName(it.packageName) == query
        }
        if (exact != null) return exact

        val starts = apps.find {
            val label = normalizeName(it.label)
            val pkg = normalizeName(it.packageName)
            label.startsWith(query) || pkg.startsWith(query)
        }
        if (starts != null) return starts

        return apps
            .map { app ->
                val label = normalizeName(app.label)
                val pkg = normalizeName(app.packageName)
                var score = 0

                if (label == query || pkg == query) score += 100
                if (label.contains(query) || pkg.contains(query)) score += 50
                if (label.split(" ").all { query.contains(it) }) score += 20
                if (query.split(" ").all { label.contains(it) || pkg.contains(it) }) score += 10

                app to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .firstOrNull()
            ?.first
    }

    fun findAppByKeywords(
        service: AccessibilityService,
        keywords: List<String>
    ): InstalledApp? {

        val apps = getLaunchableApps(service, includeSystem = true)
        val normalizedKeywords = keywords.map { normalizeName(it) }

        return apps
            .map { app ->
                val label = normalizeName(app.label)
                val pkg = normalizeName(app.packageName)
                val score = normalizedKeywords.sumOf { keyword ->
                    when {
                        label.contains(keyword) -> 30
                        pkg.contains(keyword) -> 20
                        else -> 0
                    }
                }
                app to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .firstOrNull()
            ?.first
    }

    /**
     * Get all installed apps including system apps
     */
    fun getAllApps(
        service: AccessibilityService,
        includeSystem: Boolean = false
    ): List<InstalledApp> {
        return getAllInstalledApps(service, includeSystem)
    }
}

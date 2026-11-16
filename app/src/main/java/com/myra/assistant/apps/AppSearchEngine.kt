package com.myra.assistant.apps

import android.content.Context

object AppSearchEngine {
    fun search(context: Context, query: String) = InstalledAppsManager.findApp(context, query)
    fun searchAll(context: Context, query: String) =
        InstalledAppsManager.getAllApps(context).filter {
            it.name.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
}

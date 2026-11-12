package com.myra.assistant.apps

import android.content.Context
import com.myra.assistant.models.AppModel

object PackageScanner {
    fun scanAll(context: Context): List<AppModel> = InstalledAppsManager.getAllApps(context)
    fun refresh(context: Context): List<AppModel> {
        InstalledAppsManager.invalidateCache()
        return InstalledAppsManager.getAllApps(context)
    }
}

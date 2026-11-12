package com.myra.assistant.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class MyraDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}

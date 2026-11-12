package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        private const val TAG = "MYRA_ACCESS"

        var instance: AccessibilityHelperService? = null
        var currentRoot: AccessibilityNodeInfo? = null

        fun isEnabled(context: Context): Boolean {
            val expectedComponentName =
                ComponentName(
                    context,
                    AccessibilityHelperService::class.java
                )

            val enabledServicesSetting =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

            val colonSplitter =
                TextUtils.SimpleStringSplitter(':')

            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()

                val enabledService =
                    ComponentName.unflattenFromString(
                        componentNameString
                    )

                if (enabledService == expectedComponentName) {
                    return true
                }
            }

            return false
        }

        fun getFreshRoot(): AccessibilityNodeInfo? {
            return instance?.rootInActiveWindow ?: currentRoot
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        instance = this
        SmartAccessibilityEngine.service = this

        serviceInfo = AccessibilityServiceInfo().apply {

            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED

            feedbackType =
                AccessibilityServiceInfo.FEEDBACK_GENERIC

            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

            notificationTimeout = 50
        }

        currentRoot = rootInActiveWindow

        Log.d(TAG, "==============================")
        Log.d(TAG, "MYRA ACCESSIBILITY CONNECTED")
        Log.d(TAG, "==============================")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        currentRoot = rootInActiveWindow
        com.myra.assistant.accessibility.AccessibilityEventManager.onEvent(event)
        Log.d(TAG, "EVENT -> ${event.packageName} | ${event.className}")
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICE INTERRUPTED")
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null
        currentRoot = null
        SmartAccessibilityEngine.service = null

        Log.d(TAG, "SERVICE DESTROYED")
    }
}
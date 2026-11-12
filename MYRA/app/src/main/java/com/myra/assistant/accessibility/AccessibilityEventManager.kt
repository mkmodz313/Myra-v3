package com.myra.assistant.accessibility

import android.view.accessibility.AccessibilityEvent

object AccessibilityEventManager {
    private var lastPackage = ""
    private var lastEventType = -1
    fun onEvent(e: AccessibilityEvent) { lastEventType = e.eventType; e.packageName?.let { lastPackage = it.toString() } }
    fun getCurrentPackage() = lastPackage
    fun getLastEventType() = lastEventType
}

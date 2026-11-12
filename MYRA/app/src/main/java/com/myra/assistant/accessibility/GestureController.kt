package com.myra.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build

object GestureController {
    fun swipeUp(svc: AccessibilityService, w: Int, h: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(w/2f, h*0.75f); lineTo(w/2f, h*0.25f) }
        svc.dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300)).build(), null, null)
    }
    fun tap(svc: AccessibilityService, x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x, y) }
        svc.dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build(), null, null)
    }
}

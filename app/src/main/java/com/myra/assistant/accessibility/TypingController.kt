package com.myra.assistant.accessibility

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.myra.assistant.service.SmartAccessibilityEngine

object TypingController {
    fun typeText(text: String): Boolean {
        val root = SmartAccessibilityEngine.service?.rootInActiveWindow ?: return false
        val et = NodeFinder.findEditText(root) ?: return false
        et.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return et.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}

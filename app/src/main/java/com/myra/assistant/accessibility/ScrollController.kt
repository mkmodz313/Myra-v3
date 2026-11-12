package com.myra.assistant.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.myra.assistant.service.SmartAccessibilityEngine

object ScrollController {
    fun scrollDown() = scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    fun scrollUp() = scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    private fun scroll(action: Int): Boolean {
        val root = SmartAccessibilityEngine.service?.rootInActiveWindow ?: return false
        return doScroll(root, action)
    }
    private fun doScroll(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) return node.performAction(action)
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (doScroll(c, action)) return true
        }
        return false
    }
}

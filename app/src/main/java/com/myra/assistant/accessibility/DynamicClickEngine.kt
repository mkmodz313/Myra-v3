package com.myra.assistant.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.myra.assistant.service.SmartAccessibilityEngine

object DynamicClickEngine {
    fun clickByText(text: String): Boolean {
        val root = SmartAccessibilityEngine.service?.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, text) ?: return false
        return click(node)
    }
    private fun click(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var p = node.parent; var d = 0
        while (p != null && d++ < 5) {
            if (p.isClickable) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            p = p.parent
        }
        return false
    }
}

package com.myra.assistant.accessibility

import android.view.accessibility.AccessibilityNodeInfo

object NodeFinder {
    fun findByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString()?.contains(text, true) == true) return root
        if (root.contentDescription?.toString()?.contains(text, true) == true) return root
        for (i in 0 until root.childCount) { findByText(root.getChild(i), text)?.let { return it } }
        return null
    }
    fun findEditText(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.className?.contains("EditText") == true) return root
        for (i in 0 until root.childCount) { findEditText(root.getChild(i))?.let { return it } }
        return null
    }
    fun findClickable(root: AccessibilityNodeInfo?, hint: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val m = root.text?.toString()?.contains(hint, true) == true ||
                root.contentDescription?.toString()?.contains(hint, true) == true
        if (root.isClickable && m) return root
        for (i in 0 until root.childCount) { findClickable(root.getChild(i), hint)?.let { return it } }
        return null
    }
}

package com.myra.assistant.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.myra.assistant.models.ScreenNodeModel

object NodeReader {
    fun readAll(root: AccessibilityNodeInfo?): List<ScreenNodeModel> {
        val nodes = mutableListOf<ScreenNodeModel>()
        if (root == null) return nodes
        traverse(root, nodes)
        return nodes
    }

    private fun traverse(node: AccessibilityNodeInfo, list: MutableList<ScreenNodeModel>) {
        list.add(ScreenNodeModel(
            text = node.text?.toString(),
            contentDesc = node.contentDescription?.toString(),
            className = node.className?.toString(),
            isClickable = node.isClickable,
            isEditable = node.isEditable,
            bounds = Rect().also { node.getBoundsInScreen(it) },
            viewId = node.viewIdResourceName
        ))
        for (i in 0 until node.childCount) { node.getChild(i)?.let { traverse(it, list) } }
    }

    fun dumpText(root: AccessibilityNodeInfo?): String =
        readAll(root).mapNotNull { it.text?.ifBlank { null } ?: it.contentDesc?.ifBlank { null } }
            .joinToString(" | ")
}

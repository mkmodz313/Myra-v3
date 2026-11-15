package com.myra.assistant.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object UiTreeSerializer {

    private const val TAG = "MYRA_UI_TREE"

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun serialize(root: AccessibilityNodeInfo?): String {
        if (root == null) return "[]"

        val array = JSONArray()
        traverse(root, array)

        return array.toString()
    }

    private fun traverse(
        node: AccessibilityNodeInfo,
        array: JSONArray
    ) {

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        val obj = JSONObject().apply {
            put("text", text)
            put("desc", desc)
            put("class", node.className?.toString() ?: "")
            put("clickable", node.isClickable)
            put("enabled", node.isEnabled)
            put("visible", node.isVisibleToUser)
            put(
                "bounds",
                "${rect.left},${rect.top},${rect.right},${rect.bottom}"
            )
        }

        array.put(obj)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                traverse(it, array)
            }
        }
    }

    /**
     * Find elements matching a query in the UI tree
     */
    fun findMatchingElements(
        root: AccessibilityNodeInfo?,
        query: String
    ): List<AccessibilityNodeInfo> {

        if (root == null) return emptyList()

        val results = mutableListOf<AccessibilityNodeInfo>()
        val queryLower = normalize(query)

        findMatchingElementsRecursive(root, queryLower, results)

        return results
    }

    private fun findMatchingElementsRecursive(
        node: AccessibilityNodeInfo,
        query: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {

        val text = normalize(node.text?.toString() ?: "")
        val desc = normalize(node.contentDescription?.toString() ?: "")

        // Match if text or description contains query
        if ((text.contains(query) || desc.contains(query)) &&
            (node.isClickable || text.isNotEmpty())) {
            results.add(node)
        }

        // Recurse through children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                findMatchingElementsRecursive(it, query, results)
            }
        }
    }

    /**
     * Get all clickable elements
     */
    fun findClickableElements(
        root: AccessibilityNodeInfo?
    ): List<AccessibilityNodeInfo> {

        if (root == null) return emptyList()

        val results = mutableListOf<AccessibilityNodeInfo>()
        findClickableElementsRecursive(root, results)

        return results
    }

    private fun findClickableElementsRecursive(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>
    ) {

        if (node.isClickable && node.isVisibleToUser) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                findClickableElementsRecursive(it, results)
            }
        }
    }

    /**
     * Get node center coordinates
     */
    fun getNodeCenter(node: AccessibilityNodeInfo): Pair<Int, Int> {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        return Pair(
            (rect.left + rect.right) / 2,
            (rect.top + rect.bottom) / 2
        )
    }
}
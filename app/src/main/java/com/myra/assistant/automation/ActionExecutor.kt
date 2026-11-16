package com.myra.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object ActionExecutor {

    private const val TAG = "MYRA_ACTION"

    /**
     * Click element by text (smart matching)
     */
    fun clickByText(
        service: AccessibilityService,
        text: String
    ): Boolean {

        val root = service.rootInActiveWindow ?: return false

        Log.d(TAG, "Searching for text: $text")

        // Try exact match first
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNotEmpty()) {
            return performClick(nodes[0])
        }

        // Try case-insensitive partial match
        val matches = UiTreeSerializer.findMatchingElements(root, text)
        if (matches.isNotEmpty()) {
            return performClick(matches[0])
        }

        Log.d(TAG, "No matching element found for: $text")
        return false
    }

    /**
     * Click by content description
     */
    fun clickByDescription(
        service: AccessibilityService,
        description: String
    ): Boolean {

        val root = service.rootInActiveWindow ?: return false

        Log.d(TAG, "Searching by description: $description")

        val nodes = root.findAccessibilityNodeInfosByText(description)
        if (nodes.isNotEmpty()) {
            return performClick(nodes[0])
        }

        return false
    }

    /**
     * Click by view ID (requires resource name)
     */
    fun clickById(
        service: AccessibilityService,
        resourceId: String
    ): Boolean {

        val root = service.rootInActiveWindow ?: return false

        Log.d(TAG, "Searching by ID: $resourceId")

        val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
        if (nodes.isNotEmpty()) {
            return performClick(nodes[0])
        }

        return false
    }

    /**
     * Click element based on semantic meaning
     * (e.g., "click the send button")
     */
    fun clickByIntention(
        service: AccessibilityService,
        intention: String
    ): Boolean {

        val root = service.rootInActiveWindow ?: return false
        val query = intention.lowercase()

        Log.d(TAG, "Finding element for intention: $intention")

        // Analyze intention to find relevant keywords
        val keywords = extractKeywords(intention)

        for (keyword in keywords) {
            val matches = UiTreeSerializer.findMatchingElements(root, keyword)
            if (matches.isNotEmpty()) {
                // Prefer clickable elements
                val clickable = matches.find { it.isClickable }
                if (clickable != null) {
                    return performClick(clickable)
                }
                return performClick(matches[0])
            }
        }

        return false
    }

    /**
     * Perform click on a node (handles parent search for clickable)
     */
    fun performClick(
        node: AccessibilityNodeInfo?
    ): Boolean {

        var current = node

        while (current != null) {
            if (current.isClickable && current.isEnabled) {
                Log.d(TAG, "Clicking on: ${current.text}")

                return current.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }
            current = current.parent
        }

        Log.d(TAG, "No clickable parent found")
        return false
    }

    /**
     * Tap at specific coordinates
     */
    fun tap(
        service: AccessibilityService,
        x: Int,
        y: Int
    ): Boolean {

        Log.d(TAG, "Tapping at: $x, $y")

        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    50  // 50ms duration
                )
            )
            .build()

        return service.dispatchGesture(
            gesture,
            null,
            null
        )
    }

    /**
     * Swipe gesture
     */
    fun swipe(
        service: AccessibilityService,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = 300
    ): Boolean {

        Log.d(TAG, "Swiping from ($startX,$startY) to ($endX,$endY)")

        val path = Path()
        path.moveTo(startX.toFloat(), startY.toFloat())
        path.lineTo(endX.toFloat(), endY.toFloat())

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    duration
                )
            )
            .build()

        return service.dispatchGesture(
            gesture,
            null,
            null
        )
    }

    /**
     * Long press gesture
     */
    fun longPress(
        service: AccessibilityService,
        x: Int,
        y: Int,
        duration: Long = 500
    ): Boolean {

        Log.d(TAG, "Long pressing at: $x, $y")

        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    duration
                )
            )
            .build()

        return service.dispatchGesture(
            gesture,
            null,
            null
        )
    }

    /**
     * Extract keywords from intention string
     */
    private fun extractKeywords(intention: String): List<String> {
        val words = intention.lowercase()
            .split(" ")
            .filter { it.length > 2 }  // Skip short words

        return words + intention.lowercase()  // Include full query too
    }

    /**
     * Get first clickable element on screen
     */
    fun getFirstClickable(
        service: AccessibilityService
    ): AccessibilityNodeInfo? {

        val root = service.rootInActiveWindow ?: return null
        val clickable = UiTreeSerializer.findClickableElements(root)

        return if (clickable.isNotEmpty()) clickable[0] else null
    }
}
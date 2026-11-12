package com.myra.assistant.service

import android.util.Log
import com.myra.assistant.automation.ActionExecutor
import com.myra.assistant.automation.UiTreeSerializer

object ScreenVisionAnalyzer {

    private const val TAG = "MYRA_VISION"

    data class ScreenPoint(
        val x: Int,
        val y: Int
    )

    /**
     * Find interactive element based on command
     * NO HARDCODED COORDINATES - Dynamic UI analysis
     */
    fun findInteractiveElement(
        command: String,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): ScreenPoint? {

        val service = SmartAccessibilityEngine.service ?: return null
        val root = service.rootInActiveWindow ?: return null

        Log.d(TAG, "Finding element for: $command")

        // Try to find matching elements using UI tree
        val matches = UiTreeSerializer.findMatchingElements(root, command)

        if (matches.isNotEmpty()) {
            val element = matches[0]
            val center = UiTreeSerializer.getNodeCenter(element)

            Log.d(TAG, "Found element at: ${center.first}, ${center.second}")

            return ScreenPoint(center.first, center.second)
        }

        return null
    }

    /**
     * Find button or clickable element for semantic intent
     * E.g., "send", "submit", "next", "share"
     */
    fun findButtonByIntent(
        intent: String,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): ScreenPoint? {

        val service = SmartAccessibilityEngine.service ?: return null
        val root = service.rootInActiveWindow ?: return null

        Log.d(TAG, "Finding button for intent: $intent")

        // Common button keywords
        val buttonKeywords = when (intent.lowercase()) {
            "send" -> listOf("send", "submit", "post")
            "next" -> listOf("next", "continue", "proceed")
            "back" -> listOf("back", "cancel", "close")
            "menu" -> listOf("menu", "more", "options")
            "play" -> listOf("play", "start", "resume")
            "call" -> listOf("call", "dial", "contact")
            "share" -> listOf("share", "export", "forward")
            else -> listOf(intent)
        }

        // Find clickable elements matching keywords
        for (keyword in buttonKeywords) {
            val matches = UiTreeSerializer.findMatchingElements(root, keyword)

            if (matches.isNotEmpty()) {
                val clickables = matches.filter { it.isClickable }
                if (clickables.isNotEmpty()) {
                    val center = UiTreeSerializer.getNodeCenter(clickables[0])
                    Log.d(TAG, "Found $intent button at: ${center.first}, ${center.second}")
                    return ScreenPoint(center.first, center.second)
                }
            }
        }

        // Fallback: find any clickable on screen
        val allClickable = UiTreeSerializer.findClickableElements(root)
        if (allClickable.isNotEmpty()) {
            val center = UiTreeSerializer.getNodeCenter(allClickable[0])
            Log.d(TAG, "Using first clickable at: ${center.first}, ${center.second}")
            return ScreenPoint(center.first, center.second)
        }

        return null
    }

    /**
     * Analyze screen and execute action based on vision
     * SMART: Uses actual UI elements, not hardcoded positions
     */
    fun analyzeAndExecute(command: String): Boolean {

        val service = SmartAccessibilityEngine.service ?: return false

        Log.d(TAG, "Analyze & Execute -> $command")

        // Try to find and click element directly
        val point = findInteractiveElement(command)

        if (point != null) {
            Log.d(TAG, "Tapping at found element position")
            return ActionExecutor.tap(service, point.x, point.y)
        }

        // Try semantic intent matching
        val intent = extractIntent(command)
        if (intent.isNotEmpty()) {
            val buttonPoint = findButtonByIntent(intent)
            if (buttonPoint != null) {
                Log.d(TAG, "Tapping button for intent: $intent")
                return ActionExecutor.tap(service, buttonPoint.x, buttonPoint.y)
            }
        }

        return false
    }

    /**
     * Extract the main intent from command
     * E.g., "click send button" -> "send"
     */
    private fun extractIntent(command: String): String {
        return command
            .replace("click", "", ignoreCase = true)
            .replace("tap", "", ignoreCase = true)
            .replace("press", "", ignoreCase = true)
            .replace("button", "", ignoreCase = true)
            .trim()
    }

    /**
     * Get all interactive elements on current screen
     */
    fun getAllInteractiveElements(): List<ScreenPoint> {
        val service = SmartAccessibilityEngine.service ?: return emptyList()
        val root = service.rootInActiveWindow ?: return emptyList()

        val clickable = UiTreeSerializer.findClickableElements(root)

        return clickable.map { element ->
            val center = UiTreeSerializer.getNodeCenter(element)
            ScreenPoint(center.first, center.second)
        }
    }

    /**
     * Check if element with text is visible and clickable
     */
    fun isElementInteractive(text: String): Boolean {
        val service = SmartAccessibilityEngine.service ?: return false
        val root = service.rootInActiveWindow ?: return false

        val matches = UiTreeSerializer.findMatchingElements(root, text)

        return matches.any { 
            it.isClickable && it.isVisibleToUser 
        }
    }

    /**
     * Legacy method for compatibility (no hardcoded values)
     * Redirects to smart analysis
     */
    fun analyze(
        command: String,
        screenDump: String,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): ScreenPoint? {
        return findInteractiveElement(command, screenWidth, screenHeight)
    }

    fun clickByVision(command: String): Boolean {
        val service = SmartAccessibilityEngine.service ?: return false

        Log.d(TAG, "Click by vision: $command")

        return when {
            analyzeAndExecute(command) -> true
            ActionExecutor.clickByText(service, command) -> true
            ActionExecutor.clickByIntention(service, command) -> true
            else -> false
        }
    }
}
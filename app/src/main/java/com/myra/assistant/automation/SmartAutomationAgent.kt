package com.myra.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log

object SmartAutomationAgent {

    private const val TAG = "MYRA_AGENT"

    fun run(
        service: AccessibilityService,
        command: String
    ): Boolean {

        Log.d(TAG, "Running smart command: $command")

        return when {
            // Try direct UI tree analysis first
            analyzeAndClick(service, command) -> true

            // Try semantic intention matching
            ActionExecutor.clickByIntention(service, command) -> true

            // Fallback: try removing common words and clicking
            clickWithCleanedCommand(service, command) -> true

            else -> {
                Log.d(TAG, "Could not execute: $command")
                false
            }
        }
    }

    /**
     * Analyze UI tree and find relevant clickable elements
     */
    private fun analyzeAndClick(
        service: AccessibilityService,
        command: String
    ): Boolean {

        val root = service.rootInActiveWindow ?: return false

        // Get relevant elements based on command
        val relevant = UiTreeSerializer.findMatchingElements(
            root,
            command
        )

        if (relevant.isEmpty()) {
            Log.d(TAG, "No matching elements found for: $command")
            return false
        }

        Log.d(TAG, "Found ${relevant.size} matching elements")

        // Try to click the first matching clickable element
        for (element in relevant) {
            if (ActionExecutor.performClick(element)) {
                Log.d(TAG, "Clicked element: ${element.text}")
                return true
            }
        }

        return false
    }

    /**
     * Try clicking with cleaned command (remove common words)
     */
    private fun clickWithCleanedCommand(
        service: AccessibilityService,
        command: String
    ): Boolean {

        val target = command
            .replace("click", "", ignoreCase = true)
            .replace("open", "", ignoreCase = true)
            .replace("tap", "", ignoreCase = true)
            .replace("press", "", ignoreCase = true)
            .trim()

        if (target.isNotEmpty()) {
            return ActionExecutor.clickByText(service, target)
        }

        return false
    }
}

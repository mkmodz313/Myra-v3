package com.myra.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ScreenMonitor {

    private const val TAG = "MYRA_SCREEN_MONITOR"

    private var lastUiTree: String = ""
    private var lastPackage: String = ""
    private var monitoringScope: CoroutineScope? = null

    data class ScreenState(
        val uiTree: String,
        val packageName: String,
        val timestamp: Long,
        val elements: List<UiElement>
    )

    data class UiElement(
        val text: String,
        val contentDesc: String,
        val className: String,
        val isClickable: Boolean,
        val bounds: String,
        val isVisible: Boolean
    )

    /**
     * Start continuous screen monitoring
     */
    fun startMonitoring(
        service: AccessibilityService,
        scope: CoroutineScope
    ) {
        monitoringScope = scope

        Log.d(TAG, "Screen monitoring started")
    }

    /**
     * Capture current screen state
     */
    fun captureScreenState(
        service: AccessibilityService
    ): ScreenState? {

        return try {
            val root = service.rootInActiveWindow 
                ?: return null

            val packageName = root.packageName?.toString() 
                ?: "unknown"

            val uiTree = UiTreeSerializer.serialize(root)
            val elements = extractElements(root)

            ScreenState(
                uiTree = uiTree,
                packageName = packageName,
                timestamp = System.currentTimeMillis(),
                elements = elements
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen: ${e.message}")
            null
        }
    }

    /**
     * Extract all clickable and text elements from the screen
     */
    private fun extractElements(
        node: android.view.accessibility.AccessibilityNodeInfo,
        elements: MutableList<UiElement> = mutableListOf()
    ): List<UiElement> {

        try {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)

            // Extract element only if it has text or content description
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""

            if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable) {
                elements.add(
                    UiElement(
                        text = text,
                        contentDesc = desc,
                        className = node.className?.toString() ?: "",
                        isClickable = node.isClickable,
                        bounds = "${rect.left},${rect.top}," +
                                "${rect.right},${rect.bottom}",
                        isVisible = node.isVisibleToUser
                    )
                )
            }

            // Recurse through children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let {
                    extractElements(it, elements)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting elements: ${e.message}")
        }

        return elements
    }

    /**
     * Find interactive elements related to a command
     */
    fun findRelevantElements(
        service: AccessibilityService,
        command: String
    ): List<UiElement> {

        val state = captureScreenState(service) 
            ?: return emptyList()

        val query = command.lowercase()

        return state.elements.filter { element ->
            val text = element.text.lowercase()
            val desc = element.contentDesc.lowercase()

            (element.isClickable || text.isNotEmpty()) &&
            (text.contains(query) || 
             desc.contains(query) ||
             query.contains(text) ||
             query.contains(desc))
        }
    }

    /**
     * Get all clickable elements on screen
     */
    fun getClickableElements(
        service: AccessibilityService
    ): List<UiElement> {

        val state = captureScreenState(service) 
            ?: return emptyList()

        return state.elements.filter { it.isClickable }
    }

    /**
     * Update UI tree after event (called from accessibility service)
     */
    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        service: AccessibilityService
    ) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                monitoringScope?.launch(Dispatchers.Default) {
                    val state = captureScreenState(service)
                    if (state != null) {
                        lastUiTree = state.uiTree
                        lastPackage = state.packageName

                        Log.d(TAG, "Screen updated: ${state.packageName}")
                    }
                }
            }
        }
    }

    /**
     * Get last captured screen state
     */
    fun getLastScreenState(): Pair<String, String> {
        return Pair(lastUiTree, lastPackage)
    }

    fun stopMonitoring() {
        Log.d(TAG, "Screen monitoring stopped")
    }
}

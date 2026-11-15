package com.myra.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AutomationManager {

    private const val TAG = "MYRA_AUTOMATION_MGR"

    private var scope: CoroutineScope? = null
    private var service: AccessibilityService? = null
    private var isAutomatic = true

    /**
     * Initialize automation manager
     */
    fun initialize(
        accessibilityService: AccessibilityService,
        coroutineScope: CoroutineScope
    ) {
        service = accessibilityService
        scope = coroutineScope

        // Start screen monitoring
        ScreenMonitor.startMonitoring(accessibilityService, coroutineScope)

        Log.d(TAG, "Automation manager initialized")
    }

    /**
     * Enable/disable automatic task execution
     */
    fun setAutomaticMode(enabled: Boolean) {
        isAutomatic = enabled
        Log.d(TAG, "Automatic mode: $enabled")
    }

    /**
     * Execute automation task from command
     */
    fun executeTask(command: String): Boolean {
        val svc = service ?: return false

        Log.d(TAG, "Execute task: $command")

        if (!isAutomatic) {
            Log.d(TAG, "Automatic mode disabled")
            return false
        }

        return when {
            // Handle app opening
            command.startsWith("OPEN_APP", ignoreCase = true) -> {
                handleOpenApp(svc, command)
            }

            // Handle clicks
            command.startsWith("CLICK", ignoreCase = true) -> {
                handleClick(svc, command)
            }

            // Handle searches
            command.startsWith("SEARCH", ignoreCase = true) -> {
                handleSearch(svc, command)
            }

            // Smart automation
            else -> {
                SmartAutomationAgent.run(svc, command)
            }
        }
    }

    /**
     * Handle OPEN_APP command with dynamic app detection
     */
    private fun handleOpenApp(
        service: AccessibilityService,
        command: String
    ): Boolean {

        val appName = command
            .removePrefix("OPEN_APP")
            .removePrefix(":")
            .trim()

        Log.d(TAG, "Opening app: $appName")

        val app = AppDetector.findAppByName(service, appName)

        if (app == null) {
            Log.e(TAG, "App not found: $appName")
            return false
        }

        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
                Log.d(TAG, "Opened: ${app.label}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: ${e.message}")
            false
        }
    }

    /**
     * Handle CLICK command
     */
    private fun handleClick(
        service: AccessibilityService,
        command: String
    ): Boolean {

        val target = command
            .removePrefix("CLICK")
            .trim()

        Log.d(TAG, "Clicking: $target")

        return ActionExecutor.clickByText(service, target)
    }

    /**
     * Handle SEARCH command
     */
    private fun handleSearch(
        service: AccessibilityService,
        command: String
    ): Boolean {

        val query = command
            .removePrefix("SEARCH")
            .trim()

        Log.d(TAG, "Searching: $query")

        // Implementation depends on the app
        // This is a placeholder
        return ActionExecutor.clickByIntention(service, "search $query")
    }

    /**
     * Get current screen state
     */
    fun getCurrentScreenState(): ScreenMonitor.ScreenState? {
        val svc = service ?: return null
        return ScreenMonitor.captureScreenState(svc)
    }

    /**
     * Get all installed apps
     */
    fun getInstalledApps(): List<AppDetector.InstalledApp> {
        val svc = service ?: return emptyList()
        return AppDetector.getInstalledApps(svc)
    }

    /**
     * Find app by name
     */
    fun findApp(appName: String): AppDetector.InstalledApp? {
        val svc = service ?: return null
        return AppDetector.findAppByName(svc, appName)
    }

    /**
     * Execute with timeout (for async operations)
     */
    fun executeAsync(command: String) {
        scope?.launch(Dispatchers.Main) {
            executeTask(command)
        }
    }

    /**
     * Shutdown automation
     */
    fun shutdown() {
        ScreenMonitor.stopMonitoring()
        service = null
        scope = null

        Log.d(TAG, "Automation manager shutdown")
    }
}

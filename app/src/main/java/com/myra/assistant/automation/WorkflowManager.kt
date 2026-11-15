package com.myra.assistant.automation

import com.myra.assistant.models.ActionModel
import com.myra.assistant.utils.Logger
import kotlinx.coroutines.delay

object WorkflowManager {
    private val TAG = "WORKFLOW"

    suspend fun runSequence(actions: List<ActionModel>, delayMs: Long = 500) {
        AutomationStateManager.setRunning()
        for (action in actions) {
            if (!AutomationStateManager.isRunning()) break
            val success = TaskExecutor.execute(action)
            Logger.d(TAG, "Action ${action.type}: $success")
            delay(delayMs)
        }
        AutomationStateManager.setIdle()
    }
}

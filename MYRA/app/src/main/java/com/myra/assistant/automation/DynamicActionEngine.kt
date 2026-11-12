package com.myra.assistant.automation

import android.content.Context
import com.myra.assistant.apps.AppLauncher
import com.myra.assistant.models.ActionModel
import com.myra.assistant.models.ActionType
import com.myra.assistant.screenvision.VisionDecisionEngine
import com.myra.assistant.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DynamicActionEngine {
    private val TAG = "DYNAMIC_ACTION"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun execute(context: Context, rawCommand: String) {
        val cmd = rawCommand.trim().uppercase()
        Logger.d(TAG, "Dynamic action: $cmd")

        when {
            cmd.startsWith("OPEN_APP") -> {
                val name = rawCommand.removePrefix("OPEN_APP").removePrefix(":").trim()
                AppLauncher.launch(context, name)
            }
            cmd.startsWith("CLICK") -> {
                val target = rawCommand.removePrefix("CLICK").trim()
                enqueue(ActionModel(ActionType.CLICK_TEXT, target = target))
            }
            cmd.startsWith("TYPE") -> {
                val text = rawCommand.removePrefix("TYPE").trim()
                enqueue(ActionModel(ActionType.TYPE_TEXT, value = text))
            }
            cmd.startsWith("SCROLL_DOWN") -> enqueue(ActionModel(ActionType.SCROLL_DOWN))
            cmd.startsWith("SCROLL_UP") -> enqueue(ActionModel(ActionType.SCROLL_UP))
            else -> {
                // Vision-based fallback
                scope.launch { VisionDecisionEngine.executeVisualAction(rawCommand) }
            }
        }
    }

    private fun enqueue(action: ActionModel) {
        ActionQueue.enqueue(action)
        scope.launch {
            if (!AutomationStateManager.isRunning()) {
                AutomationStateManager.setRunning()
                while (!ActionQueue.isEmpty()) {
                    ActionQueue.dequeue()?.let { TaskExecutor.execute(it) }
                    kotlinx.coroutines.delay(300)
                }
                AutomationStateManager.setIdle()
            }
        }
    }
}

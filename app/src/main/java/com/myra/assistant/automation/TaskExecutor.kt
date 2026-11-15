package com.myra.assistant.automation

import com.myra.assistant.accessibility.DynamicClickEngine
import com.myra.assistant.accessibility.ScrollController
import com.myra.assistant.accessibility.TypingController
import com.myra.assistant.models.ActionModel
import com.myra.assistant.models.ActionType
import com.myra.assistant.utils.Logger

object TaskExecutor {
    private val TAG = "TASK_EXEC"

    fun execute(action: ActionModel): Boolean {
        Logger.d(TAG, "Executing: ${action.type} target=${action.target}")
        return when (action.type) {
            ActionType.CLICK_TEXT -> DynamicClickEngine.clickByText(action.target)
            ActionType.TYPE_TEXT -> TypingController.typeText(action.value)
            ActionType.SCROLL_DOWN -> ScrollController.scrollDown()
            ActionType.SCROLL_UP -> ScrollController.scrollUp()
            else -> {
                Logger.w(TAG, "Unhandled action: ${action.type}")
                false
            }
        }
    }
}

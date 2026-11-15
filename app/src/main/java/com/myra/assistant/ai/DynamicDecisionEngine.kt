package com.myra.assistant.ai

import android.content.Context
import com.myra.assistant.apps.AppLauncher
import com.myra.assistant.models.CommandType
import com.myra.assistant.models.VoiceCommand
import com.myra.assistant.service.SmartAccessibilityEngine
import com.myra.assistant.utils.Logger

object DynamicDecisionEngine {
    private val TAG = "DECISION"

    suspend fun execute(context: Context, command: VoiceCommand): String {
        Logger.d(TAG, "Executing: ${command.type} - ${command.raw}")

        return when (command.type) {
            CommandType.OPEN_APP -> {
                val app = command.args["app"] ?: ""
                if (AppLauncher.launch(context, app)) "Opening $app"
                else "App not found: $app"
            }

            CommandType.VOLUME_UP -> {
                SmartAccessibilityEngine.execute("VOLUME_UP")
                "Volume badha diya"
            }

            CommandType.VOLUME_DOWN -> {
                SmartAccessibilityEngine.execute("VOLUME_DOWN")
                "Volume kam kiya"
            }

            CommandType.FLASHLIGHT_ON -> {
                toggleFlashlight(context, true)
                "Torch on kar diya"
            }

            CommandType.FLASHLIGHT_OFF -> {
                toggleFlashlight(context, false)
                "Torch off kar diya"
            }

            CommandType.WHATSAPP_CALL -> {
                SmartAccessibilityEngine.execute("WHATSAPP_CALL ${command.args["name"] ?: ""}")
                "WhatsApp call kar rahi hoon"
            }

            CommandType.WHATSAPP_MSG -> {
                val name = command.args["name"] ?: ""
                val msg = command.args["message"] ?: ""
                SmartAccessibilityEngine.execute("WHATSAPP_MSG $name $msg")
                "Message bhej rahi hoon"
            }

            CommandType.YOUTUBE_PLAY -> {
                val query = command.args["query"] ?: ""
                SmartAccessibilityEngine.execute("YOUTUBE_PLAY $query")
                "YouTube play kar rahi hoon"
            }

            CommandType.CALL -> {
                SmartAccessibilityEngine.execute("CALL ${command.args["name"] ?: ""}")
                "Call kar rahi hoon"
            }

            else -> ""
        }
    }

    private fun toggleFlashlight(context: Context, on: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cm.cameraIdList.firstOrNull() ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                cm.setTorchMode(cameraId, on)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Flashlight error: ${e.message}")
        }
    }
}

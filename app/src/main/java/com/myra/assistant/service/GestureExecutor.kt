package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

object GestureExecutor {

    private const val TAG = "MYRA_GESTURE"

    fun tap(
        service: AccessibilityService,
        x: Int,
        y: Int
    ): Boolean {

        return try {

            val path = Path().apply {
                moveTo(
                    x.toFloat(),
                    y.toFloat()
                )
            }

            val gesture =
                GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            path,
                            0,
                            120
                        )
                    )
                    .build()

            val success = service.dispatchGesture(
                gesture,
                null,
                null
            )

            Log.d(TAG, "Tap -> ($x,$y) : $success")

            success

        } catch (e: Exception) {
            Log.e(TAG, "Gesture failed: ${e.message}")
            false
        }
    }

    fun longTap(
        service: AccessibilityService,
        x: Int,
        y: Int
    ): Boolean {

        return try {

            val path = Path().apply {
                moveTo(
                    x.toFloat(),
                    y.toFloat()
                )
            }

            val gesture =
                GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            path,
                            0,
                            800
                        )
                    )
                    .build()

            service.dispatchGesture(
                gesture,
                null,
                null
            )

        } catch (e: Exception) {
            false
        }
    }

    fun swipe(
        service: AccessibilityService,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int
    ): Boolean {

        return try {

            val path = Path().apply {
                moveTo(
                    startX.toFloat(),
                    startY.toFloat()
                )

                lineTo(
                    endX.toFloat(),
                    endY.toFloat()
                )
            }

            val gesture =
                GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            path,
                            0,
                            300
                        )
                    )
                    .build()

            service.dispatchGesture(
                gesture,
                null,
                null
            )

        } catch (e: Exception) {
            false
        }
    }
}
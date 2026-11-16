package com.myra.assistant.automation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

object CallAutomation {

    private const val TAG = "MYRA_CALL"

    fun makeCall(
        context: Context,
        number: String
    ): Boolean {

        val cleanNumber = number
            .replace(" ", "")
            .replace("-", "")
            .trim()

        if (cleanNumber.isEmpty()) {
            Log.e(TAG, "Empty number")
            return false
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "CALL_PHONE permission denied")
            return false
        }

        return try {

            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            Log.d(TAG, "Calling -> $cleanNumber")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Call failed: ${e.message}")
            false
        }
    }

    fun openDialer(
        context: Context,
        number: String
    ): Boolean {

        return try {

            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true

        } catch (e: Exception) {
            false
        }
    }
}
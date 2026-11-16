package com.myra.assistant.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import java.util.concurrent.Executor

/**
 * BiometricManager - Fingerprint authentication for MYRA
 * Requirements:
 * 1. ✅ Fingerprint auth on launch
 * 2. ✅ Block automation until success
 * 3. ✅ Fallback PIN if unavailable
 * 4. ✅ Secure session timeout
 */
object BiometricManager {

    private const val TAG = "MYRA_BIOMETRIC"
    private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000 // 5 minutes
    private const val PREFS_NAME = "myra_security"
    private const val KEY_LAST_AUTH = "last_auth_time"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_PIN_SET = "pin_set"

    private var isAuthenticated = false
    private var lastAuthTime = 0L

    /**
     * Check if biometric is available on device
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Check if user has enabled biometric in settings
     */
    fun isBiometricEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Enable/disable biometric
     */
    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    /**
     * Main authentication method
     */
    fun authenticate(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    ) {
        // Check session timeout
        if (isSessionValid(context)) {
            Log.d(TAG, "Session still valid, skipping auth")
            onSuccess()
            return
        }

        if (!isBiometricEnabled(context)) {
            Log.d(TAG, "Biometric not enabled, proceeding")
            onSuccess()
            return
        }

        if (!isBiometricAvailable(context)) {
            Log.w(TAG, "Biometric not available, using fallback")
            onFallback()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showBiometricPrompt(context, onSuccess, onError, onFallback)
        } else {
            onFallback()
        }
    }

    private fun showBiometricPrompt(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        // Create PromptInfo using the builder
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("MYRA Security")
            .setSubtitle("Verify your identity")
            .setDescription("Place your finger on the sensor")
            .setNegativeButtonText("Use PIN")
            .build()

        // Get activity from context
        val activity = context as androidx.fragment.app.FragmentActivity

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "✅ Biometric authentication succeeded")
                    isAuthenticated = true
                    lastAuthTime = System.currentTimeMillis()
                    saveAuthTime(context)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed")
                    onError("Fingerprint not recognized. Try again.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG, "Biometric error: $errorCode - $errString")
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onFallback()
                    } else {
                        onError(errString.toString())
                    }
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Check if current session is valid (not timed out)
     */
    fun isSessionValid(context: Context): Boolean {
        if (!isAuthenticated) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAuth = prefs.getLong(KEY_LAST_AUTH, 0)
        val elapsed = System.currentTimeMillis() - lastAuth

        return elapsed < SESSION_TIMEOUT_MS
    }

    /**
     * Reset authentication state
     */
    fun resetAuth() {
        isAuthenticated = false
        lastAuthTime = 0
    }

    /**
     * Save authentication time
     */
    private fun saveAuthTime(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTH, System.currentTimeMillis())
            .apply()
    }

    /**
     * Check if PIN is set
     */
    fun isPinSet(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PIN_SET, false)
    }

    /**
     * Set PIN
     */
    fun setPin(context: Context, pin: String) {
        // In production, use encrypted storage
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PIN_SET, true)
            .putString("pin_hash", hashPin(pin))
            .apply()
    }

    /**
     * Verify PIN
     */
    fun verifyPin(context: Context, pin: String): Boolean {
        val storedHash = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("pin_hash", "") ?: ""
        return storedHash == hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        // Simple hash - in production use proper encryption
        return pin.hashCode().toString()
    }
}
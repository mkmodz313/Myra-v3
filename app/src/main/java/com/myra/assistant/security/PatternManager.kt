package com.myra.assistant.security

import android.content.Context
import android.util.Log

/**
 * PatternManager — Pattern save, verify aur clear karo
 */
object PatternManager {

    private const val TAG = "MYRA_PATTERN"
    private const val PREFS_NAME = "myra_security_prefs"
    private const val KEY_PATTERN      = "unlock_pattern"
    private const val KEY_PATTERN_ON   = "pattern_lock_enabled"
    private const val KEY_WRONG_COUNT  = "pattern_wrong_count"
    private const val KEY_LOCKOUT_TILL = "pattern_lockout_until"
    private const val MAX_ATTEMPTS     = 5
    private const val LOCKOUT_MS       = 30_000L

    fun savePattern(context: Context, pattern: List<Int>) {
        val raw = pattern.joinToString("-")
        val encrypted = SecurityManager.encrypt(raw)
        getPrefs(context).edit()
            .putString(KEY_PATTERN, encrypted)
            .putBoolean(KEY_PATTERN_ON, true)
            .apply()
        Log.d(TAG, "Pattern saved (${pattern.size} dots)")
    }

    sealed class PatternResult {
        object CORRECT    : PatternResult()
        object NOT_SET    : PatternResult()
        object TOO_SHORT  : PatternResult()
        data class WRONG(val attempts: Int, val lockedOut: Boolean) : PatternResult()
        data class LOCKED_OUT(val secondsRemaining: Long)           : PatternResult()
    }

    fun verify(context: Context, inputPattern: List<Int>): PatternResult {
        if (inputPattern.size < PatternLockView.MIN_PATTERN_LENGTH)
            return PatternResult.TOO_SHORT

        val prefs = getPrefs(context)
        val lockUntil = prefs.getLong(KEY_LOCKOUT_TILL, 0L)
        if (System.currentTimeMillis() < lockUntil) {
            val remaining = (lockUntil - System.currentTimeMillis()) / 1000
            return PatternResult.LOCKED_OUT(remaining)
        }

        val stored = prefs.getString(KEY_PATTERN, null)
            ?: return PatternResult.NOT_SET

        val decrypted = SecurityManager.decrypt(stored)
        val inputStr   = inputPattern.joinToString("-")

        return if (inputStr == decrypted) {
            resetAttempts(context)
            PatternResult.CORRECT
        } else {
            handleWrong(context)
        }
    }

    private fun handleWrong(context: Context): PatternResult.WRONG {
        val prefs    = getPrefs(context)
        val attempts = prefs.getInt(KEY_WRONG_COUNT, 0) + 1
        prefs.edit().putInt(KEY_WRONG_COUNT, attempts).apply()

        if (attempts >= MAX_ATTEMPTS) {
            val lockUntil = System.currentTimeMillis() + LOCKOUT_MS
            prefs.edit().putLong(KEY_LOCKOUT_TILL, lockUntil).apply()
            return PatternResult.WRONG(attempts, lockedOut = true)
        }
        return PatternResult.WRONG(attempts, lockedOut = false)
    }

    fun resetAttempts(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_WRONG_COUNT, 0)
            .putLong(KEY_LOCKOUT_TILL, 0L)
            .apply()
    }

    fun isPatternSet(context: Context): Boolean = getPrefs(context).contains(KEY_PATTERN)

    fun isPatternLockEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_PATTERN_ON, false)

    fun enablePatternLock(context: Context) = getPrefs(context).edit().putBoolean(KEY_PATTERN_ON, true).apply()

    fun disablePatternLock(context: Context) = getPrefs(context).edit().putBoolean(KEY_PATTERN_ON, false).apply()

    fun removePattern(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PATTERN)
            .putBoolean(KEY_PATTERN_ON, false)
            .apply()
    }

    fun getWrongAttempts(context: Context): Int = getPrefs(context).getInt(KEY_WRONG_COUNT, 0)

    fun getRemainingAttempts(context: Context): Int = (MAX_ATTEMPTS - getWrongAttempts(context)).coerceAtLeast(0)

    fun getLockoutRemaining(context: Context): Long {
        val until = getPrefs(context).getLong(KEY_LOCKOUT_TILL, 0L)
        return if (System.currentTimeMillis() < until)
            (until - System.currentTimeMillis()) / 1000
        else 0L
    }

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

package com.myra.assistant.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SecurityManager — AES-256 Encryption + PIN + Voice + App Lock per package
 *
 * ADDED: isPackageLocked() — AccessibilityHelperService ke liye
 */
object SecurityManager {

    private const val TAG = "MYRA_SECURITY"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "MYRA_MASTER_KEY"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private const val PREFS_NAME = "myra_security_prefs"
    private const val KEY_PIN_HASH       = "pin_hash"
    private const val KEY_VOICE_PHRASE   = "voice_passphrase"
    private const val KEY_APP_LOCK_ON    = "app_lock_enabled"
    private const val KEY_BIOMETRIC_ON   = "biometric_lock_enabled"
    private const val KEY_DEVICE_LOCK_ON = "device_lock_enabled"
    private const val KEY_PRIVATE_MODE   = "private_mode_active"
    private const val KEY_WRONG_ATTEMPTS = "wrong_attempts"
    private const val KEY_LOCKOUT_TIME   = "lockout_until"
    private const val KEY_LOCKED_PACKAGES= "locked_packages"
    private const val MAX_ATTEMPTS       = 3
    private const val LOCKOUT_MS         = 30_000L

    // ── KEYSTORE ────────────────────────────────────────────────

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    // ── ENCRYPT / DECRYPT ───────────────────────────────────────

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt failed: ${e.message}")
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        return try {
            val parts = cipherText.split(":")
            if (parts.size != 2) return cipherText
            val iv        = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed: ${e.message}")
            ""
        }
    }

    // ── PIN ─────────────────────────────────────────────────────

    fun setPin(context: Context, pin: String) {
        val hash = hashPin(pin)
        getPrefs(context).edit().putString(KEY_PIN_HASH, encrypt(hash)).apply()
    }

    fun verifyPin(context: Context, inputPin: String): PinResult {
        val lockUntil = getPrefs(context).getLong(KEY_LOCKOUT_TIME, 0L)
        if (System.currentTimeMillis() < lockUntil) {
            val rem = (lockUntil - System.currentTimeMillis()) / 1000
            return PinResult.LOCKED_OUT(rem)
        }
        val stored = getPrefs(context).getString(KEY_PIN_HASH, null) ?: return PinResult.NOT_SET
        val inputHash = hashPin(inputPin)
        return if (inputHash == decrypt(stored)) {
            resetAttempts(context)
            PinResult.CORRECT
        } else {
            handleWrongAttempt(context)
        }
    }

    private fun handleWrongAttempt(context: Context): PinResult.WRONG {
        val prefs = getPrefs(context)
        val attempts = prefs.getInt(KEY_WRONG_ATTEMPTS, 0) + 1
        prefs.edit().putInt(KEY_WRONG_ATTEMPTS, attempts).apply()
        if (attempts >= MAX_ATTEMPTS) {
            prefs.edit().putLong(KEY_LOCKOUT_TIME, System.currentTimeMillis() + LOCKOUT_MS).apply()
            return PinResult.WRONG(attempts, lockedOut = true)
        }
        return PinResult.WRONG(attempts, lockedOut = false)
    }

    fun resetAttempts(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_WRONG_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIME, 0L)
            .apply()
    }

    fun hasPin(context: Context) = getPrefs(context).contains(KEY_PIN_HASH)
    fun removePin(context: Context) { getPrefs(context).edit().remove(KEY_PIN_HASH).apply() }
    fun getWrongAttempts(context: Context) = getPrefs(context).getInt(KEY_WRONG_ATTEMPTS, 0)
    fun getRemainingAttempts(context: Context) = (MAX_ATTEMPTS - getWrongAttempts(context)).coerceAtLeast(0)

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(digest.digest(pin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    // ── VOICE PASSPHRASE ────────────────────────────────────────

    fun setVoicePassphrase(context: Context, phrase: String) {
        getPrefs(context).edit()
            .putString(KEY_VOICE_PHRASE, encrypt(normalise(phrase)))
            .apply()
    }

    fun verifyVoicePassphrase(context: Context, spoken: String): Boolean {
        val stored = getPrefs(context).getString(KEY_VOICE_PHRASE, null) ?: return false
        val storedPhrase = decrypt(stored)
        val spokenNorm = normalise(spoken)

        if (spokenNorm == storedPhrase) return true
        if (spokenNorm.contains(storedPhrase)) return true

        val storedWords = storedPhrase.split(" ").filter { it.isNotEmpty() }
        val spokenWords  = spokenNorm.split(" ").filter { it.isNotEmpty() }
        if (storedWords.isEmpty()) return false

        val matchCount = storedWords.count { sw -> spokenWords.any { it.contains(sw) || sw.contains(it) } }
        return matchCount.toFloat() / storedWords.size >= 0.8f
    }

    fun hasVoicePassphrase(context: Context) = getPrefs(context).contains(KEY_VOICE_PHRASE)

    private fun normalise(phrase: String) =
        phrase.lowercase().trim().replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ")

    // ── APP LOCK (global) ────────────────────────────────────────

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_APP_LOCK_ON, enabled).apply()
        Log.d(TAG, "Global App Lock set to: $enabled")
    }

    fun isAppLockEnabled(context: Context): Boolean {
        val masterOn = getPrefs(context).getBoolean(KEY_APP_LOCK_ON, false)
        val patternOn = PatternManager.isPatternLockEnabled(context)
        return masterOn || patternOn
    }

    // ── BIOMETRIC ────────────────────────────────────────────────

    fun isBiometricEnabled(context: Context): Boolean {
        val enabled = getPrefs(context).getBoolean(KEY_BIOMETRIC_ON, false)
        Log.d(TAG, "isBiometricEnabled: $enabled")
        return enabled
    }
    
    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ON, enabled).apply()
        Log.d(TAG, "setBiometricEnabled: $enabled")
    }

    // ── DEVICE LOCK (System Lock) ────────────────────────────────

    fun isDeviceLockEnabled(context: Context) = getPrefs(context).getBoolean(KEY_DEVICE_LOCK_ON, false)
    fun setDeviceLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEVICE_LOCK_ON, enabled).apply()
    }

    // ── PER-PACKAGE APP LOCK ─────────────────────────────────────

    /**
     * ✅ FIXED: Checks if any lock method is active + package is in list
     */
    fun isPackageLocked(context: Context, packageName: String): Boolean {
        // Master check: If global app lock is off AND pattern is off, nothing is locked
        if (!isAppLockEnabled(context) && !isBiometricEnabled(context) && !isDeviceLockEnabled(context)) {
            return false
        }

        // --- ADDED SYSTEM APP PROTECTION ---
        val systemApps = listOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.myra.assistant" // Don't lock yourself
        )
        if (systemApps.contains(packageName)) return false // Settings should NOT be locked for now to avoid loops

        // Locked packages list check karo
        val locked = getLockedPackages(context)
        val isLocked = locked.contains(packageName)
        Log.d(TAG, "Checking lock for $packageName: $isLocked")
        return isLocked
    }

    fun addLockedPackage(context: Context, packageName: String) {
        val current = getLockedPackages(context).toMutableSet()
        current.add(packageName)
        saveLockedPackages(context, current)
    }

    fun removeLockedPackage(context: Context, packageName: String) {
        val current = getLockedPackages(context).toMutableSet()
        current.remove(packageName)
        saveLockedPackages(context, current)
    }

    fun getLockedPackages(context: Context): Set<String> {
        val stored = getPrefs(context).getString(KEY_LOCKED_PACKAGES, "") ?: ""
        return if (stored.isEmpty()) emptySet()
               else stored.split(",").filter { it.isNotEmpty() }.toSet()
    }

    private fun saveLockedPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit()
            .putString(KEY_LOCKED_PACKAGES, packages.joinToString(","))
            .apply()
    }

    // ── PRIVATE MODE ─────────────────────────────────────────────

    fun enablePrivateMode(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_PRIVATE_MODE, true).apply()
    }

    fun disablePrivateMode(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_PRIVATE_MODE, false).apply()
    }

    fun isPrivateModeActive(context: Context) = getPrefs(context).getBoolean(KEY_PRIVATE_MODE, false)

    // ── RESULT ───────────────────────────────────────────────────

    sealed class PinResult {
        object CORRECT    : PinResult()
        object NOT_SET    : PinResult()
        data class WRONG(val attempts: Int, val lockedOut: Boolean) : PinResult()
        data class LOCKED_OUT(val secondsRemaining: Long) : PinResult()
    }

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
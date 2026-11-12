package com.myra.assistant.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.myra.assistant.services.ForegroundVoiceService

/** Answer / end / speakerphone for phone calls (official TelecomManager API, accessibility tap as fallback). */
object CallControl {

    private fun has(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun isRinging(ctx: Context): Boolean = try {
        if (!has(ctx, Manifest.permission.READ_PHONE_STATE)) false
        else (ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).callState == TelephonyManager.CALL_STATE_RINGING
    } catch (_: Exception) { false }

    fun answer(ctx: Context): String {
        if (has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) {
            try {
                (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).acceptRingingCall()
                return "success: the call was answered"
            } catch (_: Exception) { }
        }
        if (UiAutomation.available()) {
            val root = UiAutomation.root()
            for (label in listOf("Answer", "Accept", "Accept call")) {
                val n = root?.let { UiAutomation.find(it, label) } ?: continue
                if (UiAutomation.clickNode(n)) return "success: tapped '$label' on the incoming call screen"
            }
        }
        return "failed: could not answer. Needs the 'Phone calls' (answer) permission for MYRA, or Accessibility ON."
    }

    fun end(ctx: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) {
            try {
                if ((ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall()) return "success: call ended"
            } catch (_: Exception) { }
        }
        if (UiAutomation.available()) {
            val root = UiAutomation.root()
            for (label in listOf("End call", "Decline", "Reject", "End")) {
                val n = root?.let { UiAutomation.find(it, label) } ?: continue
                if (UiAutomation.clickNode(n)) return "success: tapped '$label'"
            }
        }
        return "failed: could not end the call"
    }

    fun setSpeaker(ctx: Context, on: Boolean): Boolean = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                val dev = am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                dev != null && am.setCommunicationDevice(dev)
            } else { am.clearCommunicationDevice(); true }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = on
            true
        }
    } catch (_: Exception) { false }

    /**
     * "Phone pe baat karo": after the call is answered MYRA switches to speaker and talks to the caller through the
     * running voice session. (Whether MYRA can also HEAR the caller depends on the phone: many Android versions block
     * microphone access during calls.)
     */
    fun beginCallConversation(ctx: Context, caller: String) {
        val app = ctx.applicationContext
        if (ForegroundVoiceService.instance == null) {
            try { ContextCompat.startForegroundService(app, Intent(app, ForegroundVoiceService::class.java)) } catch (_: Exception) { }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            setSpeaker(app, true)
            ForegroundVoiceService.instance?.startCallConversation(caller)
        }, 1500)
    }
}

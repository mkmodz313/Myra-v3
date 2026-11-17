package com.myra.assistant.utils

import android.app.Notification
import android.app.Service
import android.os.Build

/**
 * Starts a foreground service without ever crashing the app.
 * Android 14+ kills the app if a microphone / phone-call service is started before its permission is granted
 * (this happened on the very first launch while the permission dialogs were open).
 * If the real type is not allowed yet, the service goes foreground with the harmless "specialUse" type and stops itself.
 */
object FgsSafe {
    private const val TYPE_SPECIAL_USE = 0x40000000

    /** @return true when the service really runs in its normal mode. */
    fun start(s: Service, id: Int, n: Notification, type: Int, allowed: Boolean): Boolean {
        if (allowed) {
            try {
                if (Build.VERSION.SDK_INT >= 29) s.startForeground(id, n, type) else s.startForeground(id, n)
                return true
            } catch (e: Throwable) {
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) s.startForeground(id, n, TYPE_SPECIAL_USE) else s.startForeground(id, n)
        } catch (e: Throwable) {
        }
        try {
            s.stopSelf()
        } catch (e: Throwable) {
        }
        return false
    }
}

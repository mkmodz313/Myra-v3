package com.myra.assistant.security

import android.content.Context
import android.view.accessibility.AccessibilityEvent

/**
 * Enforces the "locked apps" list. Called by the accessibility service on every window change:
 * when a locked app comes to the front the lock screen (PIN / pattern / voice / fingerprint) is shown on top of it.
 * After a correct unlock that app stays open; when the user leaves it, it is locked again.
 */
object AppLockGuard {
    private var unlockedPkg: String? = null
    private var lastShownAt = 0L

    private val IGNORE = setOf("com.android.systemui", "com.myra.assistant")

    fun unlocked(pkg: String) {
        unlockedPkg = pkg
    }

    fun cancelled() {
        lastShownAt = 0L
    }

    /** Screen was switched off -> everything locks again. */
    fun reset() {
        unlockedPkg = null
    }

    fun onEvent(context: Context, event: AccessibilityEvent, imePackages: Set<String>) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (IGNORE.contains(pkg) || imePackages.contains(pkg)) return
        if (pkg == unlockedPkg) return
        // the user went to another app / the launcher: the earlier unlock is over
        unlockedPkg = null
        if (!SecurityManager.isPackageLocked(context, pkg)) return
        val now = System.currentTimeMillis()
        if (now - lastShownAt < 700) return
        lastShownAt = now
        try {
            AppLockActivity.launchFor(context, pkg)
        } catch (e: Throwable) {
        }
    }
}

package com.myra.assistant.service

import android.app.Notification
import android.content.Context
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.myra.assistant.utils.Announcer
import com.myra.assistant.utils.Constants

/**
 * Announces "you got a message from X" when a chat/SMS notification arrives. Only the sender and app name are spoken,
 * never the message text. Needs the user to allow Notification access for MYRA (Settings -> Announcements).
 */
class MessageNotificationListener : NotificationListenerService() {

    private val recent = HashMap<String, Long>()

    private val messagingApps = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.facebook.orca", "com.instagram.android", "com.snapchat.android", "com.viber.voip", "org.signal.messenger"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val sp = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            if (!sp.getBoolean("message_announce", true)) return
            val pkg = sbn.packageName
            if (pkg == packageName) return
            val n = sbn.notification ?: return
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0 || sbn.isOngoing) return
            if (n.category != Notification.CATEGORY_MESSAGE && pkg !in messagingApps) return

            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            if (title.isEmpty() || title.equals("WhatsApp", true) || title.contains("new messages", true) ||
                title.contains("messages from", true) || title.contains("checking for new", true)
            ) return

            val key = "$pkg|$title"
            val now = System.currentTimeMillis()
            if (now - (recent[key] ?: 0L) < 6000) return
            recent[key] = now

            // never talk over a phone call / ringtone
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.mode != AudioManager.MODE_NORMAL) return

            val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { "" }
            val love = sp.getBoolean("girlfriend_mode", false)
            Announcer.speak(
                this,
                (if (love) "जानी, " else "") + "आपको $title का $app मैसेज आया है।",
                (if (love) "Jaani, " else "") + "aapko $title ka $app message aaya hai."
            )
        } catch (_: Exception) { }
    }
}

package com.myra.assistant.utils

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

/** "Jaani, aapka mobile charging par lag gaya" - registered once for the whole app process. */
object PowerWatcher {
    private var registered = false
    private var last = 0L

    @Synchronized
    fun register(app: Application) {
        if (registered) return
        registered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_POWER_CONNECTED) announce(c, false)
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun announce(context: Context, force: Boolean) {
        try {
            val sp = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            if (!force && !sp.getBoolean("charging_announce", true)) return
            val now = System.currentTimeMillis()
            if (!force && now - last < 4000) return
            last = now
            val pct = try {
                (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (_: Exception) { -1 }
            val love = sp.getBoolean("girlfriend_mode", false)
            val hi = (if (love) "जानी, " else "") + "आपका मोबाइल चार्जिंग पर लग गया है।" + if (pct in 0..100) " बैटरी $pct प्रतिशत है।" else ""
            val ro = (if (love) "Jaani, " else "") + "aapka mobile charging par lag gaya hai." + if (pct in 0..100) " Battery $pct percent hai." else ""
            Announcer.speak(context, hi, ro)
        } catch (_: Throwable) { }
    }
}

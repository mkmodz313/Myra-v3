package com.myra.assistant

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Process
import com.myra.assistant.data.ChatRepository
import com.myra.assistant.ui.main.SplashActivity
import com.myra.assistant.utils.Constants
import com.myra.assistant.utils.PowerWatcher
import com.myra.assistant.wallpaper.ThumbLoader
import com.myra.assistant.wallpaper.WallpaperItem
import com.myra.assistant.wallpaper.WallpaperRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Process-level setup:
 *  - crash guard: an exception on a background thread is logged and swallowed (the app keeps running); a crash on the
 *    main thread is logged and the app restarts itself instead of showing the "App closed" dialog
 *  - charging announcer is registered once for the whole process
 *  - cached wallpaper thumbnails are loaded into memory so the gallery opens instantly
 */
class MyraApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashGuard()
        try { ChatRepository.init(this) } catch (_: Throwable) { }
        try { PowerWatcher.register(this) } catch (_: Throwable) { }
        try { com.myra.assistant.utils.NetworkState.register(this) } catch (_: Throwable) { }
        Thread {
            try {
                val urls = WallpaperRepository.cached(this).map { it.thumbUrl } + WallpaperItem.SAMPLES.map { it.thumbUrl }
                ThumbLoader.warm(this, urls)
            } catch (_: Throwable) { }
        }.start()
    }

    private fun installCrashGuard() {
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { logCrash(t, e) } catch (_: Throwable) { }
            val isMain = t == Looper.getMainLooper().thread
            if (!isMain) return@setDefaultUncaughtExceptionHandler          // background thread: app keeps running
            try {
                val sp = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val loop = now - sp.getLong("last_crash_at", 0L) < 15_000     // never restart in a crash loop
                sp.edit().putLong("last_crash_at", now).commit()
                if (!loop) {
                    startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    Thread.sleep(250)
                }
            } catch (_: Throwable) { }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun logCrash(t: Thread, e: Throwable) {
        val f = File(filesDir, CRASH_FILE)
        if (f.exists() && f.length() > 60_000) f.delete()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        f.appendText("[$stamp] thread=${t.name}\n${e.stackTraceToString().take(3500)}\n\n")
    }

    companion object {
        const val CRASH_FILE = "crash_log.txt"
    }
}

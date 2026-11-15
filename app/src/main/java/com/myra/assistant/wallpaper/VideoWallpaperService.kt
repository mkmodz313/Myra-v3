package com.myra.assistant.wallpaper

import android.content.SharedPreferences
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.myra.assistant.utils.prefs
import java.io.File

/**
 * Real Android live wallpaper (official WallpaperService API) that loops the video chosen in the MYRA app.
 *
 * Battery: muted, no wake-lock, playback is paused whenever the wallpaper is not visible (screen off, app in
 * front, etc.) and the MediaPlayer is fully released when the surface is destroyed or the engine dies.
 * Every callback is wrapped so a video problem can never crash the app.
 */
class VideoWallpaperService : WallpaperService() {

    companion object {
        const val PREF_PATH = "wp_video_path"
        const val PREF_TITLE = "wp_video_title"
        private const val TAG = "VIDEO_WP"
    }

    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private var player: MediaPlayer? = null
        private var holder: SurfaceHolder? = null
        private var prepared = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            try {
                setTouchEventsEnabled(false)
                applicationContext.prefs().registerOnSharedPreferenceChangeListener(this)
            } catch (e: Throwable) { Log.e(TAG, "onCreate: ${e.message}") }
        }

        override fun onSurfaceCreated(h: SurfaceHolder) {
            super.onSurfaceCreated(h)
            holder = h
            try { if (isVisible) startPlayback() } catch (e: Throwable) { Log.e(TAG, "surfaceCreated: ${e.message}") }
        }

        override fun onSurfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(h, format, width, height)
            holder = h
            try { if (isVisible && player == null) startPlayback() } catch (e: Throwable) { Log.e(TAG, "surfaceChanged: ${e.message}") }
        }

        override fun onSurfaceDestroyed(h: SurfaceHolder) {
            releasePlayer()
            holder = null
            super.onSurfaceDestroyed(h)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            try { if (visible) startPlayback() else pausePlayback() } catch (e: Throwable) { Log.e(TAG, "visibility: ${e.message}") }
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            if (key == PREF_PATH) {      // user picked another wallpaper while this one is active
                try {
                    releasePlayer()
                    if (isVisible) startPlayback()
                } catch (e: Throwable) { Log.e(TAG, "prefChanged: ${e.message}") }
            }
        }

        private fun startPlayback() {
            val h = holder ?: return
            if (!h.surface.isValid) return
            val p = player
            if (p != null) {
                if (prepared) try { if (!p.isPlaying) p.start() } catch (_: Throwable) { releasePlayer(); startPlayback() }
                return
            }
            val path = applicationContext.prefs().getString(PREF_PATH, null) ?: return
            if (!File(path).exists()) return
            try {
                val mp = MediaPlayer()
                player = mp
                mp.setSurface(h.surface)
                mp.setDataSource(path)
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.setOnPreparedListener { m ->
                    try {
                        prepared = true
                        if (isVisible) m.start()
                    } catch (e: Throwable) { Log.e(TAG, "start: ${e.message}"); releasePlayer() }
                }
                mp.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error $what/$extra")
                    releasePlayer()
                    true
                }
                mp.prepareAsync()
            } catch (e: Throwable) {
                Log.e(TAG, "Cannot start video: ${e.message}")
                releasePlayer()
            }
        }

        private fun pausePlayback() {
            try { if (prepared) player?.pause() } catch (_: Throwable) { }
        }

        private fun releasePlayer() {
            prepared = false
            try { player?.setOnErrorListener(null); player?.release() } catch (_: Throwable) { }
            player = null
        }

        override fun onDestroy() {
            try { applicationContext.prefs().unregisterOnSharedPreferenceChangeListener(this) } catch (_: Throwable) { }
            releasePlayer()
            super.onDestroy()
        }
    }
}

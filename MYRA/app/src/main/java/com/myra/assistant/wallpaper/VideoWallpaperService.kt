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
            setTouchEventsEnabled(false)
            applicationContext.prefs().registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSurfaceCreated(h: SurfaceHolder) {
            super.onSurfaceCreated(h)
            holder = h
            if (isVisible) startPlayback()
        }

        override fun onSurfaceDestroyed(h: SurfaceHolder) {
            releasePlayer()
            holder = null
            super.onSurfaceDestroyed(h)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) startPlayback() else pausePlayback()
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            if (key == PREF_PATH) {      // user picked another wallpaper while this one is active
                releasePlayer()
                if (isVisible) startPlayback()
            }
        }

        private fun startPlayback() {
            val h = holder ?: return
            val p = player
            if (p != null) {
                if (prepared && !p.isPlaying) try { p.start() } catch (_: Exception) { releasePlayer(); startPlayback() }
                return
            }
            val path = applicationContext.prefs().getString(PREF_PATH, null) ?: return
            if (!File(path).exists()) return
            try {
                player = MediaPlayer().apply {
                    setSurface(h.surface)
                    setDataSource(path)
                    isLooping = true
                    setVolume(0f, 0f)
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    setOnPreparedListener { mp ->
                        prepared = true
                        if (isVisible) mp.start()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error $what/$extra")
                        releasePlayer()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot start video: ${e.message}")
                releasePlayer()
            }
        }

        private fun pausePlayback() {
            try { if (prepared) player?.pause() } catch (_: Exception) { }
        }

        private fun releasePlayer() {
            prepared = false
            try { player?.release() } catch (_: Exception) { }
            player = null
        }

        override fun onDestroy() {
            applicationContext.prefs().unregisterOnSharedPreferenceChangeListener(this)
            releasePlayer()
            super.onDestroy()
        }
    }
}

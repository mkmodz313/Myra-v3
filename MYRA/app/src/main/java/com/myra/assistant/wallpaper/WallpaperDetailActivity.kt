package com.myra.assistant.wallpaper

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.myra.assistant.R
import com.myra.assistant.utils.prefs
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Wallpaper details + real video preview + the official Android "set live wallpaper" flow. */
class WallpaperDetailActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var item: WallpaperItem
    private lateinit var surface: SurfaceView
    private lateinit var thumb: ImageView
    private lateinit var loading: ProgressBar
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var previewBtn: Button
    private lateinit var setBtn: Button

    private var player: MediaPlayer? = null
    private var surfaceReady = false
    private var previewing = false
    private var viewCounted = false
    private var busy = false
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_wallpaper_detail)

        item = try {
            WallpaperItem.fromJson(JSONObject(intent.getStringExtra("item") ?: "")) 
        } catch (e: Exception) { finish(); return }

        surface = findViewById(R.id.wdSurface)
        thumb = findViewById(R.id.wdThumb)
        loading = findViewById(R.id.wdLoading)
        progress = findViewById(R.id.wdProgress)
        status = findViewById(R.id.wdStatus)
        previewBtn = findViewById(R.id.wdPreview)
        setBtn = findViewById(R.id.wdSet)

        findViewById<TextView>(R.id.wdTitle).text = item.title
        findViewById<TextView>(R.id.wdDesc).text = item.description
        val sizeText = if (item.sizeBytes > 0) " · %.1f MB".format(item.sizeBytes / 1048576.0) else ""
        findViewById<TextView>(R.id.wdMeta).text =
            "${item.category}$sizeText" + if (!item.isBundled) " · ${item.downloads} sets · ${item.views} views" else " · Sample"
        ThumbLoader.load(this, item.thumbUrl, thumb)

        val back = findViewById<ImageButton>(R.id.wdBack)
        val panel = findViewById<View>(R.id.wdPanel)
        val backTop = (back.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.wdRoot)) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (back.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin = backTop + sys.top
            back.requestLayout()
            panel.setPadding(panel.paddingLeft, panel.paddingTop, panel.paddingRight, 46 + sys.bottom)
            insets
        }

        back.setOnClickListener { finish() }
        com.myra.assistant.ui.NetworkBanner.attach(this)
        surface.holder.addCallback(this)
        previewBtn.setOnClickListener { togglePreview() }
        setBtn.setOnClickListener { setWallpaper() }
    }

    // ── preview ─────────────────────────────────────────────────────────────────────────────────
    private fun togglePreview() {
        if (previewing) stopPreview() else startPreview()
    }

    private fun startPreview() {
        if (!surfaceReady) return
        releasePlayer()
        loading.visibility = View.VISIBLE
        previewBtn.text = "Stop preview"
        previewing = true
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setSurface(surface.holder.surface)
            val local = WallpaperRepository.localFile(this, item)
            when {
                local.exists() -> mp.setDataSource(local.absolutePath)
                item.isBundled -> assets.openFd(item.videoUrl.removePrefix("asset:")).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                else -> mp.setDataSource(item.videoUrl)        // streams; nothing is stored
            }
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            mp.setOnPreparedListener {
                loading.visibility = View.GONE
                thumb.visibility = View.INVISIBLE
                it.start()
                if (!viewCounted) {
                    viewCounted = true
                    io.execute { WallpaperRepository.bump(item, "views") }
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                stopPreview()
                showStatus("Preview play nahi ho saka. Internet check karein.")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stopPreview()
            showStatus("Preview error: ${e.message}")
        }
    }

    private fun stopPreview() {
        previewing = false
        releasePlayer()
        loading.visibility = View.GONE
        thumb.visibility = View.VISIBLE
        previewBtn.text = "Preview"
    }

    private fun releasePlayer() {
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    // ── set wallpaper (real Android flow) ───────────────────────────────────────────────────────
    private fun setWallpaper() {
        if (busy) return
        busy = true
        setBtn.isEnabled = false
        progress.progress = 0
        progress.visibility = View.VISIBLE
        showStatus(if (item.isBundled) "Wallpaper tayyar ho raha hai..." else "Wallpaper download ho raha hai...")

        io.execute {
            try {
                val wasLocal = WallpaperRepository.localFile(this, item).exists()
                val file: File = WallpaperRepository.ensureVideo(this, item) { p -> ui.post { progress.progress = p } }
                if (!wasLocal) WallpaperRepository.bump(item, "downloads")
                ui.post {
                    prefs().edit()
                        .putString(VideoWallpaperService.PREF_PATH, file.absolutePath)
                        .putString(VideoWallpaperService.PREF_TITLE, item.title)
                        .apply()
                    finishBusy()
                    stopPreview()
                    launchSystemFlow()
                }
            } catch (e: Exception) {
                ui.post {
                    finishBusy()
                    showStatus("Wallpaper set nahi ho paaya: ${e.message ?: "internet check karein"}")
                }
            }
        }
    }

    private fun finishBusy() {
        busy = false
        setBtn.isEnabled = true
        progress.visibility = View.GONE
    }

    /**
     * Opens Android's own live-wallpaper preview. There the user chooses Home screen, Lock screen or Both
     * (where the device/OS supports it) - that choice screen belongs to the system, not to MYRA.
     */
    private fun launchSystemFlow() {
        val component = ComponentName(this, VideoWallpaperService::class.java)
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            )
            showStatus("Android wallpaper preview khul gaya - wahan 'Set wallpaper' dabayein aur Home / Lock / Both chunein.")
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                showStatus("List mein 'MYRA Live Wallpaper' chuniye.")
            } catch (e2: Exception) {
                showStatus("Is phone par live wallpaper picker available nahi hai.")
            }
        }
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    // ── surface / lifecycle ─────────────────────────────────────────────────────────────────────
    override fun surfaceCreated(h: SurfaceHolder) { surfaceReady = true }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) {
        surfaceReady = false
        if (previewing) stopPreview()
    }

    override fun onPause() {
        if (previewing) stopPreview()      // nothing keeps playing in the background
        super.onPause()
    }

    override fun onDestroy() {
        releasePlayer()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

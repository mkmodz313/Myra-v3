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
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
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
class WallpaperDetailActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    private lateinit var item: WallpaperItem
    private lateinit var surface: TextureView
    private var previewSurface: Surface? = null
    private var vw = 0
    private var vh = 0
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
        try { ThumbLoader.load(this, item.thumbUrl, thumb) } catch (_: Throwable) { }

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
        try { com.myra.assistant.ui.NetworkBanner.attach(this) } catch (_: Throwable) { }
        surface.surfaceTextureListener = this
        if (surface.isAvailable) surfaceReady = true
        previewBtn.setOnClickListener { togglePreview() }
        setBtn.setOnClickListener { setWallpaper() }
    }

    // ── preview ─────────────────────────────────────────────────────────────────────────────────
    private fun togglePreview() {
        if (previewing) stopPreview() else startPreview()
    }

    private fun startPreview() {
        val st = surface.surfaceTexture
        if (!surfaceReady || st == null) { showStatus("Preview tayyar ho raha hai, ek second baad dobara dabayein."); return }
        releasePlayer()
        loading.visibility = View.VISIBLE
        previewBtn.text = "Stop preview"
        previewing = true
        try {
            val mp = MediaPlayer()
            player = mp
            val sf = Surface(st)
            previewSurface = sf
            mp.setSurface(sf)
            val local = WallpaperRepository.localFile(this, item)
            when {
                local.exists() && local.length() > 0 -> mp.setDataSource(local.absolutePath)
                item.isBundled -> assets.openFd(item.videoUrl.removePrefix("asset:")).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                else -> mp.setDataSource(item.videoUrl)        // streams; nothing is stored
            }
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h -> vw = w; vh = h; applyCrop() }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) thumb.visibility = View.INVISIBLE
                false
            }
            mp.setOnPreparedListener {
                try {
                    loading.visibility = View.GONE
                    it.start()
                    ui.postDelayed({ if (previewing) thumb.visibility = View.INVISIBLE }, 700)
                    if (!viewCounted) {
                        viewCounted = true
                        io.execute { WallpaperRepository.bump(item, "views") }
                    }
                } catch (e: Throwable) {
                    stopPreview()
                    showStatus("Preview error: ${e.message}")
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                ui.post {
                    stopPreview()
                    showStatus("Preview play nahi ho saka. Internet check karein.")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Throwable) {
            stopPreview()
            showStatus("Preview error: ${e.message}")
        }
    }

    /** TextureView stretches the video; this scales it back so it fills the screen without distortion (centre-crop). */
    private fun applyCrop() {
        try {
            val w = surface.width.toFloat(); val h = surface.height.toFloat()
            if (vw <= 0 || vh <= 0 || w <= 0f || h <= 0f) return
            val s = maxOf(w / vw, h / vh)
            val m = Matrix()
            m.setScale(s * vw / w, s * vh / h, w / 2f, h / 2f)
            surface.setTransform(m)
        } catch (_: Throwable) { }
    }

    private fun stopPreview() {
        previewing = false
        releasePlayer()
        loading.visibility = View.GONE
        thumb.visibility = View.VISIBLE
        previewBtn.text = "Preview"
    }

    private fun releasePlayer() {
        try { player?.setOnErrorListener(null); player?.release() } catch (_: Throwable) {}
        player = null
        try { previewSurface?.release() } catch (_: Throwable) {}
        previewSurface = null
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
                    try {
                        prefs().edit()
                            .putString(VideoWallpaperService.PREF_PATH, file.absolutePath)
                            .putString(VideoWallpaperService.PREF_TITLE, item.title)
                            .commit()
                        finishBusy()
                        stopPreview()
                        launchSystemFlow()
                    } catch (e: Throwable) {
                        finishBusy()
                        showStatus("Wallpaper set nahi ho paaya: ${e.message}")
                    }
                }
            } catch (e: Throwable) {
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
        val tries = listOf(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component) to
                "Android wallpaper preview khul gaya - wahan 'Set wallpaper' dabayein aur Home / Lock / Both chunein.",
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER) to "List mein 'MYRA Live Wallpaper' chuniye.",
            Intent(Intent.ACTION_SET_WALLPAPER) to "List mein 'Live wallpapers' khol kar 'MYRA Live Wallpaper' chuniye."
        )
        for ((intent, msg) in tries) {
            try {
                startActivity(intent)
                showStatus(msg)
                return
            } catch (_: Throwable) { /* this phone does not support this screen - try the next one */ }
        }
        showStatus("Is phone par live wallpaper picker available nahi hai.")
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    // ── surface / lifecycle ─────────────────────────────────────────────────────────────────────
    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { surfaceReady = true }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { applyCrop() }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        surfaceReady = false
        if (previewing) stopPreview()
        return true
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

package com.myra.assistant.admin

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.myra.assistant.R
import com.myra.assistant.wallpaper.ThumbLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executors

/** Admin dashboard: upload, edit, publish / unpublish, delete wallpapers + live statistics. */
class AdminPanelActivity : AppCompatActivity() {

    private val categories = listOf("Neon", "Nature", "Abstract", "Anime", "Romantic", "Cars", "Space", "General")
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var videoUri: Uri? = null
    private var thumbUri: Uri? = null
    private var busy = false

    private lateinit var fTitle: EditText
    private lateinit var fDesc: EditText
    private lateinit var fCategory: Spinner
    private lateinit var fPublish: CheckBox
    private lateinit var videoInfo: TextView
    private lateinit var thumbInfo: TextView
    private lateinit var barVideo: ProgressBar
    private lateinit var barThumb: ProgressBar
    private lateinit var upStatus: TextView
    private lateinit var btnUpload: Button
    private lateinit var listBox: LinearLayout
    private lateinit var listStatus: TextView

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        videoUri = uri
        val i = info(uri)
        videoInfo.text = "✅ ${i.first} (${"%.1f".format(i.second / 1048576.0)} MB)"
    }
    private val pickThumb = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        thumbUri = uri
        thumbInfo.text = "✅ ${info(uri).first}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminApi.isSignedIn()) {
            startActivity(Intent(this, AdminLoginActivity::class.java))
            finish()
            return
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_admin_panel)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.adminRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }

        fTitle = findViewById(R.id.fTitle)
        fDesc = findViewById(R.id.fDesc)
        fCategory = findViewById(R.id.fCategory)
        fPublish = findViewById(R.id.fPublish)
        videoInfo = findViewById(R.id.videoInfo)
        thumbInfo = findViewById(R.id.thumbInfo)
        barVideo = findViewById(R.id.barVideo)
        barThumb = findViewById(R.id.barThumb)
        upStatus = findViewById(R.id.upStatus)
        btnUpload = findViewById(R.id.btnUpload)
        listBox = findViewById(R.id.wpList)
        listStatus = findViewById(R.id.listStatus)

        com.myra.assistant.ui.NetworkBanner.attach(this)
        fCategory.adapter = darkAdapter(categories)

        findViewById<Button>(R.id.adminLogout).setOnClickListener {
            AdminApi.signOut()
            finish()
        }
        findViewById<Button>(R.id.btnPickVideo).setOnClickListener { pickVideo.launch("video/*") }
        findViewById<Button>(R.id.btnPickThumb).setOnClickListener { pickThumb.launch("image/*") }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener { reload() }
        btnUpload.setOnClickListener { startUpload() }

        reload()
    }

    private fun darkAdapter(items: List<String>) = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getView(position, convertView, parent).also { (it as TextView).setTextColor(Color.WHITE) }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getDropDownView(position, convertView, parent).also {
                (it as TextView).setTextColor(Color.WHITE)
                it.setBackgroundColor(Color.parseColor("#2A0838"))
                it.setPadding(40, 28, 40, 28)
            }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** name, size, mime */
    private fun info(uri: Uri): Triple<String, Long, String?> {
        var name = "file"
        var size = 0L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n) ?: name
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        } catch (_: Exception) { }
        if (size <= 0) {
            try { contentResolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length } } catch (_: Exception) { }
        }
        return Triple(name, size, contentResolver.getType(uri))
    }

    // ── list / stats ─────────────────────────────────────────────────────────────────────────────
    private fun reload() {
        listStatus.visibility = View.VISIBLE
        listStatus.text = "Load ho raha hai..."
        io.execute {
            try {
                val items = AdminApi.list()
                ui.post { if (!isDestroyed) render(items) }
            } catch (e: Exception) {
                ui.post { if (!isDestroyed) { listStatus.text = "⚠️ ${e.message}"; } }
            }
        }
    }

    private fun render(items: List<AdminWallpaper>) {
        findViewById<TextView>(R.id.stTotal).text = items.size.toString()
        findViewById<TextView>(R.id.stLive).text = items.count { it.published }.toString()
        findViewById<TextView>(R.id.stSets).text = items.sumOf { it.downloads }.toString()
        findViewById<TextView>(R.id.stViews).text = items.sumOf { it.views }.toString()
        listBox.removeAllViews()
        listStatus.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (items.isEmpty()) listStatus.text = "Abhi koi wallpaper nahi. Upar se upload karein."
        items.forEach { listBox.addView(row(it)) }
    }

    private fun row(w: AdminWallpaper): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#33000000"))
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(106))
        }
        ThumbLoader.load(this, w.thumbUrl, img)
        top.addView(img)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(12) }
        }
        col.addView(TextView(this).apply { text = w.title.ifEmpty { "(untitled)" }; setTextColor(Color.WHITE); textSize = 16f })
        col.addView(TextView(this).apply {
            text = if (w.published) "● PUBLISHED" else "○ DRAFT (users ko nahi dikhta)"
            setTextColor(Color.parseColor(if (w.published) "#FF5CFFA0" else "#FFFFB8F2")); textSize = 12f
        })
        col.addView(TextView(this).apply {
            text = "${w.category} · ${"%.1f".format(w.sizeBytes / 1048576.0)} MB · ${w.downloads} sets · ${w.views} views"
            setTextColor(Color.parseColor("#FFF3D9F5")); textSize = 12f
        })
        top.addView(col)
        root.addView(top)

        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) }
        }
        fun small(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_btn_3d); stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).also { it.marginEnd = dp(6) }
            setOnClickListener { onClick() }
        }
        btns.addView(small(if (w.published) "Unpublish" else "Publish") {
            runTask("Update ho raha hai...") { AdminApi.update(w.id, published = !w.published) }
        })
        btns.addView(small("Edit") { editDialog(w) })
        btns.addView(small("Delete") { deleteDialog(w) })
        root.addView(btns)

        val line = View(this).apply {
            setBackgroundColor(Color.parseColor("#44FF8CEB"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).also { it.topMargin = dp(12) }
        }
        root.addView(line)
        return root
    }

    private fun runTask(msg: String, block: () -> Unit) {
        listStatus.visibility = View.VISIBLE
        listStatus.text = msg
        io.execute {
            try { block(); ui.post { reload() } }
            catch (e: Exception) { ui.post { listStatus.text = "⚠️ ${e.message}" } }
        }
    }

    private fun editDialog(w: AdminWallpaper) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), 0) }
        val t = EditText(this).apply { setText(w.title); hint = "Title" }
        val d = EditText(this).apply { setText(w.description); hint = "Description" }
        val cats = if (w.category in categories) categories else categories + w.category
        val c = Spinner(this).apply { adapter = ArrayAdapter(this@AdminPanelActivity, android.R.layout.simple_spinner_dropdown_item, cats); setSelection(cats.indexOf(w.category)) }
        box.addView(t); box.addView(d); box.addView(c)
        AlertDialog.Builder(this).setTitle("Edit wallpaper").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val title = t.text.toString().trim()
                if (title.isEmpty()) { Toast.makeText(this, "Title zaroori hai", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                runTask("Save ho raha hai...") { AdminApi.update(w.id, title = title, description = d.text.toString().trim(), category = c.selectedItem as String) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteDialog(w: AdminWallpaper) {
        AlertDialog.Builder(this).setTitle("Delete?")
            .setMessage("\"${w.title}\" delete hoga (video aur thumbnail bhi). Ye wapas nahi aayega.")
            .setPositiveButton("Delete") { _, _ ->
                runTask("Delete ho raha hai...") {
                    AdminApi.deleteDoc(w.id)
                    AdminApi.deleteFile(w.videoPath)
                    AdminApi.deleteFile(w.thumbPath)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ── upload ───────────────────────────────────────────────────────────────────────────────────
    private fun status(text: String, error: Boolean = false) {
        upStatus.visibility = View.VISIBLE
        upStatus.text = text
        upStatus.setTextColor(Color.parseColor(if (error) "#FFFF8FA8" else "#FFFFD6F8"))
    }

    private fun startUpload() {
        if (busy) return
        val title = fTitle.text.toString().trim()
        val v = videoUri
        if (title.isEmpty()) { status("Title likhein.", true); return }
        if (v == null) { status("Pehle video chunein.", true); return }
        val vInfo = info(v)
        if (vInfo.second > 50L * 1024 * 1024) { status("Supabase free plan mein video 50 MB se choti honi chahiye. Video compress karein (720p).", true); return }
        val desc = fDesc.text.toString().trim()
        val cat = fCategory.selectedItem as String
        val publish = fPublish.isChecked
        val tUri = thumbUri

        busy = true
        btnUpload.isEnabled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        barVideo.progress = 0; barThumb.progress = 0
        barVideo.visibility = View.VISIBLE; barThumb.visibility = View.VISIBLE
        status("Video upload ho rahi hai... (app khula rakhein)")

        io.execute {
            try {
                val id = UUID.randomUUID().toString().replace("-", "").take(20)
                val stamp = System.currentTimeMillis()
                val vPath = "wallpapers/$id/video_$stamp.mp4"
                val vUrl = AdminApi.upload(vPath, vInfo.third?.takeIf { it.startsWith("video/") } ?: "video/mp4", vInfo.second,
                    { contentResolver.openInputStream(v) ?: throw AdminApi.ApiError("Video file khul nahi saki") }
                ) { p -> ui.post { barVideo.progress = p } }

                ui.post { status("Thumbnail upload ho raha hai...") }
                val tPath = "wallpapers/$id/thumb_$stamp.jpg"
                val tUrl = if (tUri != null) {
                    val ti = info(tUri)
                    AdminApi.upload(tPath, ti.third?.takeIf { it.startsWith("image/") } ?: "image/jpeg", ti.second,
                        { contentResolver.openInputStream(tUri) ?: throw AdminApi.ApiError("Thumbnail khul nahi saka") }
                    ) { p -> ui.post { barThumb.progress = p } }
                } else {
                    val bytes = frameFromVideo(v) ?: throw AdminApi.ApiError("Video se thumbnail nahi ban saka. Thumbnail khud chunein.")
                    AdminApi.upload(tPath, "image/jpeg", bytes.size.toLong(), { ByteArrayInputStream(bytes) }) { p -> ui.post { barThumb.progress = p } }
                }

                ui.post { status("Save ho raha hai...") }
                AdminApi.create(AdminWallpaper(id, title, desc, cat, vUrl, tUrl, vPath, tPath, vInfo.second, stamp, 0, 0, publish))
                ui.post {
                    status(if (publish) "✅ Upload ho gaya aur publish ho gaya! Users ko 1 minute ke andar dikhega." else "✅ Draft ke taur par save ho gaya.")
                    resetForm()
                    reload()
                }
            } catch (e: Exception) {
                ui.post { status("❌ ${e.message ?: "Upload failed"}", true) }
            } finally {
                ui.post {
                    busy = false
                    btnUpload.isEnabled = true
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    private fun frameFromVideo(uri: Uri): ByteArray? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(this, uri)
            val frame: Bitmap = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0) ?: return null
            val scaled = if (frame.width > 540) Bitmap.createScaledBitmap(frame, 540, frame.height * 540 / frame.width, true) else frame
            ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) { }
        }
    }

    private fun resetForm() {
        fTitle.setText(""); fDesc.setText("")
        videoUri = null; thumbUri = null
        videoInfo.text = "Koi video nahi chuni"
        thumbInfo.text = "Na chunein to video se khud ban jayega"
        barVideo.visibility = View.GONE; barThumb.visibility = View.GONE
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

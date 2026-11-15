package com.myra.assistant.admin

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.myra.assistant.R
import com.myra.assistant.wallpaper.ThumbLoader
import java.util.UUID
import java.util.concurrent.Executors

/**
 * In-app Admin Panel: e-mail + password login (checked by Firebase on Google's servers), then
 * upload / edit / publish / unpublish / delete wallpapers and see statistics.
 */
class AdminActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val categories = arrayOf("Neon", "Nature", "Abstract", "Anime", "Romantic", "Cars", "Space", "General")

    private lateinit var loginGroup: View
    private lateinit var dashGroup: View
    private lateinit var logoutBtn: Button
    private lateinit var email: EditText
    private lateinit var pass: EditText
    private lateinit var loginBtn: Button
    private lateinit var loginMsg: TextView
    private lateinit var fTitle: EditText
    private lateinit var fDesc: EditText
    private lateinit var catBtn: Button
    private lateinit var videoName: TextView
    private lateinit var thumbName: TextView
    private lateinit var publish: CheckBox
    private lateinit var barVideo: ProgressBar
    private lateinit var barThumb: ProgressBar
    private lateinit var status: TextView
    private lateinit var saveBtn: Button
    private lateinit var cancelEdit: Button
    private lateinit var formTitle: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var empty: TextView

    private var videoUri: Uri? = null
    private var thumbUri: Uri? = null
    private var editing: AdminItem? = null
    private var items: List<AdminItem> = emptyList()
    private var category = "Neon"
    private var busy = false

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { videoUri = uri; videoName.text = nameOf(uri) }
    }
    private val pickThumb = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { thumbUri = uri; thumbName.text = nameOf(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // keeps the password / admin screen out of screenshots and the recent-apps preview
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_admin)
        AdminApi.logout()    // every visit asks for e-mail + password

        loginGroup = findViewById(R.id.loginGroup)
        dashGroup = findViewById(R.id.dashGroup)
        logoutBtn = findViewById(R.id.adminLogout)
        email = findViewById(R.id.adminEmail)
        pass = findViewById(R.id.adminPass)
        loginBtn = findViewById(R.id.adminLoginBtn)
        loginMsg = findViewById(R.id.adminLoginMsg)
        fTitle = findViewById(R.id.fTitle)
        fDesc = findViewById(R.id.fDesc)
        catBtn = findViewById(R.id.fCat)
        videoName = findViewById(R.id.fVideoName)
        thumbName = findViewById(R.id.fThumbName)
        publish = findViewById(R.id.fPublish)
        barVideo = findViewById(R.id.barVideo)
        barThumb = findViewById(R.id.barThumb)
        status = findViewById(R.id.adminStatus)
        saveBtn = findViewById(R.id.adminSave)
        cancelEdit = findViewById(R.id.adminCancelEdit)
        formTitle = findViewById(R.id.formTitle)
        listBox = findViewById(R.id.adminList)
        empty = findViewById(R.id.adminEmpty)

        val content = findViewById<View>(R.id.adminContent)
        val top = content.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.adminRoot)) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            content.setPadding(content.paddingLeft, top + sys.top, content.paddingRight, 40 + maxOf(sys.bottom, ime.bottom))
            insets
        }

        findViewById<ImageButton>(R.id.adminBack).setOnClickListener { finish() }
        logoutBtn.setOnClickListener { doLogout() }
        loginBtn.setOnClickListener { doLogin() }
        pass.setOnEditorActionListener { _, _, _ -> doLogin(); true }
        findViewById<Button>(R.id.fPickVideo).setOnClickListener { pickVideo.launch("video/*") }
        findViewById<Button>(R.id.fPickThumb).setOnClickListener { pickThumb.launch("image/*") }
        catBtn.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Category").setItems(categories) { _, i ->
                category = categories[i]; catBtn.text = "Category: $category"
            }.show()
        }
        saveBtn.setOnClickListener { save() }
        cancelEdit.setOnClickListener { resetForm() }
        findViewById<Button>(R.id.adminRefresh).setOnClickListener { refresh() }
        catBtn.text = "Category: $category"
    }

    // ── login ───────────────────────────────────────────────────────────────────────────────────
    private fun doLogin() {
        val e = email.text.toString().trim()
        val p = pass.text.toString()
        if (e.isEmpty() || p.isEmpty()) { loginMsg.text = "Email aur password dono likhein."; return }
        loginBtn.isEnabled = false
        loginMsg.text = "Login ho raha hai..."
        io.execute {
            try {
                AdminApi.login(e, p)
                runOnUiThread {
                    pass.setText("")
                    loginMsg.text = ""
                    loginBtn.isEnabled = true
                    loginGroup.visibility = View.GONE
                    dashGroup.visibility = View.VISIBLE
                    logoutBtn.visibility = View.VISIBLE
                    refresh()
                }
            } catch (ex: Exception) {
                runOnUiThread { loginBtn.isEnabled = true; loginMsg.text = ex.message ?: "Login failed" }
            }
        }
    }

    private fun doLogout() {
        AdminApi.logout()
        dashGroup.visibility = View.GONE
        logoutBtn.visibility = View.GONE
        loginGroup.visibility = View.VISIBLE
        resetForm()
    }

    // ── list ────────────────────────────────────────────────────────────────────────────────────
    private fun refresh() {
        io.execute {
            try {
                val list = AdminApi.list()
                runOnUiThread { items = list; render() }
            } catch (ex: Exception) {
                runOnUiThread { status.text = ex.message ?: "List load nahi hui" }
            }
        }
    }

    private fun render() {
        findViewById<TextView>(R.id.stTotal).text = items.size.toString()
        findViewById<TextView>(R.id.stLive).text = items.count { it.published }.toString()
        findViewById<TextView>(R.id.stSets).text = items.sumOf { it.downloads }.toString()
        findViewById<TextView>(R.id.stViews).text = items.sumOf { it.views }.toString()
        listBox.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val inf = LayoutInflater.from(this)
        for (w in items) {
            val row = inf.inflate(R.layout.item_admin_wallpaper, listBox, false)
            row.findViewById<TextView>(R.id.aiTitle).text = w.title.ifEmpty { "(untitled)" }
            row.findViewById<TextView>(R.id.aiMeta).text =
                "${w.category} · %.1f MB · ${w.downloads} sets · ${w.views} views".format(w.sizeBytes / 1048576.0)
            row.findViewById<TextView>(R.id.aiState).apply {
                text = if (w.published) "● PUBLISHED" else "○ DRAFT"
                setTextColor(if (w.published) Color.parseColor("#FF4DE88A") else Color.parseColor("#FFFFB8F2"))
            }
            ThumbLoader.load(this, w.thumbUrl, row.findViewById<ImageView>(R.id.aiThumb))
            row.findViewById<Button>(R.id.aiToggle).apply {
                text = if (w.published) "Unpublish" else "Publish"
                setOnClickListener { run("Update", w.id) { AdminApi.update(w.id, mapOf("published" to !w.published)) } }
            }
            row.findViewById<Button>(R.id.aiEdit).setOnClickListener { startEdit(w) }
            row.findViewById<Button>(R.id.aiDelete).setOnClickListener {
                AlertDialog.Builder(this).setTitle("Delete?")
                    .setMessage("\"${w.title}\" video aur thumbnail ke saath hamesha ke liye hat jayega.")
                    .setPositiveButton("Delete") { _, _ ->
                        run("Delete", w.id) {
                            AdminApi.deleteDoc(w.id)
                            AdminApi.deleteFile(w.videoPath)
                            AdminApi.deleteFile(w.thumbPath)
                        }
                    }.setNegativeButton("Cancel", null).show()
            }
            listBox.addView(row)
        }
    }

    private fun run(what: String, id: String, block: () -> Unit) {
        status.text = "$what ho raha hai..."
        io.execute {
            try {
                block()
                runOnUiThread { status.text = "$what ✅"; refresh() }
            } catch (ex: Exception) {
                runOnUiThread { status.text = ex.message ?: "$what failed" }
            }
        }
    }

    // ── form ────────────────────────────────────────────────────────────────────────────────────
    private fun startEdit(w: AdminItem) {
        editing = w
        formTitle.text = "✏️ Edit wallpaper"
        fTitle.setText(w.title); fDesc.setText(w.description)
        category = w.category; catBtn.text = "Category: $category"
        publish.isChecked = w.published
        videoUri = null; thumbUri = null
        videoName.text = "Purani video rakhi hai (nayi chunein agar badalni ho)"
        thumbName.text = "Purana thumbnail rakha hai"
        saveBtn.text = "Save changes"
        cancelEdit.visibility = View.VISIBLE
        findViewById<View>(R.id.adminScroll).scrollTo(0, 0)
    }

    private fun resetForm() {
        editing = null; videoUri = null; thumbUri = null
        formTitle.text = "⬆️ Upload new wallpaper"
        fTitle.setText(""); fDesc.setText(""); publish.isChecked = false
        videoName.text = "Koi video nahi chuni"; thumbName.text = "Koi thumbnail nahi chuni"
        barVideo.progress = 0; barThumb.progress = 0
        saveBtn.text = "Upload & save"
        cancelEdit.visibility = View.GONE
    }

    private fun save() {
        if (busy) return
        val title = fTitle.text.toString().trim()
        if (title.isEmpty()) { status.text = "Title zaroori hai."; return }
        val old = editing
        if (old == null && (videoUri == null || thumbUri == null)) { status.text = "Video aur thumbnail dono chunein."; return }
        busy = true
        saveBtn.isEnabled = false
        barVideo.progress = 0; barThumb.progress = 0
        val desc = fDesc.text.toString().trim()
        val cat = category
        val pub = publish.isChecked
        val vUri = videoUri
        val tUri = thumbUri

        io.execute {
            try {
                val id = old?.id ?: UUID.randomUUID().toString().replace("-", "").take(20)
                var videoUrl = old?.videoUrl.orEmpty(); var videoPath = old?.videoPath.orEmpty(); var size = old?.sizeBytes ?: 0L
                var thumbUrl = old?.thumbUrl.orEmpty(); var thumbPath = old?.thumbPath.orEmpty()
                val stamp = System.currentTimeMillis()

                if (vUri != null) {
                    runOnUiThread { status.text = "Video upload ho rahi hai..." }
                    val (u, p) = AdminApi.upload(this, vUri, "wallpapers/$id/video_$stamp.mp4", "video/mp4") { pct -> runOnUiThread { barVideo.progress = pct } }
                    videoUrl = u; videoPath = p
                    size = contentSize(vUri)
                }
                if (tUri != null) {
                    runOnUiThread { status.text = "Thumbnail upload ho raha hai..." }
                    val (u, p) = AdminApi.upload(this, tUri, "wallpapers/$id/thumb_$stamp.jpg", "image/jpeg") { pct -> runOnUiThread { barThumb.progress = pct } }
                    thumbUrl = u; thumbPath = p
                }

                val data = mutableMapOf<String, Any>(
                    "title" to title, "description" to desc, "category" to cat, "published" to pub,
                    "videoUrl" to videoUrl, "videoPath" to videoPath, "thumbUrl" to thumbUrl, "thumbPath" to thumbPath,
                    "sizeBytes" to size
                )
                if (old == null) {
                    data["createdAt"] = stamp; data["downloads"] = 0L; data["views"] = 0L
                    AdminApi.create(id, data)
                } else {
                    AdminApi.update(id, data)
                    if (vUri != null && old.videoPath.isNotEmpty() && old.videoPath != videoPath) AdminApi.deleteFile(old.videoPath)
                    if (tUri != null && old.thumbPath.isNotEmpty() && old.thumbPath != thumbPath) AdminApi.deleteFile(old.thumbPath)
                }
                runOnUiThread {
                    busy = false; saveBtn.isEnabled = true
                    status.text = if (pub) "Saved & published ✅ (users ko 1 minute ke andar dikh jayega)" else "Draft save ho gaya ✅"
                    resetForm(); refresh()
                }
            } catch (ex: Exception) {
                runOnUiThread { busy = false; saveBtn.isEnabled = true; status.text = ex.message ?: "Failed" }
            }
        }
    }

    private fun contentSize(uri: Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst() && i >= 0 && !c.isNull(i)) return c.getLong(i)
        }
        return 0L
    }

    private fun nameOf(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) return c.getString(i)
        }
        return "selected file"
    }

    override fun onDestroy() {
        AdminApi.logout()     // nothing stays logged in once the panel is closed
        super.onDestroy()
    }
}

package com.myra.assistant.security

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.myra.assistant.R
import java.io.File

/**
 * Private Vault: photos, videos, music and any file are MOVED into this app's private storage.
 * Gallery / music player / file manager cannot see them any more; only MYRA (after the lock) can.
 */
class VaultActivity : AppCompatActivity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private lateinit var content: LinearLayout
    private var pendingRestore: File? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dir(): File = File(filesDir, "vault").also { it.mkdirs() }

    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) importAll(uris)
    }
    private val restore = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = pendingRestore
        pendingRestore = null
        if (uri != null && f != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
                f.delete()
                Toast.makeText(this, "Wapas nikal di ✅", Toast.LENGTH_SHORT).show()
                refresh()
            } catch (e: Throwable) {
                Toast.makeText(this, "Restore nahi hui: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this)
        root.setBackgroundColor(0xFF120A20.toInt())
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(36), dp(16), dp(30))
        root.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        setContentView(root)

        content.addView(tv("🔐  Private Vault", 22f, 0xFFFFA6EE.toInt(), true))
        content.addView(tv("Photo, video, music ya koi bhi file yahan chhupayein. Gallery / music player / file manager me nahi dikhegi. Sirf MYRA (lock ke baad) dikhayega.", 12f, 0xFFCFA6D6.toInt(), false))
        val add = Button(this)
        add.text = "➕  Files chhupao (photo / video / music / file)"
        add.isAllCaps = false
        add.setTextColor(Color.WHITE)
        add.setBackgroundResource(R.drawable.bg_btn_3d)
        add.stateListAnimator = null
        add.setOnClickListener { pick.launch(arrayOf("*/*")) }
        val lp = LinearLayout.LayoutParams(MATCH, dp(52))
        lp.topMargin = dp(14)
        content.addView(add, lp)
        info = tv("", 12f, 0xFFFFE08A.toInt(), false)
        info.setPadding(0, dp(8), 0, dp(4))
        content.addView(info)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        content.addView(list)
        content.visibility = View.INVISIBLE
    }

    override fun onResume() {
        super.onResume()
        val needLock = SecurityManager.hasAnyUnlockMethod(this)
        if (needLock && !AppLockActivity.isUnlockedThisSession) {
            content.visibility = View.INVISIBLE
            AppLockActivity.launch(this)
            return
        }
        content.visibility = View.VISIBLE
        refresh()
    }

    private fun tv(t: String, sp: Float, color: Int, bold: Boolean): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = sp
        v.setTextColor(color)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun pretty(f: File): String = f.name.substringAfter('_', f.name)

    private fun size(f: File): String {
        val b = f.length()
        return when {
            b > 1_000_000 -> "%.1f MB".format(b / 1_000_000.0)
            b > 1_000 -> "${b / 1000} KB"
            else -> "$b B"
        }
    }

    private fun refresh() {
        list.removeAllViews()
        val files = (dir().listFiles() ?: emptyArray()).sortedByDescending { it.lastModified() }
        info.text = if (files.isEmpty()) "Vault khali hai." else "${files.size} file(s) chhupi hui hain. Tap = kholo, lamba dabao = Restore / Delete"
        for (f in files) {
            val row = TextView(this)
            val icon = when (mime(f)?.substringBefore('/')) {
                "image" -> "🖼️"
                "video" -> "🎬"
                "audio" -> "🎵"
                else -> "📄"
            }
            row.text = "$icon  ${pretty(f)}\n${size(f)}"
            row.setTextColor(Color.WHITE)
            row.textSize = 14f
            row.setPadding(dp(14), dp(12), dp(14), dp(12))
            val g = GradientDrawable()
            g.cornerRadius = dp(12).toFloat()
            g.setColor(0x33FFFFFF)
            g.setStroke(dp(1), 0x66FF4DA6)
            row.background = g
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.topMargin = dp(8)
            row.layoutParams = lp
            row.setOnClickListener { open(f) }
            row.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(pretty(f))
                    .setItems(arrayOf("↩️  Restore (wapas nikalo)", "🗑️  Delete hamesha ke liye")) { _, i ->
                        if (i == 0) {
                            pendingRestore = f
                            restore.launch(pretty(f))
                        } else {
                            AlertDialog.Builder(this).setTitle("Delete?").setMessage(pretty(f))
                                .setPositiveButton("Delete") { _, _ -> f.delete(); refresh() }
                                .setNegativeButton("Cancel", null).show()
                        }
                    }.show()
                true
            }
            list.addView(row)
        }
    }

    private fun mime(f: File): String? {
        val ext = f.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private fun open(f: File) {
        val m = mime(f) ?: ""
        if (m.startsWith("image/")) {
            val iv = ImageView(this)
            iv.setBackgroundColor(Color.BLACK)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            val o = BitmapFactory.Options()
            o.inSampleSize = 2
            iv.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath, o))
            AlertDialog.Builder(this).setView(iv).setPositiveButton("Close", null).show()
        } else if (m.startsWith("video/") || m.startsWith("audio/")) {
            val vv = VideoView(this)
            vv.setMediaController(MediaController(this).also { it.setAnchorView(vv) })
            vv.setVideoURI(Uri.fromFile(f))
            val box = LinearLayout(this)
            box.setBackgroundColor(Color.BLACK)
            box.gravity = Gravity.CENTER
            box.addView(vv, LinearLayout.LayoutParams(MATCH, dp(300)))
            val d = AlertDialog.Builder(this).setView(box).setPositiveButton("Close", null).create()
            d.setOnDismissListener { vv.stopPlayback() }
            d.show()
            vv.start()
        } else {
            Toast.makeText(this, "Is type ki file yahan khul nahi sakti. Restore karke kholein.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importAll(uris: List<Uri>) {
        var ok = 0
        var notDeleted = 0
        for (u in uris) {
            try {
                var name = "file"
                contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0) ?: name
                }
                val out = File(dir(), "${System.currentTimeMillis()}${ok}_${name.replace('/', '_')}")
                contentResolver.openInputStream(u)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                if (out.exists() && out.length() > 0) {
                    ok++
                    var gone = false
                    try {
                        gone = DocumentsContract.deleteDocument(contentResolver, u)
                    } catch (e: Throwable) {
                    }
                    if (!gone) notDeleted++
                }
            } catch (e: Throwable) {
            }
        }
        Toast.makeText(this, "$ok file(s) vault me chhup gayi", Toast.LENGTH_SHORT).show()
        if (notDeleted > 0) {
            AlertDialog.Builder(this)
                .setTitle("Asli copy abhi bhi gallery me hai")
                .setMessage("$notDeleted file(s) ki asli copy phone ne delete karne nahi di. Gallery / Files app se un files ko khud delete kar dein, tab wo poori tarah chhup jayengi.")
                .setPositiveButton("Theek hai", null).show()
        }
        refresh()
    }
}

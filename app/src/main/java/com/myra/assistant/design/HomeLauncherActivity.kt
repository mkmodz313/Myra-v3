package com.myra.assistant.design

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.ui.chat.ChatActivity
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.wallpaper.WallpaperActivity
import java.util.concurrent.Executors

/**
 * The REAL phone home screen, in the chosen design (Android / iOS / Premium look).
 *
 * This activity is declared in the manifest with category HOME + DEFAULT, so Android lets the user pick MYRA
 * as their default launcher (a system prompt the user confirms themselves - MYRA never forces this, it only
 * shows up as one of the choices, exactly like any other launcher app). Everything here is the SAME themed
 * grid of real installed apps already used by Apps Box; this screen just also behaves like a Home screen:
 * - Back / Home button never closes it (a Home screen can't be "exited"), it only sends it to the background.
 * - It always reflects whichever design is currently applied, refreshing every time it is shown.
 */
class HomeLauncherActivity : AppCompatActivity() {

    data class AppItem(val label: String, val pkg: String, val icon: Drawable)

    private lateinit var design: AppDesign
    private var all: List<AppItem> = emptyList()
    private val adapter = Adapter()
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var search: EditText

    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) search.setText(t)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_apps_box)
        com.myra.assistant.ui.NetworkBanner.attach(this)

        design = AppDesigns.current(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.boxRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }

        search = findViewById(R.id.boxSearch)
        val grid = findViewById<RecyclerView>(R.id.boxGrid)
        grid.layoutManager = GridLayoutManager(this, 4)
        grid.adapter = adapter

        findViewById<View>(R.id.boxPreviewBar).visibility = View.GONE   // this is the real home screen, never a preview
        applyDesign()
        wireDock(grid)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { filter(s?.toString().orEmpty()) }
        })
        findViewById<View>(R.id.boxMic).setOnClickListener {
            try {
                voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "App ka naam bolein")
                })
            } catch (_: Exception) { Toast.makeText(this, "Voice search is phone par available nahi", Toast.LENGTH_SHORT).show() }
        }
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        design = AppDesigns.current(this)      // re-apply in case the design was changed while this was in the background
        applyDesign()
        loadApps()
    }

    /** A Home screen can never be "closed" - pressing back (or the Home key again) just backgrounds it. */
    override fun onBackPressed() { moveTaskToBack(true) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun applyDesign() {
        val d = design
        val dm = resources.displayMetrics.density
        findViewById<DesignBackgroundView>(R.id.boxBg).design = d

        val art = findViewById<ImageView>(R.id.boxArt)
        val title = findViewById<View>(R.id.boxTitle)
        if (d.art != 0) { art.setImageResource(d.art); art.visibility = View.VISIBLE; title.visibility = View.GONE }
        else {
            art.visibility = View.GONE; title.visibility = View.VISIBLE
            findViewById<TextView>(R.id.boxName).apply { setTextColor(d.accent); setShadowLayer(14f, 0f, 0f, d.accent) }
            findViewById<TextView>(R.id.boxTag).setTextColor(d.textColor)
        }

        findViewById<View>(R.id.boxSearchBar).background = DesignStyle.searchBar(d, dm)
        search.setTextColor(d.textColor); search.setHintTextColor(Color.argb(150, Color.red(d.textColor), Color.green(d.textColor), Color.blue(d.textColor)))
        findViewById<View>(R.id.boxDock).background = DesignStyle.dock(d, dm)
        listOf(R.id.dockHomeLabel, R.id.dockDesignsLabel, R.id.dockSettingsLabel, R.id.dockWallpapersLabel).forEach {
            findViewById<TextView>(it).setTextColor(d.textColor)
        }
        findViewById<ImageView>(R.id.dockCenterImg).background = DesignStyle.iconFrame(d.copy(frameStyle = 1, iconShape = 0), dp(58), dm)
        adapter.notifyDataSetChanged()
    }

    private fun wireDock(grid: RecyclerView) {
        findViewById<View>(R.id.dockHome).setOnClickListener { grid.smoothScrollToPosition(0) }
        findViewById<View>(R.id.dockDesigns).setOnClickListener { startActivity(Intent(this, DesignsActivity::class.java)) }
        findViewById<View>(R.id.dockCenter).setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        findViewById<View>(R.id.dockSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.dockWallpapers).setOnClickListener { startActivity(Intent(this, WallpaperActivity::class.java)) }
    }

    private fun loadApps() {
        io.execute {
            try {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val list = pm.queryIntentActivities(intent, 0)
                    .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
                    .distinctBy { it.pkg }
                    .sortedBy { it.label.lowercase() }
                ui.post { if (!isDestroyed) { all = list; filter(search.text.toString()) } }
            } catch (_: Throwable) { }
        }
    }

    private fun filter(q: String) {
        val s = q.trim().lowercase()
        adapter.submit(if (s.isEmpty()) all else all.filter { it.label.lowercase().contains(s) })
    }

    private fun open(item: AppItem) {
        val i = packageManager.getLaunchIntentForPackage(item.pkg)
        if (i == null) { Toast.makeText(this, "App khul nahi saki", Toast.LENGTH_SHORT).show(); return }
        try { startActivity(i) } catch (_: Exception) { Toast.makeText(this, "App khul nahi saki", Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<AppItem> = emptyList()

        fun submit(list: List<AppItem>) { items = list; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val frame: FrameLayout = v.findViewById(R.id.appFrame)
            val icon: ImageView = v.findViewById(R.id.appIcon)
            val label: TextView = v.findViewById(R.id.appLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app_box, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val it = items[position]
            val d = design
            val dm = resources.displayMetrics.density
            val frameSize = dp(70)
            val iconSize = if (d.frameStyle == 0) dp(66) else dp(52)

            h.frame.background = DesignStyle.iconFrame(d, frameSize, dm)
            h.icon.layoutParams = (h.icon.layoutParams as FrameLayout.LayoutParams).also { lp -> lp.width = iconSize; lp.height = iconSize }
            val radius = d.cornerFraction * iconSize
            h.icon.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) { outline.setRoundRect(0, 0, view.width, view.height, radius) }
            }
            h.icon.clipToOutline = true
            h.icon.setImageDrawable(it.icon)

            h.label.text = it.label
            h.label.setTextColor(d.textColor)
            if (d.labelPill) {
                h.label.background = DesignStyle.labelPill(d, dm)
                h.label.setPadding(dp(8), dp(3), dp(8), dp(3))
                h.label.setTextColor(Color.WHITE)
                h.label.setShadowLayer(0f, 0f, 0f, 0)
            } else {
                h.label.background = null
                h.label.setPadding(0, 0, 0, 0)
                h.label.setShadowLayer(if (DesignStyle.isLight(d)) 0f else 4f, 0f, 1f, Color.BLACK)
            }
            h.itemView.setOnClickListener { _ -> open(it) }
        }
    }
}

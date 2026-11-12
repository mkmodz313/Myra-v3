package com.myra.assistant.wallpaper

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.ui.avatar.AmbientFxView
import java.util.concurrent.Executors

/** Live Wallpapers gallery: search, categories, cards, auto-refreshing catalog. */
class WallpaperActivity : AppCompatActivity() {

    private lateinit var grid: RecyclerView
    private lateinit var chips: LinearLayout
    private lateinit var banner: TextView
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private lateinit var fx: AmbientFxView
    private val adapter = CardAdapter { openDetail(it) }

    private var cloud: List<WallpaperItem> = emptyList()
    private var category = "All"
    private var query = ""
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            loadCloud(silent = true)
            ui.postDelayed(this, CloudConfig.REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_wallpapers)

        grid = findViewById(R.id.wpGrid)
        chips = findViewById(R.id.wpChips)
        banner = findViewById(R.id.wpBanner)
        loading = findViewById(R.id.wpLoading)
        empty = findViewById(R.id.wpEmpty)
        fx = findViewById(R.id.wpFx)

        com.myra.assistant.ui.NetworkBanner.attach(this)
        grid.layoutManager = GridLayoutManager(this, 2)
        grid.adapter = adapter

        val header = findViewById<View>(R.id.wpHeader)
        val hTop = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.wpRoot)) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, hTop + sys.top, header.paddingRight, header.paddingBottom)
            grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, 10 + sys.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.wpBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.wpRefresh).setOnClickListener { loadCloud(silent = false) }
        findViewById<EditText>(R.id.wpSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim().orEmpty(); render() }
        })

        cloud = WallpaperRepository.cached(this)   // instantly show what was synced last time
        ThumbLoader.warm(this, cloud.map { it.thumbUrl } + WallpaperItem.SAMPLES.map { it.thumbUrl })
        render()
    }

    override fun onResume() {
        super.onResume()
        fx.start()
        ui.removeCallbacks(poll)
        ui.post(poll)           // refresh now, then every REFRESH_INTERVAL_MS while the page is open
    }

    override fun onPause() {
        ui.removeCallbacks(poll)
        fx.stop()
        super.onPause()
    }

    private fun loadCloud(silent: Boolean) {
        if (!CloudConfig.isConfigured) {
            showBanner("Cloud catalog abhi configure nahi hai - sirf bundled sample wallpapers dikh rahe hain. (backend/README_BACKEND.md)")
            return
        }
        if (!silent) loading.visibility = View.VISIBLE
        io.execute {
            val result = try { WallpaperRepository.fetchCloud(this) } catch (e: Exception) { null }
            ui.post {
                if (isDestroyed) return@post
                loading.visibility = View.GONE
                if (result != null) { cloud = result; showBanner(null) }
                else showBanner("Internet/Server problem - saved wallpapers dikh rahe hain.")
                render()
            }
        }
    }

    private fun showBanner(text: String?) {
        banner.text = text
        banner.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun render() {
        val all = cloud + WallpaperItem.SAMPLES
        buildChips(all.map { it.category }.distinct().sorted())
        val q = query.lowercase()
        val shown = all.filter {
            (category == "All" || it.category == category) &&
                (q.isEmpty() || it.title.lowercase().contains(q) || it.description.lowercase().contains(q) || it.category.lowercase().contains(q))
        }
        adapter.submitList(shown)
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildChips(categories: List<String>) {
        val names = listOf("All") + categories
        if (category !in names) category = "All"
        chips.removeAllViews()
        names.forEach { name ->
            chips.addView(TextView(this).apply {
                text = name
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(36, 14, 36, 14)
                isSelected = name == category
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.marginEnd = 14 }
                setOnClickListener { category = name; render() }
            })
        }
    }

    private fun openDetail(item: WallpaperItem) {
        startActivity(Intent(this, WallpaperDetailActivity::class.java).putExtra("item", item.toJson().toString()))
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private class CardAdapter(val onClick: (WallpaperItem) -> Unit) :
        ListAdapter<WallpaperItem, CardAdapter.VH>(object : DiffUtil.ItemCallback<WallpaperItem>() {
            override fun areItemsTheSame(a: WallpaperItem, b: WallpaperItem) = a.id == b.id
            override fun areContentsTheSame(a: WallpaperItem, b: WallpaperItem) = a == b
        }) {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val thumb: ImageView = v.findViewById(R.id.wpThumb)
            val title: TextView = v.findViewById(R.id.wpTitle)
            val category: TextView = v.findViewById(R.id.wpCategory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_wallpaper, parent, false))

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = getItem(position)
            h.title.text = item.title
            h.category.text = if (item.isBundled) "${item.category} · Sample" else item.category
            ThumbLoader.load(h.itemView.context, item.thumbUrl, h.thumb)
            h.itemView.setOnClickListener { onClick(item) }
        }
    }
}

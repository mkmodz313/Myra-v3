package com.myra.assistant.design

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R

/** Gallery of Android / iOS / Premium designs. Tap one to preview it in the Apps Box, then Apply. */
class DesignsActivity : AppCompatActivity() {

    private var category = "All"
    private lateinit var grid: RecyclerView
    private lateinit var chips: LinearLayout
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_designs)
        com.myra.assistant.ui.NetworkBanner.attach(this)

        val header = findViewById<View>(R.id.dsHeader)
        val top = header.paddingTop
        grid = findViewById(R.id.dsGrid)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dsRoot)) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(header.paddingLeft, top + b.top, header.paddingRight, header.paddingBottom)
            grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, 10 + b.bottom)
            insets
        }
        chips = findViewById(R.id.dsChips)
        grid.layoutManager = GridLayoutManager(this, 2)
        grid.adapter = adapter
        findViewById<View>(R.id.dsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.dsOpenBox).setOnClickListener { startActivity(Intent(this, AppsBoxActivity::class.java)) }
        buildChips()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()      // "Applied" badge may have changed
    }

    private fun buildChips() {
        chips.removeAllViews()
        listOf("All", "Premium", "Android", "iOS").forEach { name ->
            chips.addView(TextView(this).apply {
                text = if (name == "All") "All (${AppDesigns.ALL.size})" else "$name (${AppDesigns.ALL.count { it.category == name }})"
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(36, 14, 36, 14)
                isSelected = name == category
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = 14 }
                setOnClickListener { category = name; buildChips(); refresh() }
            })
        }
    }

    private fun refresh() {
        adapter.items = if (category == "All") AppDesigns.ALL else AppDesigns.ALL.filter { it.category == category }
        adapter.notifyDataSetChanged()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        var items: List<AppDesign> = emptyList()

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val preview: DesignPreviewView = v.findViewById(R.id.dsPreview)
            val name: TextView = v.findViewById(R.id.dsName)
            val cat: TextView = v.findViewById(R.id.dsCat)
            val applied: View = v.findViewById(R.id.dsApplied)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_design, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val d = items[position]
            h.preview.design = d
            h.name.text = d.name
            h.cat.text = d.category
            h.applied.visibility = if (AppDesigns.current(this@DesignsActivity).id == d.id) View.VISIBLE else View.GONE
            h.itemView.setOnClickListener {
                startActivity(Intent(this@DesignsActivity, AppsBoxActivity::class.java).putExtra("preview_id", d.id))
            }
        }
    }
}

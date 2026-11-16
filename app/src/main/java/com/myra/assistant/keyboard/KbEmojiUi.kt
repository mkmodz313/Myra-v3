package com.myra.assistant.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class KbItemVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

/** Cells of ONE page (emoji grid, symbol grid or the Kalimat / Durood list). */
class KbItemAdapter(
    private val page: KbPage,
    private val themeOf: () -> KbTheme,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<KbItemVH>() {

    var items: List<KbItem> = emptyList()

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KbItemVH {
        val c = parent.context
        val tv = TextView(c)
        tv.gravity = Gravity.CENTER
        if (page.kind == KbPage.LIST) {
            tv.textSize = page.textSize
            tv.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 14))
            val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(c, 4), dp(c, 3), dp(c, 4), dp(c, 3))
            tv.layoutParams = lp
        } else {
            tv.textSize = page.textSize
            tv.maxLines = 1
            tv.includeFontPadding = false
            tv.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (page.cols <= 3) dp(c, 40) else dp(c, 44)
            )
        }
        return KbItemVH(tv)
    }

    override fun onBindViewHolder(holder: KbItemVH, position: Int) {
        val item = items[position]
        val th = themeOf()
        val c = holder.tv.context
        if (page.kind == KbPage.LIST) {
            val sb = SpannableStringBuilder()
            val title = item.title
            if (title != null) {
                sb.append(title).append("\n")
                sb.setSpan(RelativeSizeSpan(0.62f), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(th.accent), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val start = sb.length
            sb.append(item.text)
            sb.setSpan(ForegroundColorSpan(KbShiny.make(c, th, th.keyFill, 14f).textColor()), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.tv.text = sb
            holder.tv.maxLines = 4
            holder.tv.setLineSpacing(0f, 1.15f)
        } else {
            holder.tv.text = item.text
            holder.tv.setTextColor(th.text)
        }
        if (page.boxed) {
            val dr = KbShiny.make(c, th, th.keyFill, if (page.kind == KbPage.LIST) 14f else 10f)
            holder.tv.background = dr
            if (page.kind == KbPage.GRID) holder.tv.setTextColor(dr.textColor())
        } else {
            holder.tv.background = null
        }
        holder.tv.setOnClickListener { onPick(item.text) }
    }

    override fun getItemCount(): Int = items.size

    private fun blend(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1 - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1 - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        return Color.argb(255, r, g, bl)
    }
}

class KbPageVH(val rv: RecyclerView) : RecyclerView.ViewHolder(rv)

/** Horizontal pager: swipe left/right between pages, scroll up/down inside a page. */
class KbPagerAdapter(
    private val pages: List<KbPage>,
    private val themeOf: () -> KbTheme,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<KbPageVH>() {

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KbPageVH {
        val c = parent.context
        val page = pages[viewType]
        val rv = RecyclerView(c)
        rv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        rv.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        rv.clipToPadding = false
        val pad = (4 * c.resources.displayMetrics.density).toInt()
        rv.setPadding(pad, 0, pad, pad)
        rv.layoutManager = if (page.kind == KbPage.LIST) LinearLayoutManager(c) else GridLayoutManager(c, page.cols)
        rv.adapter = KbItemAdapter(page, themeOf, onPick)
        return KbPageVH(rv)
    }

    override fun onBindViewHolder(holder: KbPageVH, position: Int) {
        val ad = holder.rv.adapter as KbItemAdapter
        ad.items = pages[position].getter()
        ad.notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pages.size
}

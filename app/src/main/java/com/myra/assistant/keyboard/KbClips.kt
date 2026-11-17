package com.myra.assistant.keyboard

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.io.File

/** Clipboard history without a small limit: every copied text is kept (stored in a file, newest first). */
object KbClips {
    private const val MAX = 5000
    private const val MAX_LEN = 30000
    private var cache: ArrayList<String>? = null

    private fun file(c: Context) = File(c.applicationContext.filesDir, "kb_clips.json")

    @Synchronized
    fun all(c: Context): List<String> {
        var l = cache
        if (l == null) {
            l = ArrayList()
            try {
                val f = file(c)
                if (f.exists()) {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) l.add(arr.getString(i))
                } else {
                    l.addAll(KbPrefs.clips(c)) // older small history
                }
            } catch (e: Throwable) {
            }
            cache = l
        }
        return l
    }

    @Synchronized
    private fun save(c: Context) {
        try {
            val arr = JSONArray()
            for (s in cache ?: return) arr.put(s)
            file(c).writeText(arr.toString())
        } catch (e: Throwable) {
        }
    }

    @Synchronized
    fun add(c: Context, text: String) {
        val t = if (text.length > MAX_LEN) text.substring(0, MAX_LEN) else text
        val l = all(c) as ArrayList<String>
        l.remove(t)
        l.add(0, t)
        while (l.size > MAX) l.removeAt(l.size - 1)
        save(c)
    }

    @Synchronized
    fun remove(c: Context, text: String) {
        (all(c) as ArrayList<String>).remove(text)
        save(c)
    }

    @Synchronized
    fun clear(c: Context) {
        (all(c) as ArrayList<String>).clear()
        save(c)
    }
}

class KbClipVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

/** Shiny list of all copied texts. Tap = paste, long press = delete that one. */
class KbClipAdapter(
    private val themeOf: () -> KbTheme,
    private val onPick: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<KbClipVH>() {

    var items: List<String> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KbClipVH {
        val c = parent.context
        val d = c.resources.displayMetrics.density
        val tv = TextView(c)
        tv.textSize = 14f
        tv.maxLines = 3
        tv.ellipsize = android.text.TextUtils.TruncateAt.END
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding((14 * d).toInt(), (9 * d).toInt(), (14 * d).toInt(), (15 * d).toInt())
        val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins((4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
        tv.layoutParams = lp
        return KbClipVH(tv)
    }

    override fun onBindViewHolder(holder: KbClipVH, position: Int) {
        val s = items[position]
        val th = themeOf()
        val dr = KbShiny.make(holder.tv.context, th, th.keyFill, 12f)
        holder.tv.background = dr
        holder.tv.setTextColor(dr.textColor())
        holder.tv.text = s
        holder.tv.setOnClickListener { onPick(s) }
        holder.tv.setOnLongClickListener {
            onDelete(s)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}

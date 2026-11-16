package com.myra.assistant.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * One font / decoration style. render() turns the plain letters the user typed into the styled text.
 * pre/post = decoration put around the word, sep = separator put between every letter,
 * tf = optional letter converter (bold, script, fullwidth ...).
 */
class KbFontStyle(
    val id: String,
    val name: String,
    val pre: String = "",
    val post: String = "",
    val sep: String = "",
    val tf: ((String) -> String)? = null
) {
    fun render(raw: String): String {
        val f = tf
        var body = if (f != null) f(raw) else raw
        if (sep.isNotEmpty()) {
            val sb = StringBuilder(sep)
            var i = 0
            while (i < body.length) {
                val c = body.codePointAt(i)
                val n = Character.charCount(c)
                sb.append(body, i, i + n).append(sep)
                i += n
            }
            body = sb.toString()
        }
        return pre + body + post
    }
}

object KbFonts {

    private fun cp(c: Int): String = String(Character.toChars(c))

    private fun mathMap(
        upper: Int, lower: Int, digit: Int = -1, exc: Map<Char, String> = emptyMap()
    ): (String) -> String = { s ->
        val sb = StringBuilder()
        for (ch in s) {
            val e = exc[ch]
            when {
                e != null -> sb.append(e)
                ch in 'A'..'Z' -> sb.append(cp(upper + (ch - 'A')))
                ch in 'a'..'z' -> sb.append(cp(lower + (ch - 'a')))
                ch in '0'..'9' && digit >= 0 -> sb.append(cp(digit + (ch - '0')))
                else -> sb.append(ch)
            }
        }
        sb.toString()
    }

    private val full: (String) -> String = { s ->
        val sb = StringBuilder()
        for (ch in s) {
            if (ch.code in 33..126) sb.append((ch.code - 33 + 0xFF01).toChar()) else sb.append(ch)
        }
        sb.toString()
    }

    private val SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"
    private val smallCaps: (String) -> String = { s ->
        val sb = StringBuilder()
        for (ch in s) {
            val l = ch.lowercaseChar()
            if (l in 'a'..'z') sb.append(SMALL[l - 'a']) else sb.append(ch)
        }
        sb.toString()
    }

    private val squared: (String) -> String = { s ->
        val sb = StringBuilder()
        for (ch in s) {
            val u = ch.uppercaseChar()
            if (u in 'A'..'Z') sb.append(cp(0x1F130 + (u - 'A'))) else sb.append(ch)
        }
        sb.toString()
    }

    private val circled: (String) -> String = run {
        val ex = HashMap<Char, String>()
        ex['0'] = "⓪"
        for (d in 1..9) ex['0' + d] = cp(0x2460 + d - 1)
        mathMap(0x24B6, 0x24D0, -1, ex)
    }

    private val negCircled: (String) -> String = run {
        val ex = HashMap<Char, String>()
        ex['0'] = "⓿"
        for (d in 1..9) ex['0' + d] = cp(0x2776 + d - 1)
        val up = mathMap(0x1F150, 0x1F150, -1, ex)
        val f: (String) -> String = { s -> up(s.uppercase()) }
        f
    }

    private val dbl = mathMap(
        0x1D538, 0x1D552, 0x1D7D8,
        mapOf('C' to "ℂ", 'H' to "ℍ", 'N' to "ℕ", 'P' to "ℙ", 'Q' to "ℚ", 'R' to "ℝ", 'Z' to "ℤ")
    )
    private val bold = mathMap(0x1D400, 0x1D41A, 0x1D7CE)
    private val italic = mathMap(0x1D434, 0x1D44E, -1, mapOf('h' to "ℎ"))
    private val boldItalic = mathMap(0x1D468, 0x1D482)
    private val scriptBold = mathMap(0x1D4D0, 0x1D4EA)
    private val frakturBold = mathMap(0x1D56C, 0x1D586)
    private val mono = mathMap(0x1D670, 0x1D68A, 0x1D7F6)
    private val sansBold = mathMap(0x1D5D4, 0x1D5EE, 0x1D7EC)

    val normal = KbFontStyle("normal", "Normal (default)")

    private fun w(n: Int, pre: String, post: String, tf: ((String) -> String)? = null) =
        KbFontStyle("s%02d".format(n), "%02d".format(n), pre, post, "", tf)

    /** The 30 decoration styles + extra letter fonts. */
    val all: List<KbFontStyle> = listOf(
        normal,
        w(1, "꧁", "꧂"),
        w(2, "𓆩", "𓆪"),
        w(3, "『", "』", full),
        w(4, "乂", "乂", full),
        w(5, "メ", "メ"),
        w(6, "亗", "亗"),
        w(7, "么", "么"),
        w(8, "༺", "༻"),
        w(9, "࿐", "࿐"),
        w(10, "〆", "〆"),
        w(11, "♛", "♛"),
        w(12, "♕", "♕"),
        w(13, "✧･ﾟ", "･ﾟ✧"),
        w(14, "⫷", "⫸"),
        w(15, "◥", "◤"),
        KbFontStyle("s16", "16", "", "", "░"),
        w(17, "꧁༒", "༒꧂"),
        w(18, "𖤍", "𖤍"),
        w(19, "ᯓ", "ᯓ"),
        w(20, "⸙", "⸙"),
        w(21, "༄", "࿐"),
        w(22, "𓂀", "𓂀"),
        w(23, "𒆜", "𒆜"),
        w(24, "⟡", "⟡"),
        w(25, "⋆｡°✩", "✩°｡⋆"),
        w(26, "『亗』", "『亗』"),
        w(27, "乛", "乛"),
        w(28, "⌁", "⌁"),
        w(29, "𖣘", "𖣘"),
        w(30, "꧁𓊈𒆜", "𒆜𓊉꧂"),
        // ---- more fonts (letters change shape) ----
        KbFontStyle("f_bold", "Bold", tf = bold),
        KbFontStyle("f_italic", "Italic", tf = italic),
        KbFontStyle("f_bolditalic", "Bold Italic", tf = boldItalic),
        KbFontStyle("f_sans", "Sans Bold", tf = sansBold),
        KbFontStyle("f_script", "Script", tf = scriptBold),
        KbFontStyle("f_gothic", "Gothic", tf = frakturBold),
        KbFontStyle("f_double", "Double Line", tf = dbl),
        KbFontStyle("f_mono", "Typewriter", tf = mono),
        KbFontStyle("f_circle", "Circle", tf = circled),
        KbFontStyle("f_negcircle", "Black Circle", tf = negCircled),
        KbFontStyle("f_square", "Square", tf = squared),
        KbFontStyle("f_small", "Small Caps", tf = smallCaps),
        KbFontStyle("f_full", "Wide", tf = full),
        KbFontStyle("c_script", "Script ꧁ ꧂", "꧁", "꧂", tf = scriptBold),
        KbFontStyle("c_gothic", "Gothic ༺ ༻", "༺", "༻", tf = frakturBold),
        KbFontStyle("c_bold", "Bold ♛", "♛", "♛", tf = bold),
        KbFontStyle("c_circle", "Circle ✧ ✧", "✧", "✧", tf = circled)
    )

    fun byId(id: String): KbFontStyle = all.firstOrNull { it.id == id } ?: normal
}

class KbFontVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

/** The list of fonts shown in the keyboard's font panel. */
class KbFontAdapter(
    private val themeOf: () -> KbTheme,
    private val selectedId: () -> String,
    private val onPick: (KbFontStyle) -> Unit
) : RecyclerView.Adapter<KbFontVH>() {

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KbFontVH {
        val c = parent.context
        val tv = TextView(c)
        tv.gravity = Gravity.CENTER
        tv.textSize = 19f
        tv.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6))
        val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(c, 4), dp(c, 3), dp(c, 4), dp(c, 3))
        tv.layoutParams = lp
        return KbFontVH(tv)
    }

    override fun onBindViewHolder(holder: KbFontVH, position: Int) {
        val st = KbFonts.all[position]
        val th = themeOf()
        val c = holder.tv.context
        val sel = st.id == selectedId()
        val sb = SpannableStringBuilder()
        sb.append(st.name).append("\n")
        sb.setSpan(RelativeSizeSpan(0.6f), 0, st.name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(th.accent), 0, st.name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), 0, st.name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val start = sb.length
        sb.append(st.render("Myra 786"))
        sb.setSpan(ForegroundColorSpan(th.text), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        holder.tv.text = sb
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                (th.keyFill and 0x00FFFFFF) or (0xE0 shl 24),
                (th.specialFill and 0x00FFFFFF) or (0xD0 shl 24)
            )
        )
        g.cornerRadius = dp(c, 12).toFloat()
        g.setStroke(if (sel) dp(c, 3) else dp(c, 1), if (sel) Color.WHITE else th.accent)
        holder.tv.background = g
        holder.tv.setOnClickListener { onPick(st) }
    }

    override fun getItemCount(): Int = KbFonts.all.size
}

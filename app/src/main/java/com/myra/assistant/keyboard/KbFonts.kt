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
 * One font style: tf turns plain letters into the stylish letters, reverse flips the order (upside down).
 * keycap() is what the keys of the keyboard show when this font is active.
 */
class KbFontStyle(
    val id: String,
    val name: String,
    val reverse: Boolean = false,
    val tf: ((String) -> String)? = null
) {
    fun keycap(s: String): String {
        val f = tf
        return if (f != null) f(s) else s
    }

    fun render(raw: String): String {
        val f = tf
        var body = if (f != null) f(raw) else raw
        if (reverse) {
            val sb = StringBuilder()
            var i = body.length
            while (i > 0) {
                val c = body.codePointBefore(i)
                sb.appendCodePoint(c)
                i -= Character.charCount(c)
            }
            body = sb.toString()
        }
        return body
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

    private fun parts(s: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val c = s.codePointAt(i)
            out.add(cp(c))
            i += Character.charCount(c)
        }
        return out
    }

    /** upper = 26 look-alike letters (A..Z); lower = 26 more or null (then upper is used for both). */
    private fun tableMap(upper: String, lower: String? = null, digits: String? = null): (String) -> String {
        val u = parts(upper)
        val l = parts(lower ?: upper)
        val d = if (digits != null) parts(digits) else emptyList()
        return { s ->
            val sb = StringBuilder()
            for (ch in s) {
                when {
                    ch in 'A'..'Z' -> sb.append(u[ch - 'A'])
                    ch in 'a'..'z' -> sb.append(l[ch - 'a'])
                    ch in '0'..'9' && d.size == 10 -> sb.append(d[ch - '0'])
                    else -> sb.append(ch)
                }
            }
            sb.toString()
        }
    }

    private fun combine(mark: Char): (String) -> String = { s ->
        val sb = StringBuilder()
        for (ch in s) {
            sb.append(ch)
            if (ch.isLetterOrDigit()) sb.append(mark)
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

    private fun circledDigits(): Map<Char, String> {
        val ex = HashMap<Char, String>()
        ex['0'] = "⓪"
        for (d in 1..9) ex['0' + d] = cp(0x2460 + d - 1)
        return ex
    }

    private fun negCircledDigits(): Map<Char, String> {
        val ex = HashMap<Char, String>()
        ex['0'] = "⓿"
        for (d in 1..9) ex['0' + d] = cp(0x2776 + d - 1)
        return ex
    }

    private fun parenDigits(): Map<Char, String> {
        val ex = HashMap<Char, String>()
        for (d in 1..9) ex['0' + d] = cp(0x2474 + d - 1)
        return ex
    }

    private fun upperOnly(f: (String) -> String): (String) -> String = { s -> f(s.uppercase()) }

    private val script = mathMap(
        0x1D49C, 0x1D4B6, -1,
        mapOf(
            'B' to "ℬ", 'E' to "ℰ", 'F' to "ℱ", 'H' to "ℋ", 'I' to "ℐ", 'L' to "ℒ", 'M' to "ℳ", 'R' to "ℛ",
            'e' to "ℯ", 'g' to "ℊ", 'o' to "ℴ"
        )
    )
    private val fraktur = mathMap(
        0x1D504, 0x1D51E, -1,
        mapOf('C' to "ℭ", 'H' to "ℌ", 'I' to "ℑ", 'R' to "ℜ", 'Z' to "ℨ")
    )
    private val dbl = mathMap(
        0x1D538, 0x1D552, 0x1D7D8,
        mapOf('C' to "ℂ", 'H' to "ℍ", 'N' to "ℕ", 'P' to "ℙ", 'Q' to "ℚ", 'R' to "ℝ", 'Z' to "ℤ")
    )
    private val SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"
    private val smallCaps: (String) -> String = { s ->
        val sb = StringBuilder()
        for (ch in s) {
            val l = ch.lowercaseChar()
            if (l in 'a'..'z') sb.append(SMALL[l - 'a']) else sb.append(ch)
        }
        sb.toString()
    }

    val normal = KbFontStyle("normal", "Normal (default)")

    /** 30 different modern fonts (letter shapes only - no decoration symbols). */
    val all: List<KbFontStyle> = listOf(
        normal,
        KbFontStyle("f01", "Bold", tf = mathMap(0x1D400, 0x1D41A, 0x1D7CE)),
        KbFontStyle("f02", "Italic", tf = mathMap(0x1D434, 0x1D44E, -1, mapOf('h' to "ℎ"))),
        KbFontStyle("f03", "Bold Italic", tf = mathMap(0x1D468, 0x1D482)),
        KbFontStyle("f04", "Script", tf = script),
        KbFontStyle("f05", "Bold Script", tf = mathMap(0x1D4D0, 0x1D4EA)),
        KbFontStyle("f06", "Fraktur", tf = fraktur),
        KbFontStyle("f07", "Bold Fraktur", tf = mathMap(0x1D56C, 0x1D586)),
        KbFontStyle("f08", "Double Line", tf = dbl),
        KbFontStyle("f09", "Sans", tf = mathMap(0x1D5A0, 0x1D5BA, 0x1D7E2)),
        KbFontStyle("f10", "Sans Bold", tf = mathMap(0x1D5D4, 0x1D5EE, 0x1D7EC)),
        KbFontStyle("f11", "Sans Italic", tf = mathMap(0x1D608, 0x1D622, 0x1D7E2)),
        KbFontStyle("f12", "Sans Bold Italic", tf = mathMap(0x1D63C, 0x1D656, 0x1D7EC)),
        KbFontStyle("f13", "Typewriter", tf = mathMap(0x1D670, 0x1D68A, 0x1D7F6)),
        KbFontStyle("f14", "Bubble", tf = mathMap(0x24B6, 0x24D0, -1, circledDigits())),
        KbFontStyle("f15", "Black Bubble", tf = upperOnly(mathMap(0x1F150, 0x1F150, -1, negCircledDigits()))),
        KbFontStyle("f16", "Square", tf = upperOnly(mathMap(0x1F130, 0x1F130))),
        KbFontStyle("f17", "Black Square", tf = upperOnly(mathMap(0x1F170, 0x1F170))),
        KbFontStyle("f18", "Bracket", tf = mathMap(0x1F110, 0x249C, -1, parenDigits())),
        KbFontStyle("f19", "Wide", tf = full),
        KbFontStyle("f20", "Small Caps", tf = smallCaps),
        KbFontStyle(
            "f21", "Tiny",
            tf = tableMap(
                "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖᑫʳˢᵗᵘᵛʷˣʸᶻ", null, "⁰¹²³⁴⁵⁶⁷⁸⁹"
            )
        ),
        KbFontStyle(
            "f22", "Upside Down", reverse = true,
            tf = tableMap("∀ᗺƆᗡƎℲ⅁HIſ⋊˥WNOԀꝹᴚS⊥∩ΛMX⅄Z", "ɐqɔpǝɟƃɥᴉɾʞlɯuodbɹsʇnʌʍxʎz")
        ),
        KbFontStyle("f23", "Canadian", tf = tableMap("ᗩᗷᑕᗪᗴᖴᘜᕼᎥᒍᛕᒪᗰᑎᗝᑭᑫᖇᔕ丅ᑌᐯᗯ᙭Ƴᘔ")),
        KbFontStyle("f24", "Kanji", tf = tableMap("卂乃匚刀乇下厶卄工丁长乚从ん口尸㔿尺丂丅凵リ山乂丫乙")),
        KbFontStyle("f25", "Cash", tf = tableMap("₳฿₵ĐɆ₣₲ⱧłJ₭Ⱡ₥₦Ø₱QⱤ₴₮ɄV₩ӾɎⱫ")),
        KbFontStyle("f26", "Strike", tf = combine('\u0336')),
        KbFontStyle("f27", "Underline", tf = combine('\u0332')),
        KbFontStyle("f28", "Slash", tf = combine('\u0338')),
        KbFontStyle("f29", "Double Underline", tf = combine('\u0333')),
        KbFontStyle("f30", "Tilde", tf = combine('\u0303'))
    )

    private var visibleCache: List<KbFontStyle>? = null

    /** Only the fonts this phone can really draw are shown (no empty boxes). */
    fun visible(): List<KbFontStyle> {
        val c = visibleCache
        if (c != null) return c
        val p = android.graphics.Paint()
        val out = all.filter {
            it.id == "normal" || p.hasGlyph(it.render("Aa")) && p.hasGlyph(it.render("Zz"))
        }
        visibleCache = out
        return out
    }

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
        val st = KbFonts.visible()[position]
        val th = themeOf()
        val c = holder.tv.context
        val sel = st.id == selectedId()
        val sb = SpannableStringBuilder()
        sb.append(st.name).append("\n")
        sb.setSpan(RelativeSizeSpan(0.6f), 0, st.name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(th.accent), 0, st.name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), 0, st.name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val start = sb.length
        sb.append(st.render("Myra Keyboard 786"))
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

    override fun getItemCount(): Int = KbFonts.visible().size
}

package com.myra.assistant.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.util.Locale

/**
 * The real typing surface of MYRA Keyboard. Everything is drawn on a Canvas so the 3D / glass /
 * bubble / PNG key looks can be changed freely. Supports multi-touch, long-press alternates,
 * key preview bubbles, shift / caps-lock, symbols pages and many language layouts.
 */
class MyraKeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(t: String)
        fun onDelete()
        fun onEnter()
        fun onLangNext()
        fun onLangPicker()
        fun onKeyFeedback()
    }

    companion object {
        const val T_CHAR = 0
        const val T_SHIFT = 1
        const val T_DEL = 2
        const val T_SPACE = 3
        const val T_ENTER = 4
        const val T_SYM = 5
        const val T_MODE2 = 6
        const val T_LANG = 7

        const val M_LETTERS = 0
        const val M_SYM1 = 1
        const val M_SYM2 = 2

        private val INDIC = setOf("hi", "mr", "bn", "pa", "gu", "or", "ta", "te", "kn", "ml")
        private val RTL = setOf("ar", "ur", "fa")

        private val ALTS: Map<String, List<String>> = mapOf(
            "a" to listOf("à", "á", "â", "ä", "ã", "å", "ā", "æ"),
            "e" to listOf("é", "è", "ê", "ë", "ē", "ę", "ė"),
            "i" to listOf("í", "ì", "î", "ï", "ī", "į"),
            "o" to listOf("ó", "ò", "ô", "ö", "õ", "ø", "ō", "œ"),
            "u" to listOf("ú", "ù", "û", "ü", "ū"),
            "c" to listOf("ç", "ć", "č"),
            "n" to listOf("ñ", "ń"),
            "s" to listOf("ß", "ś", "š"),
            "z" to listOf("ž", "ź", "ż"),
            "y" to listOf("ý", "ÿ"),
            "l" to listOf("ł"),
            "d" to listOf("ð"),
            "g" to listOf("ğ"),
            "." to listOf("…", "?", "!", "'", "\"", ":", ";", "-"),
            "," to listOf("!", "?", "'", "\"", ":", ";"),
            "\$" to listOf("₹", "€", "£", "¥", "¢"),
            "-" to listOf("–", "—", "_"),
            "?" to listOf("¿"),
            "!" to listOf("¡"),
            "क" to listOf("क्ष", "क़"),
            "ज" to listOf("ज्ञ", "ज़"),
            "त" to listOf("त्र"),
            "श" to listOf("श्र"),
            "ड" to listOf("ड़"),
            "ढ" to listOf("ढ़"),
            "फ" to listOf("फ़"),
            "ख" to listOf("ख़"),
            "ग" to listOf("ग़"),
            "ا" to listOf("آ", "أ", "إ"),
            "ی" to listOf("ي", "ئ"),
            "ه" to listOf("ة", "ۃ"),
            "ک" to listOf("ك")
        )
    }

    class Key(var label: String, val type: Int, val weight: Float) {
        var hint: String? = null
        var display: String = label
        val cell = RectF()
        val rect = RectF()
        var pressed = false
        var body: Shader? = null
        var gloss: Shader? = null
    }

    var listener: Listener? = null
    var kbTheme: KbTheme = KbThemes.byId("pinkheart")
    var style: String = "3d"
    var opacity: Int = 80
    var rowH: Float = 0f
    var showHints = true
    var showPreview = true
    var customKey: Bitmap? = null
    var lang: KbLang = KbLayouts.byId("en")
    var mode: Int = M_LETTERS
    var shift: Int = 0
    var enterKind: String = "enter"

    private var rows: List<List<Key>> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val active = HashMap<Int, Key>()

    private var altKey: Key? = null
    private var altList: List<String> = emptyList()
    private var altSel = 0
    private var altPointer = -1
    private val altRect = RectF()
    private var altCellW = 0f

    private var lastShiftTap = 0L
    private var longRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val tmp = RectF()
    private val tmp2 = RectF()

    init {
        rowH = dp(50f)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun loc(): Locale = Locale(lang.locale)

    // ------------------------------------------------------------------ colors
    private fun withOp(c: Int, extra: Float = 1f): Int {
        val a = (Color.alpha(c) * opacity / 100f * extra).toInt().coerceIn(0, 255)
        return (c and 0x00FFFFFF) or (a shl 24)
    }

    private fun lighten(c: Int, f: Float): Int {
        val r = (Color.red(c) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * f).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(c), r, g, b)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1 - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1 - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        return Color.argb(Color.alpha(a), r, g, bl)
    }

    private fun baseColor(k: Key): Int = when (k.type) {
        T_ENTER -> kbTheme.accent
        T_CHAR, T_SPACE -> kbTheme.keyFill
        else -> kbTheme.specialFill
    }

    // ------------------------------------------------------------------ public API
    fun reload() {
        rows = buildRows()
        requestLayout()
        layoutKeys()
        invalidate()
    }

    fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
        longRunnable = null
        repeatRunnable = null
        for (k in active.values) k.pressed = false
        active.clear()
        altKey = null
        altPointer = -1
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ layout building
    private fun mk(label: String, type: Int, w: Float): Key {
        val k = Key(label, type, w)
        if (type == T_CHAR && label.isNotEmpty()) {
            val t = Character.getType(label.codePointAt(0))
            if (t == Character.NON_SPACING_MARK.toInt() ||
                t == Character.COMBINING_SPACING_MARK.toInt() ||
                t == Character.ENCLOSING_MARK.toInt()
            ) k.display = "\u25CC" + label
        }
        return k
    }

    private fun symRow(s: String): List<Key> = s.split(" ").filter { it.isNotEmpty() }.map { mk(it, T_CHAR, 1f) }

    private fun buildRows(): List<List<Key>> {
        val out = ArrayList<List<Key>>()
        if (mode == M_LETTERS) {
            val l = loc()
            val base: List<List<String>> =
                if (shift != 0) (lang.shift ?: lang.rows.map { r -> r.map { it.uppercase(l) } }) else lang.rows
            for ((ri, r) in base.withIndex()) {
                val row = ArrayList<Key>()
                if (ri == 2) row.add(mk("shift", T_SHIFT, 1.35f))
                for ((ci, s) in r.withIndex()) {
                    val key = mk(s, T_CHAR, 1f)
                    if (ri == 0 && ci < 10) key.hint = "1234567890"[ci].toString()
                    row.add(key)
                }
                if (ri == 2) row.add(mk("del", T_DEL, 1.35f))
                out.add(row)
            }
        } else if (mode == M_SYM1) {
            val r0 = symRow("1 2 3 4 5 6 7 8 9 0")
            val r1 = symRow("@ # \$ _ & - + ( ) /")
            val r2 = ArrayList<Key>()
            r2.add(mk("=\\<", T_MODE2, 1.35f))
            r2.addAll(symRow("* \" ' : ; ! ?"))
            r2.add(mk("del", T_DEL, 1.35f))
            out.add(r0); out.add(r1); out.add(r2)
        } else {
            val r0 = symRow("~ ` | • √ π ÷ × ¶ ∆")
            val r1 = symRow("£ ¢ € ₹ ¥ ° = { } \\")
            val r2 = ArrayList<Key>()
            r2.add(mk("?123", T_MODE2, 1.35f))
            r2.addAll(symRow("% © ® ™ ✓ [ ]"))
            r2.add(mk("del", T_DEL, 1.35f))
            out.add(r0); out.add(r1); out.add(r2)
        }
        val bottom = ArrayList<Key>()
        if (mode == M_LETTERS) {
            bottom.add(mk("?123", T_SYM, 1.4f))
            bottom.add(mk("lang", T_LANG, 1f))
            bottom.add(mk(",", T_CHAR, 1f))
            bottom.add(mk(lang.name, T_SPACE, 4.2f))
        } else {
            bottom.add(mk("ABC", T_SYM, 1.4f))
            bottom.add(mk(",", T_CHAR, 1f))
            bottom.add(mk(lang.name, T_SPACE, 5.2f))
        }
        bottom.add(mk(".", T_CHAR, 1f))
        bottom.add(mk("enter", T_ENTER, 1.4f))
        out.add(bottom)
        return out
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (rowH * 4f + dp(10f)).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width <= 0 || rows.isEmpty()) return
        val padH = dp(3f)
        val padV = dp(5f)
        var maxW = 0f
        for (r in rows) {
            var t = 0f
            for (k in r) t += k.weight
            if (t > maxW) maxW = t
        }
        val unit = (width - 2f * padH) / maxW
        val gapX = dp(4f)
        val gapY = dp(6f)
        for ((ri, r) in rows.withIndex()) {
            var total = 0f
            for (k in r) total += k.weight
            val extra = maxW - total
            val ws = FloatArray(r.size) { r[it].weight }
            var x0 = padH
            if (extra > 0.01f) {
                val last = ri == rows.size - 1
                val hasEdge = r.any { it.type == T_SHIFT || it.type == T_DEL || it.type == T_MODE2 }
                if (last) {
                    for (i in r.indices) if (r[i].type == T_SPACE) ws[i] += extra
                } else if (hasEdge) {
                    ws[0] += extra / 2f
                    ws[ws.size - 1] += extra / 2f
                } else {
                    x0 = padH + extra * unit / 2f
                }
            }
            var x = x0
            val top = padV + ri * rowH
            for ((i, k) in r.withIndex()) {
                val w = ws[i] * unit
                k.cell.set(x, top, x + w, top + rowH)
                k.rect.set(x + gapX / 2f, top + gapY / 2f, x + w - gapX / 2f, top + rowH - gapY / 2f)
                x += w
                buildShaders(k)
            }
        }
    }

    private fun buildShaders(k: Key) {
        k.body = null
        k.gloss = null
        val r = k.rect
        if (r.width() <= 0f) return
        val b = baseColor(k)
        when (style) {
            "3d" -> {
                k.body = LinearGradient(
                    r.left, r.top, r.left, r.bottom,
                    intArrayOf(withOp(lighten(b, 1.18f)), withOp(b), withOp(lighten(b, 0.84f))),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                k.gloss = LinearGradient(
                    r.left, r.top, r.left, r.top + r.height() * 0.5f,
                    0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
                )
            }
            "glass" -> {
                k.gloss = LinearGradient(
                    r.left, r.top, r.left, r.top + r.height() * 0.55f,
                    0x70FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
                )
            }
            "bubble" -> {
                val rad = maxOf(r.width(), r.height()) * 0.75f
                k.body = RadialGradient(
                    r.centerX() - r.width() * 0.15f, r.centerY() - r.height() * 0.25f, rad,
                    intArrayOf(withOp(blend(b, Color.WHITE, 0.65f)), withOp(b), withOp(lighten(b, 0.78f))),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
            }
        }
    }

    // ------------------------------------------------------------------ drawing
    override fun onDraw(canvas: Canvas) {
        for (r in rows) for (k in r) drawKey(canvas, k)
        if (altKey != null) {
            drawAltPopup(canvas)
        } else if (showPreview) {
            for (k in active.values) if (k.type == T_CHAR) drawPreview(canvas, k)
        }
    }

    private fun drawKey(c: Canvas, k: Key) {
        val r = tmp
        r.set(k.rect)
        val base = baseColor(k)
        val rad = when (style) {
            "bubble" -> minOf(r.width(), r.height()) / 2f
            "flat" -> dp(6f)
            else -> dp(10f)
        }
        val cb = customKey
        if (style == "png" && cb != null) {
            bmpPaint.alpha = (255 * opacity / 100).coerceIn(40, 255)
            c.drawBitmap(cb, null, r, bmpPaint)
            if (k.pressed) {
                paint.shader = null
                paint.color = withOp(kbTheme.accent, 0.5f)
                c.drawRoundRect(r, rad, rad, paint)
            }
        } else {
            if (k.pressed) r.offset(0f, dp(1.2f))
            when (style) {
                "glass" -> {
                    paint.shader = null
                    paint.color = withOp(if (k.pressed) blend(base, kbTheme.accent, 0.45f) else base, 0.8f)
                    c.drawRoundRect(r, rad, rad, paint)
                    strokePaint.color = Color.argb((150 * opacity / 100).coerceIn(0, 255), 255, 255, 255)
                    c.drawRoundRect(r, rad, rad, strokePaint)
                    val g = k.gloss
                    if (g != null && !k.pressed) {
                        paint.shader = g
                        paint.color = Color.WHITE
                        tmp2.set(r.left + 1f, r.top + 1f, r.right - 1f, r.top + r.height() * 0.55f)
                        c.drawRoundRect(tmp2, rad, rad, paint)
                        paint.shader = null
                    }
                }
                "flat" -> {
                    paint.shader = null
                    paint.color = withOp(if (k.pressed) blend(base, kbTheme.accent, 0.45f) else base)
                    c.drawRoundRect(r, rad, rad, paint)
                }
                "bubble" -> {
                    if (!k.pressed) {
                        paint.shader = null
                        paint.color = Color.argb((70 * opacity / 100).coerceIn(0, 255), 0, 0, 0)
                        tmp2.set(r)
                        tmp2.offset(0f, dp(2f))
                        c.drawRoundRect(tmp2, rad, rad, paint)
                    }
                    val bd = k.body
                    if (k.pressed || bd == null) {
                        paint.shader = null
                        paint.color = withOp(blend(base, kbTheme.accent, 0.45f))
                    } else {
                        paint.shader = bd
                        paint.color = Color.WHITE
                    }
                    c.drawRoundRect(r, rad, rad, paint)
                    paint.shader = null
                    strokePaint.color = Color.argb((110 * opacity / 100).coerceIn(0, 255), 255, 255, 255)
                    c.drawRoundRect(r, rad, rad, strokePaint)
                }
                else -> { // 3d
                    if (!k.pressed) {
                        paint.shader = null
                        paint.color = Color.argb((95 * opacity / 100).coerceIn(0, 255), 0, 0, 0)
                        tmp2.set(r)
                        tmp2.offset(0f, dp(2.4f))
                        c.drawRoundRect(tmp2, rad, rad, paint)
                    }
                    val bd = k.body
                    if (k.pressed || bd == null) {
                        paint.shader = null
                        paint.color = withOp(lighten(base, 0.78f))
                    } else {
                        paint.shader = bd
                        paint.color = Color.WHITE
                    }
                    c.drawRoundRect(r, rad, rad, paint)
                    paint.shader = null
                    strokePaint.color = Color.argb((120 * opacity / 100).coerceIn(0, 255), 255, 255, 255)
                    c.drawRoundRect(r, rad, rad, strokePaint)
                    val g = k.gloss
                    if (g != null && !k.pressed) {
                        paint.shader = g
                        paint.color = Color.WHITE
                        tmp2.set(r.left + 1f, r.top + 1f, r.right - 1f, r.top + r.height() * 0.5f)
                        c.drawRoundRect(tmp2, rad, rad, paint)
                        paint.shader = null
                    }
                }
            }
        }
        drawContent(c, k, r)
    }

    private fun drawLabel(
        c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, maxW: Float, bold: Boolean = false
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.isFakeBoldText = bold
        val w = textPaint.measureText(s)
        if (w > maxW && w > 0f) textPaint.textSize = size * maxW / w
        val fm = textPaint.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
        textPaint.isFakeBoldText = false
    }

    private fun drawContent(c: Canvas, k: Key, r: RectF) {
        val tc = if (k.type == T_ENTER) Color.WHITE else kbTheme.text
        val cx = r.centerX()
        val cy = r.centerY()
        val maxW = r.width() * 0.9f
        when (k.type) {
            T_CHAR -> {
                drawLabel(c, k.display, cx, cy, rowH * 0.40f, tc, maxW)
                val h = k.hint
                if (h != null && showHints && mode == M_LETTERS) {
                    hintPaint.textSize = rowH * 0.2f
                    hintPaint.color = kbTheme.hint
                    c.drawText(h, r.right - dp(4f), r.top + rowH * 0.27f, hintPaint)
                }
            }
            T_SYM, T_MODE2 -> drawLabel(c, k.label, cx, cy, rowH * 0.28f, tc, maxW, true)
            T_SPACE -> drawLabel(
                c, k.label, cx, cy, rowH * 0.27f,
                (kbTheme.text and 0x00FFFFFF) or (0xB0 shl 24), maxW
            )
            T_SHIFT -> drawShift(c, cx, cy, if (shift != 0) kbTheme.accent else tc)
            T_DEL -> drawDel(c, cx, cy, tc)
            T_LANG -> drawGlobe(c, cx, cy, tc)
            T_ENTER -> drawEnter(c, cx, cy, tc)
        }
    }

    private fun drawShift(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = rowH * 0.17f
        iconPaint.color = color
        iconPaint.strokeWidth = dp(2f)
        iconPaint.style = if (shift != 0) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        path.reset()
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s, cy)
        path.lineTo(cx + s * 0.45f, cy)
        path.lineTo(cx + s * 0.45f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.45f, cy)
        path.lineTo(cx - s, cy)
        path.close()
        c.drawPath(path, iconPaint)
        if (shift == 2) {
            iconPaint.style = Paint.Style.STROKE
            c.drawLine(cx - s * 0.45f, cy + s * 1.35f, cx + s * 0.45f, cy + s * 1.35f, iconPaint)
        }
    }

    private fun drawDel(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = rowH * 0.18f
        iconPaint.color = color
        iconPaint.strokeWidth = dp(2f)
        iconPaint.style = Paint.Style.STROKE
        path.reset()
        path.moveTo(cx - s * 1.25f, cy)
        path.lineTo(cx - s * 0.45f, cy - s * 0.85f)
        path.lineTo(cx + s * 1.25f, cy - s * 0.85f)
        path.lineTo(cx + s * 1.25f, cy + s * 0.85f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.85f)
        path.close()
        c.drawPath(path, iconPaint)
        c.drawLine(cx - s * 0.1f, cy - s * 0.4f, cx + s * 0.75f, cy + s * 0.4f, iconPaint)
        c.drawLine(cx - s * 0.1f, cy + s * 0.4f, cx + s * 0.75f, cy - s * 0.4f, iconPaint)
    }

    private fun drawGlobe(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = rowH * 0.2f
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.8f)
        iconPaint.style = Paint.Style.STROKE
        c.drawCircle(cx, cy, s, iconPaint)
        tmp2.set(cx - s * 0.45f, cy - s, cx + s * 0.45f, cy + s)
        c.drawOval(tmp2, iconPaint)
        c.drawLine(cx - s, cy, cx + s, cy, iconPaint)
    }

    private fun drawEnter(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = rowH * 0.18f
        iconPaint.color = color
        iconPaint.strokeWidth = dp(2.2f)
        iconPaint.style = Paint.Style.STROKE
        when (enterKind) {
            "go" -> drawLabel(c, "Go", cx, cy, rowH * 0.32f, color, rowH * 1.2f, true)
            "search" -> {
                c.drawCircle(cx - s * 0.2f, cy - s * 0.2f, s * 0.7f, iconPaint)
                c.drawLine(cx + s * 0.3f, cy + s * 0.3f, cx + s * 1.0f, cy + s * 1.0f, iconPaint)
            }
            "send" -> {
                path.reset()
                path.moveTo(cx - s, cy - s * 0.85f)
                path.lineTo(cx + s * 1.1f, cy)
                path.lineTo(cx - s, cy + s * 0.85f)
                path.lineTo(cx - s * 0.5f, cy)
                path.close()
                iconPaint.style = Paint.Style.FILL_AND_STROKE
                c.drawPath(path, iconPaint)
            }
            "next" -> {
                c.drawLine(cx - s, cy, cx + s, cy, iconPaint)
                c.drawLine(cx + s * 0.3f, cy - s * 0.7f, cx + s, cy, iconPaint)
                c.drawLine(cx + s * 0.3f, cy + s * 0.7f, cx + s, cy, iconPaint)
            }
            "done" -> {
                path.reset()
                path.moveTo(cx - s, cy)
                path.lineTo(cx - s * 0.3f, cy + s * 0.75f)
                path.lineTo(cx + s, cy - s * 0.75f)
                c.drawPath(path, iconPaint)
            }
            else -> {
                path.reset()
                path.moveTo(cx + s * 0.9f, cy - s * 0.8f)
                path.lineTo(cx + s * 0.9f, cy + s * 0.2f)
                path.lineTo(cx - s * 0.9f, cy + s * 0.2f)
                c.drawPath(path, iconPaint)
                c.drawLine(cx - s * 0.9f, cy + s * 0.2f, cx - s * 0.2f, cy - s * 0.5f, iconPaint)
                c.drawLine(cx - s * 0.9f, cy + s * 0.2f, cx - s * 0.2f, cy + s * 0.9f, iconPaint)
            }
        }
    }

    private fun bubbleColor(): Int = if (kbTheme.dark) 0xFF2B2B36.toInt() else Color.WHITE

    private fun drawPreview(c: Canvas, k: Key) {
        val w = maxOf(k.rect.width() * 1.3f, dp(46f))
        val h = rowH * 1.15f
        var left = k.rect.centerX() - w / 2f
        left = left.coerceIn(0f, maxOf(0f, width - w))
        val bottom = k.rect.top - dp(2f)
        tmp.set(left, bottom - h, left + w, bottom)
        paint.shader = null
        paint.color = bubbleColor()
        c.drawRoundRect(tmp, dp(12f), dp(12f), paint)
        strokePaint.color = kbTheme.accent
        c.drawRoundRect(tmp, dp(12f), dp(12f), strokePaint)
        drawLabel(c, k.display, tmp.centerX(), tmp.centerY(), rowH * 0.62f, kbTheme.text, w * 0.85f)
    }

    private fun drawAltPopup(c: Canvas) {
        paint.shader = null
        paint.color = bubbleColor()
        c.drawRoundRect(altRect, dp(12f), dp(12f), paint)
        strokePaint.color = kbTheme.accent
        c.drawRoundRect(altRect, dp(12f), dp(12f), strokePaint)
        for ((i, s) in altList.withIndex()) {
            val l = altRect.left + i * altCellW
            if (i == altSel) {
                paint.color = kbTheme.accent
                tmp.set(l + dp(3f), altRect.top + dp(3f), l + altCellW - dp(3f), altRect.bottom - dp(3f))
                c.drawRoundRect(tmp, dp(9f), dp(9f), paint)
            }
            drawLabel(
                c, s, l + altCellW / 2f, altRect.centerY(), rowH * 0.5f,
                if (i == altSel) Color.WHITE else kbTheme.text, altCellW * 0.85f
            )
        }
    }

    // ------------------------------------------------------------------ touch
    private fun keyAt(x: Float, y: Float): Key? {
        if (rows.isEmpty()) return null
        val ri = ((y - dp(5f)) / rowH).toInt().coerceIn(0, rows.size - 1)
        val row = rows[ri]
        for (k in row) if (x < k.cell.right) return k
        return row[row.size - 1]
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val k = keyAt(e.getX(i), e.getY(i))
                if (k != null) down(e.getPointerId(i), k)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun down(id: Int, k: Key) {
        k.pressed = true
        active[id] = k
        listener?.onKeyFeedback()
        when (k.type) {
            T_DEL -> {
                listener?.onDelete()
                startRepeat(k)
            }
            T_CHAR, T_LANG -> scheduleLong(id, k)
        }
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        if (id == altPointer && altKey != null) {
            if (altCellW > 0f) {
                val idx = ((x - altRect.left) / altCellW).toInt().coerceIn(0, altList.size - 1)
                if (idx != altSel) {
                    altSel = idx
                    invalidate()
                }
            }
            return
        }
        val k = active[id] ?: return
        val nk = keyAt(x, y)
        if (nk != null && nk !== k) {
            k.pressed = false
            if (k.type == T_DEL) stopRepeat()
            cancelLong()
            active.remove(id)
            if (nk.type != T_DEL) down(id, nk)
            invalidate()
        }
    }

    private fun up(id: Int) {
        val k = active.remove(id) ?: return
        k.pressed = false
        cancelLong()
        if (altKey != null && id == altPointer) {
            val s = altList.getOrNull(altSel)
            altKey = null
            altPointer = -1
            invalidate()
            if (s != null) commitChar(s)
            return
        }
        when (k.type) {
            T_CHAR -> commitChar(k.label)
            T_SPACE -> listener?.onText(" ")
            T_ENTER -> listener?.onEnter()
            T_SHIFT -> handleShift()
            T_SYM -> {
                mode = if (mode == M_LETTERS) M_SYM1 else M_LETTERS
                reload()
            }
            T_MODE2 -> {
                mode = if (mode == M_SYM1) M_SYM2 else M_SYM1
                reload()
            }
            T_LANG -> listener?.onLangNext()
            T_DEL -> stopRepeat()
        }
        invalidate()
    }

    private fun commitChar(s: String) {
        if (shift == 1 && mode == M_LETTERS) {
            shift = 0
            reload()
        }
        listener?.onText(s)
    }

    private fun handleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == 2 -> 0
            shift == 1 && now - lastShiftTap < 400L -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
        reload()
    }

    private fun altsFor(k: Key): List<String> {
        val out = ArrayList<String>()
        k.hint?.let { out.add(it) }
        if (k.type == T_CHAR) {
            val l = loc()
            val isLetter = k.label.length == 1 && k.label[0].isLetter() && lang.latin
            if (isLetter) {
                val base = ALTS[k.label.lowercase(Locale.ROOT)]
                if (base != null) {
                    if (shift != 0) base.forEach { out.add(it.uppercase(l)) } else out.addAll(base)
                }
            } else {
                val base = ALTS[k.label]
                if (base != null) out.addAll(base)
            }
            if (k.label == "." && lang.id in INDIC) out.add(0, "।")
            if (k.label == "," && lang.id in RTL) {
                out.add(0, "،")
                out.add(1, "؟")
                out.add(2, "؛")
            }
        }
        return out
    }

    private fun scheduleLong(id: Int, k: Key) {
        cancelLong()
        val r = Runnable {
            if (active[id] === k) {
                if (k.type == T_LANG) {
                    k.pressed = false
                    active.remove(id)
                    listener?.onLangPicker()
                    invalidate()
                } else {
                    val a = altsFor(k)
                    if (a.isNotEmpty()) {
                        altKey = k
                        altList = a
                        altSel = 0
                        altPointer = id
                        val cw = maxOf(k.rect.width(), dp(40f))
                        val total = cw * a.size
                        var left = k.rect.centerX() - total / 2f
                        left = left.coerceIn(0f, maxOf(0f, width - total))
                        altCellW = cw
                        altRect.set(left, k.rect.top - rowH - dp(6f), left + total, k.rect.top - dp(6f))
                        listener?.onKeyFeedback()
                        invalidate()
                    }
                }
            }
        }
        longRunnable = r
        handler.postDelayed(r, 340L)
    }

    private fun cancelLong() {
        longRunnable?.let { handler.removeCallbacks(it) }
        longRunnable = null
    }

    private fun startRepeat(k: Key) {
        stopRepeat()
        val r = object : Runnable {
            override fun run() {
                if (!k.pressed) return
                listener?.onDelete()
                handler.postDelayed(this, 55L)
            }
        }
        repeatRunnable = r
        handler.postDelayed(r, 380L)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }
}

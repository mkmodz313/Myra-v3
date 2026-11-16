package com.myra.assistant.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable

/** Shared look for every button around the keys (toolbar, suggestions, panels): same shiny 3D design as the keys. */
object KbShiny {
    @JvmField var style: String = "jewel"

    fun make(
        c: Context, th: KbTheme, base: Int = th.keyFill, radiusDp: Float = 12f,
        selected: Boolean = false, insetDp: Float = 0f
    ): KbShinyDrawable = KbShinyDrawable(th, style, base, radiusDp, c.resources.displayMetrics.density, selected, insetDp)
}

class KbShinyDrawable(
    private val th: KbTheme,
    private val style: String,
    private val base: Int,
    private val radiusDp: Float,
    private val d: Float,
    private val selected: Boolean,
    private val insetDp: Float
) : Drawable() {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
    private val r = RectF()
    private val t = RectF()
    private val path = Path()
    private val glintPath = Path()

    private val darkFace = style == "neon" || style == "rainbow" || style == "glossy"
    private val accent = th.accent or (0xFF shl 24)

    private fun blend(a: Int, b: Int, k: Float): Int {
        val rr = (Color.red(a) * (1 - k) + Color.red(b) * k).toInt()
        val gg = (Color.green(a) * (1 - k) + Color.green(b) * k).toInt()
        val bb = (Color.blue(a) * (1 - k) + Color.blue(b) * k).toInt()
        return Color.argb(255, rr, gg, bb)
    }

    private val faceMid: Int =
        if (selected) accent
        else if (darkFace) blend(base, Color.BLACK, if (th.dark) 0.30f else 0.62f)
        else blend(base, Color.WHITE, 0.22f)

    private fun lum(c: Int): Float =
        (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    /** Text colour that is readable on this button. */
    fun textColor(): Int = if (lum(faceMid) > 0.58f) 0xFF22222A.toInt() else Color.WHITE

    private fun hueColor(h: Float): Int =
        Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, 0.82f, 1f))

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val ins = insetDp * d
        r.set(b.left + d * 1.6f + ins, b.top + d * 1.2f + ins, b.right - d * 1.6f - ins, b.bottom - d * 4.2f - ins)
        if (r.height() < d * 8f) r.set(b.left + d, b.top + d, b.right - d, b.bottom - d)
        val rad = minOf(radiusDp * d, r.height() / 2f)

        // soft shadow + thick side wall
        p.shader = null
        val tint = blend(accent, Color.BLACK, 0.65f)
        p.color = Color.argb(55, Color.red(tint), Color.green(tint), Color.blue(tint))
        t.set(r); t.offset(0f, d * 3.2f)
        canvas.drawRoundRect(t, rad, rad, p)
        p.color = blend(blend(faceMid, Color.BLACK, 0.3f), accent, 0.35f)
        t.set(r); t.offset(0f, d * 2.5f)
        canvas.drawRoundRect(t, rad, rad, p)

        // face
        val top = if (selected) blend(accent, Color.WHITE, 0.35f) else if (darkFace) blend(faceMid, Color.WHITE, 0.20f) else blend(base, Color.WHITE, 0.62f)
        val bottom = blend(faceMid, Color.BLACK, if (darkFace) 0.35f else 0.20f)
        p.shader = LinearGradient(r.left, r.top, r.left, r.bottom, intArrayOf(top, faceMid, bottom), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, rad, rad, p)
        p.shader = null

        // rim (colour border)
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        val rim: Shader = when (style) {
            "rainbow" -> SweepGradient(
                r.centerX(), r.centerY(),
                intArrayOf(0xFFFF2BD6.toInt(), 0xFFFF7A18.toInt(), 0xFFFFE600.toInt(), 0xFF2BFF6A.toInt(), 0xFF1ED8FF.toInt(), 0xFF3D6BFF.toInt(), 0xFF9B3DFF.toInt(), 0xFFFF2BD6.toInt()),
                null
            )
            "neon" -> SweepGradient(
                r.centerX(), r.centerY(),
                intArrayOf(hueColor(hsv[0]), hueColor(hsv[0] + 55f), hueColor(hsv[0] + 115f), hueColor(hsv[0] + 55f), hueColor(hsv[0])),
                null
            )
            else -> LinearGradient(
                r.left, r.top, r.left, r.bottom,
                blend(accent, Color.WHITE, 0.6f), blend(accent, Color.BLACK, 0.35f), Shader.TileMode.CLAMP
            )
        }
        sp.shader = rim
        sp.color = Color.WHITE
        if (style == "neon" || style == "rainbow") {
            sp.strokeWidth = d * 6f; sp.alpha = 45
            canvas.drawRoundRect(r, rad, rad, sp)
            sp.strokeWidth = d * 3.6f; sp.alpha = 95
            canvas.drawRoundRect(r, rad, rad, sp)
        }
        sp.alpha = 255
        sp.strokeWidth = d * (if (selected) 2.6f else 2f)
        t.set(r); t.inset(d, d)
        canvas.drawRoundRect(t, rad - d, rad - d, sp)
        sp.shader = null
        if (selected) {
            sp.strokeWidth = d * 1.4f
            sp.color = Color.WHITE
            canvas.drawRoundRect(r, rad, rad, sp)
        }

        // gloss on the upper half
        p.shader = LinearGradient(r.left, r.top, r.left, r.top + r.height() * 0.5f, 0x88FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        t.set(r.left + d * 2.4f, r.top + d * 2.4f, r.right - d * 2.4f, r.top + r.height() * 0.5f)
        canvas.drawRoundRect(t, maxOf(1f, rad - d * 2f), maxOf(1f, rad - d * 2f), p)
        p.shader = null
        sp.strokeWidth = d * 0.8f
        sp.color = Color.argb(100, 255, 255, 255)
        t.set(r); t.inset(d * 3f, d * 3f)
        canvas.drawRoundRect(t, maxOf(1f, rad - d * 3f), maxOf(1f, rad - d * 3f), sp)

        // pearls around jewel buttons
        if (style == "jewel" && r.width() > d * 26f) {
            path.reset()
            t.set(r); t.inset(d * 2.6f, d * 2.6f)
            path.addRoundRect(t, maxOf(1f, rad - d * 2.6f), maxOf(1f, rad - d * 2.6f), Path.Direction.CW)
            val pm = PathMeasure(path, true)
            val len = pm.length
            val n = maxOf(8, (len / (d * 4.6f)).toInt())
            val pos = FloatArray(2)
            for (i in 0 until n) {
                pm.getPosTan(len * i / n, pos, null)
                p.color = Color.argb(70, 0, 0, 0)
                canvas.drawCircle(pos[0] + d * 0.5f, pos[1] + d * 0.7f, d * 1.4f, p)
                p.color = Color.argb(245, 255, 255, 255)
                canvas.drawCircle(pos[0], pos[1], d * 1.35f, p)
            }
        }

        // star glints
        if (r.width() > d * 22f) {
            glint(canvas, r.left + d * 9f, r.top + d * 6f, d * 4.4f, 235)
            if (r.width() > d * 60f) glint(canvas, r.right - d * 10f, r.bottom - d * 6.5f, d * 3.2f, 170)
        }
    }

    private fun glint(c: Canvas, x: Float, y: Float, s: Float, a: Int) {
        glintPath.reset()
        glintPath.moveTo(x, y - s)
        glintPath.quadTo(x + s * 0.12f, y - s * 0.12f, x + s, y)
        glintPath.quadTo(x + s * 0.12f, y + s * 0.12f, x, y + s)
        glintPath.quadTo(x - s * 0.12f, y + s * 0.12f, x - s, y)
        glintPath.quadTo(x - s * 0.12f, y - s * 0.12f, x, y - s)
        glintPath.close()
        p.shader = null
        p.color = Color.argb(a / 3, 255, 255, 255)
        c.drawCircle(x, y, s * 1.25f, p)
        p.color = Color.argb(a, 255, 255, 255)
        c.drawPath(glintPath, p)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

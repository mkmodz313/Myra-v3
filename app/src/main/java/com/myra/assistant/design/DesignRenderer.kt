package com.myra.assistant.design

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.sin

/** Draws the background art of a design (shared by the full screen and the small previews). */
object DesignRenderer {

    private fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    fun drawBackground(c: Canvas, d: AppDesign, w: Float, h: Float, density: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h, d.bgTop, d.bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        when (d.bgStyle) {
            1 -> { // gold / colour streaks
                for (i in 0 until 3) {
                    val path = Path()
                    val y0 = h * (0.30f + i * 0.24f)
                    val th = h * 0.055f
                    path.moveTo(-0.2f * w, y0 + th)
                    path.lineTo(1.2f * w, y0 - h * 0.22f)
                    path.lineTo(1.2f * w, y0 - h * 0.22f + th)
                    path.lineTo(-0.2f * w, y0 + th * 2)
                    path.close()
                    p.shader = LinearGradient(0f, 0f, w, 0f,
                        intArrayOf(alpha(d.accent, 0), alpha(if (i == 1) d.accent2 else d.accent, 150), alpha(d.accent, 0)),
                        null, Shader.TileMode.CLAMP)
                    c.drawPath(path, p)
                }
                p.shader = RadialGradient(w * 0.1f, h * 0.05f, w * 0.8f, alpha(d.accent2, 120), alpha(d.accent2, 0), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, p)
                p.shader = null
                sparkles(c, d, w, h, density)
            }
            2 -> { // neon glow corners
                p.shader = RadialGradient(0f, 0f, w * 0.95f, alpha(d.accent, 120), alpha(d.accent, 0), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, p)
                p.shader = RadialGradient(w, h * 0.7f, w * 0.9f, alpha(d.accent2, 110), alpha(d.accent2, 0), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, p)
                p.shader = null
                sparkles(c, d, w, h, density)
            }
            3 -> { // soft blobs (iOS wallpaper)
                val blobs = listOf(
                    Triple(0.15f, 0.18f, d.accent), Triple(0.92f, 0.34f, d.accent2),
                    Triple(0.25f, 0.78f, d.accent2), Triple(0.88f, 0.95f, d.accent)
                )
                blobs.forEach { (fx, fy, col) ->
                    p.shader = RadialGradient(w * fx, h * fy, w * 0.75f, alpha(col, 150), alpha(col, 0), Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, w, h, p)
                }
                p.shader = null
            }
            4 -> { // waves
                for (k in 0 until 3) {
                    val path = Path()
                    val base = h * (0.55f + k * 0.14f)
                    path.moveTo(0f, h)
                    var x = 0f
                    while (x <= w + 8) {
                        path.lineTo(x, base + sin((x / w) * 6.28f * (1f + k * 0.4f) + k) * h * 0.035f)
                        x += 8f
                    }
                    path.lineTo(w, h)
                    path.close()
                    p.shader = LinearGradient(0f, base, 0f, h, alpha(if (k % 2 == 0) d.accent else d.accent2, 90), alpha(d.accent, 10), Shader.TileMode.CLAMP)
                    c.drawPath(path, p)
                }
                p.shader = null
            }
            5 -> { // dot grid
                p.color = alpha(d.accent, 38)
                val step = 22f * density
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) { c.drawCircle(x, y, 1.3f * density, p); x += step }
                    y += step
                }
            }
        }
    }

    private fun sparkles(c: Canvas, d: AppDesign, w: Float, h: Float, density: Float) {
        val rnd = Random(d.id.hashCode().toLong())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(d.accent, 200); strokeWidth = 1.2f * density }
        repeat(16) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = (2 + rnd.nextInt(4)) * density
            c.drawLine(x - r, y, x + r, y, p)
            c.drawLine(x, y - r, x, y + r, p)
        }
    }
}

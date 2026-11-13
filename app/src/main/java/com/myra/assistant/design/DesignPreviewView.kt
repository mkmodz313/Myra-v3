package com.myra.assistant.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.LruCache
import android.view.View

/** Mini picture of a design (background, header, search bar, icons, dock) - used in the designs gallery. */
class DesignPreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var design: AppDesign = AppDesigns.ALL[0]
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val palette = intArrayOf(
        Color.parseColor("#4C6BFF"), Color.parseColor("#FF5B7F"), Color.parseColor("#35D6A0"), Color.parseColor("#FFB02E"),
        Color.parseColor("#8E4CFF"), Color.parseColor("#2EC4FF"), Color.parseColor("#FF7A3D"), Color.parseColor("#E4E8F2")
    )

    private fun art(res: Int): Bitmap? {
        cache.get(res)?.let { return it }
        val b = try { BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = 2 }) } catch (_: Throwable) { null }
        if (b != null) cache.put(res, b)
        return b
    }

    override fun onDraw(c: Canvas) {
        val d = design
        val w = width.toFloat()
        val h = height.toFloat()
        val dm = resources.displayMetrics.density
        DesignRenderer.drawBackground(c, d, w, h, dm)

        val pad = w * 0.07f
        var y = h * 0.03f

        // header
        val bmp = if (d.art != 0) art(d.art) else null
        if (bmp != null) {
            val tw = w - pad
            val th = tw * bmp.height / bmp.width
            rect.set(pad / 2, y, pad / 2 + tw, y + th)
            c.drawBitmap(bmp, null, rect, null)
            y += th + h * 0.012f
        } else {
            p.shader = null
            p.color = d.accent
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.058f
            p.isFakeBoldText = true
            p.setShadowLayer(8 * dm, 0f, 0f, d.accent)
            c.drawText("Apps Box", w / 2, y + h * 0.065f, p)
            p.clearShadowLayer()
            p.isFakeBoldText = false
            p.color = d.textColor
            p.alpha = 170
            p.textSize = h * 0.026f
            c.drawText(d.category + " design", w / 2, y + h * 0.105f, p)
            p.alpha = 255
            y += h * 0.13f
        }

        // search bar
        val sh = h * 0.07f
        rect.set(pad, y, w - pad, y + sh)
        DesignStyle.searchBar(d, dm).also { it.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt()); it.draw(c) }
        p.color = d.textColor; p.alpha = 110
        rect.set(pad + sh * 0.9f, y + sh * 0.4f, pad + sh * 0.9f + w * 0.3f, y + sh * 0.62f)
        c.drawRoundRect(rect, 8f, 8f, p)
        p.alpha = 255
        y += sh + h * 0.025f

        // icons
        val dockH = h * 0.085f
        val dockTop = h - dockH - h * 0.03f
        val cols = 4
        val rows = 4
        val areaH = dockTop - y - h * 0.015f
        val cellW = (w - pad * 2) / cols
        val cellH = areaH / rows
        val size = minOf(cellW * 0.66f, cellH * 0.62f)
        for (r in 0 until rows) for (col in 0 until cols) {
            val cx = pad + cellW * col + cellW / 2
            val top = y + cellH * r + cellH * 0.04f
            val ico = RectF(cx - size / 2, top, cx + size / 2, top + size)
            val frame = DesignStyle.iconFrame(d, size.toInt(), dm)
            frame.setBounds(ico.left.toInt(), ico.top.toInt(), ico.right.toInt(), ico.bottom.toInt()); frame.draw(c)
            p.color = palette[(r * cols + col) % palette.size]
            val inset = size * (if (d.frameStyle == 0) 0.0f else 0.16f)
            val rr = d.cornerFraction * (size - inset * 2)
            rect.set(ico.left + inset, ico.top + inset, ico.right - inset, ico.bottom - inset)
            c.drawRoundRect(rect, rr, rr, p)
            // label
            p.color = d.textColor; p.alpha = 190
            rect.set(cx - size * 0.38f, ico.bottom + size * 0.1f, cx + size * 0.38f, ico.bottom + size * 0.22f)
            c.drawRoundRect(rect, 6f, 6f, p)
            p.alpha = 255
        }

        // dock
        rect.set(pad * 0.7f, dockTop, w - pad * 0.7f, dockTop + dockH)
        DesignStyle.dock(d, dm).also { it.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt()); it.draw(c) }
        for (i in 0 until 5) {
            val cx = rect.left + rect.width() * (i + 0.5f) / 5
            val cy = rect.centerY()
            if (i == 2) { p.color = d.accent; c.drawCircle(cx, cy, dockH * 0.36f, p) }
            else { p.color = d.textColor; p.alpha = 190; c.drawCircle(cx, cy, dockH * 0.16f, p); p.alpha = 255 }
        }
    }

    companion object {
        private val cache = LruCache<Int, Bitmap>(6)
    }
}

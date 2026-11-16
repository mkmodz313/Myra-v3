package com.myra.assistant.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.widget.LinearLayout

/** Root of the keyboard window: paints the theme background (PNG/JPG image or gradient). */
class KbRootLayout(ctx: Context) : LinearLayout(ctx) {

    private var theme: KbTheme? = null
    private var bg: Bitmap? = null
    private var bgKey = ""
    private var grad: Shader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()

    init {
        setWillNotDraw(false)
    }

    fun setTheme(t: KbTheme) {
        theme = t
        reloadBg()
    }

    private fun reloadBg() {
        val t = theme ?: return
        if (width <= 0 || height <= 0) return
        val key = t.id + "|" + width + "x" + height + "|" + KbThemes.bgVersion(context, t)
        if (key == bgKey) {
            invalidate()
            return
        }
        bg = KbThemes.loadBg(context, t, width, height)
        bgKey = key
        grad = LinearGradient(0f, 0f, 0f, height.toFloat(), t.gradTop, t.gradBottom, Shader.TileMode.CLAMP)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reloadBg()
    }

    override fun onDraw(canvas: Canvas) {
        val t = theme
        val b = bg
        if (b != null) {
            dst.set(0, 0, width, height)
            canvas.drawBitmap(b, null, dst, paint)
        } else {
            val g = grad
            if (g != null) {
                paint.shader = g
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
        }
        super.onDraw(canvas)
    }
}

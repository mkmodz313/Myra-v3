package com.myra.assistant.design

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/** Builds the drawables of a design (icon frames, search bar, dock, label pills). */
object DesignStyle {

    fun isLight(d: AppDesign): Boolean {
        val t = d.textColor
        return (Color.red(t) + Color.green(t) + Color.blue(t)) / 3 < 110
    }

    private fun rect(fill: Int, radius: Float, stroke: Int = 0, strokePx: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(fill)
        if (strokePx > 0) setStroke(strokePx, stroke)
    }

    private fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    fun iconFrame(d: AppDesign, sizePx: Int, density: Float): Drawable {
        val r = d.cornerFraction * sizePx
        return when (d.frameStyle) {
            0 -> rect(d.iconBg, r)
            1 -> rect(d.iconBg, r, d.frameColor, (1.6f * density).toInt())
            2 -> LayerDrawable(arrayOf(
                rect(Color.TRANSPARENT, r + 3 * density, alpha(d.frameColor, 70), (5 * density).toInt()),
                rect(d.iconBg, r, d.frameColor, (2.2f * density).toInt())
            )).also { it.setLayerInset(0, 0, 0, 0, 0) }
            else -> LayerDrawable(arrayOf(
                rect(d.iconBg, r, d.frameColor, (2.4f * density).toInt()),
                rect(Color.TRANSPARENT, (r - 3 * density).coerceAtLeast(2f), d.accent, (1f * density).toInt())
            )).also { it.setLayerInset(1, (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt()) }
        }
    }

    fun searchBar(d: AppDesign, density: Float): Drawable {
        val light = isLight(d)
        val fill = if (light) Color.parseColor("#88FFFFFF") else Color.parseColor("#88000000")
        val stroke = if (d.frameStyle == 0) alpha(d.accent, 150) else d.frameColor
        return rect(fill, 28 * density, stroke, (1.8f * density).toInt())
    }

    fun dock(d: AppDesign, density: Float): Drawable {
        val light = isLight(d)
        return when (d.dockStyle) {
            0 -> rect(if (light) Color.parseColor("#88FFFFFF") else Color.parseColor("#33FFFFFF"), 32 * density,
                if (light) Color.parseColor("#66FFFFFF") else Color.parseColor("#55FFFFFF"), (1.2f * density).toInt())
            1 -> rect(if (light) Color.parseColor("#EEFFFFFF") else Color.parseColor("#F0141820"), 28 * density, alpha(d.accent, 120), (1.2f * density).toInt())
            else -> LayerDrawable(arrayOf(
                rect(Color.TRANSPARENT, 34 * density, alpha(d.accent, 60), (6 * density).toInt()),
                rect(Color.parseColor("#CC000000"), 32 * density, d.accent, (2f * density).toInt())
            ))
        }
    }

    fun labelPill(d: AppDesign, density: Float): Drawable =
        rect(Color.parseColor("#B0000000"), 10 * density, if (d.frameStyle == 0) alpha(d.accent, 120) else d.frameColor, (1f * density).toInt())

    fun primaryButton(d: AppDesign, density: Float): Drawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(d.accent, d.accent2)).apply { cornerRadius = 24 * density }
}

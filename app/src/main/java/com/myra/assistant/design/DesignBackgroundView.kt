package com.myra.assistant.design

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class DesignBackgroundView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var design: AppDesign = AppDesigns.ALL[0]
        set(v) { field = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        DesignRenderer.drawBackground(canvas, design, width.toFloat(), height.toFloat(), resources.displayMetrics.density)
    }
}

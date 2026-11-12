package com.myra.assistant.ui.avatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Full-screen, non-interactive ambient layer over the scene: drifting cherry-blossom petals,
 * twinkling stars and two flapping neon butterflies. Runs on the shared [AnimationClock] at ~30 fps
 * and only while started (Activity resumed) and visible.
 */
class AmbientFxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), AnimationClock.Listener {

    private val dp = resources.displayMetrics.density
    private var started = false
    private var lastNanos = 0L
    private var lastInvalidateNs = 0L
    private var t = 0f

    private class Petal(var x: Float, var y: Float, val size: Float, val fall: Float, val sway: Float, val phase: Float, val spin: Float)
    private class Star(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

    private val petals = ArrayList<Petal>()
    private val stars = ArrayList<Star>()
    private val petalPath = Path()
    private val wingPath = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        petals.clear(); stars.clear()
        val rnd = java.util.Random(7)
        repeat(14) {
            petals.add(Petal(
                x = rnd.nextFloat() * w, y = rnd.nextFloat() * h,
                size = (4f + rnd.nextFloat() * 5f) * dp,
                fall = (16f + rnd.nextFloat() * 28f) * dp,
                sway = (10f + rnd.nextFloat() * 22f) * dp,
                phase = rnd.nextFloat() * 6.28f,
                spin = 40f + rnd.nextFloat() * 90f
            ))
        }
        repeat(34) {
            stars.add(Star(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.62f, (0.8f + rnd.nextFloat() * 1.6f) * dp, rnd.nextFloat() * 6.28f, 1f + rnd.nextFloat() * 2.4f))
        }
    }

    fun start() {
        if (started) return
        started = true
        lastNanos = 0L
        AnimationClock.register(this)
    }

    fun stop() {
        if (!started) return
        started = false
        AnimationClock.unregister(this)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) AnimationClock.unregister(this)
        else if (started) AnimationClock.register(this)
    }

    override fun onFrame(frameTimeNanos: Long) {
        if (lastNanos == 0L) lastNanos = frameTimeNanos
        val dt = ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = frameTimeNanos
        t += dt
        val h = height.toFloat()
        val w = width.toFloat()
        for (p in petals) {
            p.y += p.fall * dt
            if (p.y > h + 20 * dp) { p.y = -20 * dp; p.x = (p.x + w * 0.37f) % w }
        }
        if (frameTimeNanos - lastInvalidateNs >= 33_000_000L) {
            lastInvalidateNs = frameTimeNanos
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        // stars
        paint.style = Paint.Style.FILL
        for (s in stars) {
            val a = 0.5f + 0.5f * sin(t * s.speed + s.phase)
            paint.color = Color.argb((40 + 190 * a).toInt(), 255, 190, 245)
            canvas.drawCircle(s.x, s.y, s.size * (0.6f + 0.6f * a), paint)
        }
        // petals
        for (p in petals) {
            val x = p.x + p.sway * sin(t * 0.8f + p.phase)
            canvas.save()
            canvas.translate(x, p.y)
            canvas.rotate(p.spin * t + p.phase * 20f)
            petalPath.rewind()
            petalPath.moveTo(0f, -p.size)
            petalPath.quadTo(p.size * 0.9f, -p.size * 0.2f, 0f, p.size)
            petalPath.quadTo(-p.size * 0.9f, -p.size * 0.2f, 0f, -p.size)
            paint.color = Color.argb(150, 255, 120, 205)
            canvas.drawPath(petalPath, paint)
            canvas.restore()
        }
        // butterflies drifting on lissajous paths (positions relative to screen size)
        val w = width.toFloat()
        val h = height.toFloat()
        drawButterfly(canvas, w * (0.20f + 0.05f * sin(t * 0.45f)), h * (0.215f + 0.022f * cos(t * 0.6f)), 34f * dp, t * 1.0f, -12f)
        drawButterfly(canvas, w * (0.86f + 0.04f * cos(t * 0.4f)), h * (0.555f + 0.022f * sin(t * 0.55f)), 30f * dp, t * 1.15f + 1f, 10f)
    }

    private fun drawButterfly(canvas: Canvas, cx: Float, cy: Float, size: Float, phase: Float, tilt: Float) {
        val flap = 0.35f + 0.65f * abs(cos(phase * 5f))
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)
        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.scale(side * flap, 1f)
            wingPath.rewind()
            // upper wing
            wingPath.moveTo(0f, -size * 0.05f)
            wingPath.cubicTo(size * 0.5f, -size * 1.0f, size * 1.15f, -size * 0.7f, size * 0.95f, -size * 0.1f)
            wingPath.cubicTo(size * 0.85f, size * 0.15f, size * 0.3f, size * 0.1f, 0f, 0f)
            // lower wing
            wingPath.moveTo(0f, 0f)
            wingPath.cubicTo(size * 0.7f, size * 0.15f, size * 0.85f, size * 0.75f, size * 0.4f, size * 0.7f)
            wingPath.cubicTo(size * 0.15f, size * 0.65f, 0f, size * 0.3f, 0f, 0f)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(70, 255, 90, 220)
            canvas.drawPath(wingPath, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1.2f, 1.6f * dp)
            paint.color = Color.argb(225, 255, 170, 240)
            canvas.drawPath(wingPath, paint)
            canvas.restore()
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * dp
        paint.color = Color.argb(230, 255, 200, 245)
        canvas.drawLine(0f, -size * 0.18f, 0f, size * 0.55f, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
    }
}

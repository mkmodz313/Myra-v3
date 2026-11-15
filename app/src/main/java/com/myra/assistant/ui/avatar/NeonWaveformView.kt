package com.myra.assistant.ui.avatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.myra.assistant.voice.LipSyncBus
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Neon audio visualiser. Shows the microphone level while MYRA listens and MYRA's real voice level
 * while she speaks. Part of the shared [AnimationClock]; idle cost is one cheap frame at ~30 fps.
 */
class NeonWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), AnimationClock.Listener {

    private val barCount = 41
    private val heights = FloatArray(barCount) { 0.06f }
    private val dp = resources.displayMetrics.density

    private var mode = AvatarMode.IDLE
    private var micLevel = 0f
    private var level = 0f
    private var started = false
    private var lastNanos = 0L
    private var t = 0f
    private var lastInvalidateNs = 0L
    private val tmp = FloatArray(2)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    fun setMode(newMode: AvatarMode) { mode = newMode }
    fun setMicLevel(l: Float) { micLevel = l.coerceIn(0f, 1f) }

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(Color.rgb(150, 40, 190), Color.rgb(255, 70, 215), Color.rgb(255, 190, 240), Color.rgb(255, 70, 215), Color.rgb(150, 40, 190)),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        barPaint.shader = shader
    }

    override fun onFrame(frameTimeNanos: Long) {
        if (lastNanos == 0L) lastNanos = frameTimeNanos
        val dt = ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = frameTimeNanos
        t += dt

        val target = when (mode) {
            AvatarMode.LISTENING -> micLevel
            AvatarMode.SPEAKING -> if (LipSyncBus.sample(tmp)) tmp[0] else 0f
            AvatarMode.THINKING -> 0.18f
            AvatarMode.IDLE -> 0f
        }
        level += (target - level) * (1f - exp(-dt / (if (target > level) 0.04f else 0.16f)))

        for (i in 0 until barCount) {
            val x = i / (barCount - 1f)
            val envelope = 0.35f + 0.65f * sin(Math.PI.toFloat() * x)           // taller in the middle
            val wob = 0.5f + 0.5f * sin(t * (5f + (i % 7)) + i * 0.8f)
            val idleWave = 0.05f + 0.03f * sin(t * 1.4f + i * 0.45f)
            val want = max(idleWave, level * envelope * (0.35f + 0.65f * wob))
            heights[i] += (want - heights[i]) * (1f - exp(-dt / 0.05f))
        }

        val interval = if (mode == AvatarMode.IDLE) 33_000_000L else 0L
        if (frameTimeNanos - lastInvalidateNs >= interval) {
            lastInvalidateNs = frameTimeNanos
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val slot = w / barCount
        val barW = min(slot * 0.5f, 5f * dp)
        val cy = h / 2f
        for (i in 0 until barCount) {
            val bh = max(barW, heights[i] * h)
            val cx = slot * (i + 0.5f)
            rect.set(cx - barW / 2f, cy - bh / 2f, cx + barW / 2f, cy + bh / 2f)
            // soft glow behind taller bars
            if (heights[i] > 0.2f) {
                glowPaint.color = Color.argb((abs(heights[i]) * 70).toInt().coerceIn(0, 80), 255, 60, 210)
                canvas.drawRoundRect(rect.left - barW, rect.top - barW * 0.6f, rect.right + barW, rect.bottom + barW * 0.6f, barW * 1.5f, barW * 1.5f, glowPaint)
            }
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, barPaint)
        }
    }
}

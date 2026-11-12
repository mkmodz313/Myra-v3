package com.myra.assistant.ui.avatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.myra.assistant.voice.LipSyncBus
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The circular "stage" of the home screen: neon-pink glowing frame, animated orbit rings, floating
 * sparkles and a depth halo drawn around (and partly behind/in front of) the [AvatarView].
 *
 * It is a square FrameLayout that hosts the avatar as its only child. The frame reacts to state and
 * audio level: microphone amplitude while listening, MYRA's real voice level while speaking.
 */
class AvatarStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), AnimationClock.Listener {

    val avatar = AvatarView(context)

    private val dp = resources.displayMetrics.density

    private var mode = AvatarMode.IDLE
    private var micLevel = 0f
    private var level = 0f            // smoothed visual level 0..1
    private var started = false
    private var lastNanos = 0L
    private var t = 0f
    private var lastInvalidateNs = 0L
    private val tmp = FloatArray(2)

    // orbit angles (degrees)
    private var orbitA = 0f
    private var orbitB = 120f
    private var orbitC = 250f
    private var comet = 0f
    private var thinkSweep = 0f
    private var speedMul = 0.35f

    private class Spark(
        var angle: Float, val radius: Float, val speed: Float,
        val size: Float, val phase: Float, val star: Boolean
    )

    private val sparks = List(30) { i ->
        Spark(
            angle = i * 12f + (i % 5) * 3f,
            radius = 1.04f + (i * 37 % 100) / 100f * 0.17f,
            speed = (if (i % 2 == 0) 1f else -1f) * (4f + (i * 13 % 10)),
            size = 1.1f + (i * 7 % 10) / 10f * 1.9f,
            phase = i * 0.9f,
            star = i % 3 == 0
        )
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    private val pink = Color.rgb(255, 64, 200)
    private val hot = Color.rgb(255, 120, 225)
    private val core = Color.rgb(255, 214, 246)

    init {
        setWillNotDraw(false)
        addView(avatar, LayoutParams(0, 0))
        clipChildren = false
    }

    // ───────────────────────────── API ─────────────────────────────

    fun setMode(newMode: AvatarMode) {
        if (newMode == mode) return
        mode = newMode
        avatar.setMode(newMode)
    }

    fun setMicLevel(level: Float) {
        micLevel = level.coerceIn(0f, 1f)
        avatar.setMicLevel(micLevel)
    }

    fun start() {
        if (started) return
        started = true
        lastNanos = 0L
        AnimationClock.register(this)
        avatar.start()
    }

    fun stop() {
        if (!started) return
        started = false
        AnimationClock.unregister(this)
        avatar.stop()
    }

    // ───────────────────────────── lifecycle / layout ─────────────────────────────

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) AnimationClock.unregister(this)
        else if (started) AnimationClock.register(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val side = when {
            wMode != MeasureSpec.UNSPECIFIED && hMode != MeasureSpec.UNSPECIFIED -> min(w, h)
            wMode != MeasureSpec.UNSPECIFIED -> w
            hMode != MeasureSpec.UNSPECIFIED -> h
            else -> (320 * dp).toInt()
        }
        setMeasuredDimension(side, side)
        val d = (side * RING_RADIUS * 2f * 0.97f).toInt()
        avatar.measure(
            MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val cw = avatar.measuredWidth
        val ch = avatar.measuredHeight
        avatar.layout((w - cw) / 2, (h - ch) / 2, (w + cw) / 2, (h + ch) / 2)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = min(w, h).toFloat()
        val c = s / 2f
        haloPaint.shader = RadialGradient(
            c, c, s / 2f,
            intArrayOf(Color.argb(150, 255, 40, 200), Color.argb(70, 200, 20, 190), Color.TRANSPARENT),
            floatArrayOf(0.62f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )
        discPaint.shader = RadialGradient(
            c, c, s * RING_RADIUS,
            intArrayOf(Color.rgb(70, 8, 80), Color.rgb(120, 14, 118), Color.rgb(190, 30, 170)),
            floatArrayOf(0f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ───────────────────────────── animation ─────────────────────────────

    override fun onFrame(frameTimeNanos: Long) {
        if (lastNanos == 0L) lastNanos = frameTimeNanos
        val dt = ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = frameTimeNanos
        t += dt

        // visual level: mic while listening, real voice while speaking
        val target = when (mode) {
            AvatarMode.LISTENING -> micLevel
            AvatarMode.SPEAKING -> if (LipSyncBus.sample(tmp)) tmp[0] else 0f
            AvatarMode.THINKING -> 0.25f + 0.2f * (0.5f + 0.5f * sin(t * 3f))
            AvatarMode.IDLE -> 0.08f + 0.06f * (0.5f + 0.5f * sin(t * 1.5f))
        }
        val k = if (target > level) 1f - exp(-dt / 0.05f) else 1f - exp(-dt / 0.18f)
        level += (target - level) * k

        val targetSpeed = when (mode) {
            AvatarMode.IDLE -> 0.35f
            AvatarMode.LISTENING -> 0.8f
            AvatarMode.THINKING -> 2.2f
            AvatarMode.SPEAKING -> 1.1f
        }
        speedMul += (targetSpeed - speedMul) * (1f - exp(-dt / 0.4f))
        val sp = speedMul * (1f + level * 0.8f)
        orbitA = (orbitA + 24f * sp * dt) % 360f
        orbitB = (orbitB - 17f * sp * dt + 360f) % 360f
        orbitC = (orbitC + 31f * sp * dt) % 360f
        comet = (comet + 55f * sp * dt) % 360f
        thinkSweep = (thinkSweep + 220f * dt) % 360f
        for (s in sparks) s.angle = (s.angle + s.speed * sp * dt + 360f) % 360f

        val minInterval = if (mode == AvatarMode.IDLE) 30_000_000L else 0L
        if (frameTimeNanos - lastInvalidateNs >= minInterval) {
            lastInvalidateNs = frameTimeNanos
            invalidate()
        }
    }

    // ───────────────────────────── drawing ─────────────────────────────

    override fun dispatchDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val c = s / 2f
        val r = s * RING_RADIUS

        // soft halo (ambient pink illumination)
        haloPaint.alpha = (150 + 90 * level).toInt().coerceIn(0, 255)
        canvas.drawCircle(c, c, s / 2f, haloPaint)

        // backdrop disc behind the portrait
        canvas.drawCircle(c, c, r, discPaint)

        // far side of the tilted orbit + rear sparkles
        drawOrbit(canvas, c, r, front = false)
        drawSparks(canvas, c, r, front = false)

        super.dispatchDraw(canvas)   // the avatar

        drawNeonRing(canvas, c, r)
        drawOrbitRings(canvas, c, r)
        drawOrbit(canvas, c, r, front = true)
        drawSparks(canvas, c, r, front = true)
    }

    private fun drawNeonRing(canvas: Canvas, c: Float, r: Float) {
        val boost = 1f + 0.7f * level
        val pulseR = r * (1f + 0.012f * level)
        val widths = floatArrayOf(18f, 11f, 6.5f, 3f)
        val alphas = floatArrayOf(34f, 70f, 130f, 255f)
        val colors = intArrayOf(pink, pink, hot, core)
        ringPaint.style = Paint.Style.STROKE
        for (i in widths.indices) {
            ringPaint.strokeWidth = widths[i] * dp * (0.9f + 0.25f * level)
            ringPaint.color = colors[i]
            ringPaint.alpha = (alphas[i] * min(boost, 1.6f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(c, c, pulseR, ringPaint)
        }
        // expanding ripple when the voice is loud
        if (level > 0.12f) {
            val ripple = ((t * 1.4f) % 1f)
            ringPaint.strokeWidth = 2f * dp
            ringPaint.color = hot
            ringPaint.alpha = ((1f - ripple) * 140f * level).toInt().coerceIn(0, 255)
            canvas.drawCircle(c, c, r * (1.02f + 0.16f * ripple), ringPaint)
        }
    }

    /** Thin rotating arcs around the ring (the animated orbit rings). */
    private fun drawOrbitRings(canvas: Canvas, c: Float, r: Float) {
        ringPaint.style = Paint.Style.STROKE
        val a = if (mode == AvatarMode.THINKING) 215 else 170

        ringPaint.strokeWidth = 1.8f * dp
        ringPaint.color = hot
        ringPaint.alpha = a
        oval.set(c - r * 1.07f, c - r * 1.07f, c + r * 1.07f, c + r * 1.07f)
        canvas.drawArc(oval, orbitA, 150f, false, ringPaint)
        canvas.drawArc(oval, orbitA + 190f, 60f, false, ringPaint)

        ringPaint.strokeWidth = 1.2f * dp
        ringPaint.color = Color.rgb(210, 120, 255)
        ringPaint.alpha = a - 40
        oval.set(c - r * 1.13f, c - r * 1.13f, c + r * 1.13f, c + r * 1.13f)
        canvas.drawArc(oval, orbitB, 110f, false, ringPaint)
        canvas.drawArc(oval, orbitB + 160f, 40f, false, ringPaint)

        ringPaint.strokeWidth = 1f * dp
        ringPaint.color = core
        ringPaint.alpha = a - 70
        oval.set(c - r * 1.19f, c - r * 1.19f, c + r * 1.19f, c + r * 1.19f)
        canvas.drawArc(oval, orbitC, 70f, false, ringPaint)

        // "thinking" comet: bright head with fading tail sweeping around the ring
        if (mode == AvatarMode.THINKING) {
            oval.set(c - r * 1.03f, c - r * 1.03f, c + r * 1.03f, c + r * 1.03f)
            for (i in 0 until 6) {
                ringPaint.strokeWidth = (5f - i * 0.7f) * dp
                ringPaint.color = core
                ringPaint.alpha = (230 - i * 38).coerceAtLeast(0)
                canvas.drawArc(oval, thinkSweep - i * 9f, 12f, false, ringPaint)
            }
        }
    }

    /** The tilted "planet" orbit; the far half is drawn behind the portrait, the near half in front. */
    private fun drawOrbit(canvas: Canvas, c: Float, r: Float, front: Boolean) {
        canvas.save()
        canvas.rotate(-20f, c, c)
        val a = r * 1.21f
        val b = r * 0.27f
        oval.set(c - a, c - b, c + a, c + b)
        ringPaint.style = Paint.Style.STROKE
        val start = if (front) 0f else 180f           // near (bottom) half in front, far (top) half behind
        ringPaint.strokeWidth = 5f * dp
        ringPaint.color = pink
        ringPaint.alpha = if (front) 60 else 35
        canvas.drawArc(oval, start, 180f, false, ringPaint)
        ringPaint.strokeWidth = 1.8f * dp
        ringPaint.color = hot
        ringPaint.alpha = if (front) 235 else 120
        canvas.drawArc(oval, start, 180f, false, ringPaint)

        // moving bead on the orbit
        val ang = Math.toRadians(comet.toDouble())
        val bx = c + a * cos(ang).toFloat()
        val by = c + b * sin(ang).toFloat()
        val nearSide = sin(ang) >= 0.0
        if (nearSide == front) {
            sparkPaint.style = Paint.Style.FILL
            sparkPaint.color = pink; sparkPaint.alpha = 70
            canvas.drawCircle(bx, by, 6f * dp, sparkPaint)
            sparkPaint.color = core; sparkPaint.alpha = 255
            canvas.drawCircle(bx, by, 2.4f * dp, sparkPaint)
        }
        canvas.restore()
    }

    private fun drawSparks(canvas: Canvas, c: Float, r: Float, front: Boolean) {
        for ((idx, s) in sparks.withIndex()) {
            val rad = Math.toRadians(s.angle.toDouble())
            val behind = sin(rad) < -0.15 && idx % 2 == 0   // some sparkles pass behind the portrait
            if (behind == front) continue
            val rr = r * s.radius + level * 6f * dp * sin(t * 4f + s.phase)
            val x = c + rr * cos(rad).toFloat()
            val y = c + rr * sin(rad).toFloat()
            val tw = 0.5f + 0.5f * sin(t * 2.6f + s.phase)
            val size = s.size * dp * (0.7f + 0.6f * tw)
            sparkPaint.style = Paint.Style.FILL
            sparkPaint.color = pink
            sparkPaint.alpha = (55 * tw).toInt()
            canvas.drawCircle(x, y, size * 2.6f, sparkPaint)
            sparkPaint.color = core
            sparkPaint.alpha = (110 + 145 * tw).toInt().coerceIn(0, 255)
            if (s.star) {
                sparkPaint.style = Paint.Style.STROKE
                sparkPaint.strokeWidth = max(1f, size * 0.45f)
                sparkPaint.strokeCap = Paint.Cap.ROUND
                val l = size * 2.6f
                canvas.drawLine(x - l, y, x + l, y, sparkPaint)
                canvas.drawLine(x, y - l, x, y + l, sparkPaint)
                sparkPaint.style = Paint.Style.FILL
            } else {
                canvas.drawCircle(x, y, size, sparkPaint)
            }
        }
    }

    private companion object {
        /** Neon ring radius as a fraction of the stage size (leaves room for halo, orbit and sparkles). */
        const val RING_RADIUS = 0.395f
    }
}

package com.myra.assistant.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.myra.assistant.R
import com.myra.assistant.utils.Logger
import com.myra.assistant.voice.LipSyncBus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * MYRA's face: a layered 2D facial rig built on the fixed MYRA portrait (so the identity never changes).
 *
 *  - Mouth: the lower lip/jaw region is mesh-warped downwards (drawBitmapMesh) and a mouth interior
 *    (dark cavity, teeth, tongue) is revealed. Opening is driven by [LipSyncBus], i.e. by the actual
 *    PCM audio that is playing.
 *  - Eyes: eyelids are drawn over the eyes to blink (natural random blink timing, double blinks, droop
 *    while "thinking").
 *  - Head: breathing + very small rotation/translation, with different amplitudes per state.
 *
 * All work happens in [onFrame] driven by the shared [AnimationClock]; nothing runs while stopped.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), AnimationClock.Listener {

    private companion object {
        const val TAG = "AVATAR"

        // ── Portrait geometry (pixel coordinates of res/drawable-nodpi/myra_avatar.png, 600×600) ──
        const val SRC = 600f
        const val MX = 338.5f            // mouth centre (midpoint between the lip corners)
        const val MY = 293.25f
        const val M_ANGLE = 15.9f        // tilt of the lip line (degrees, clockwise)
        const val M_HALF = 39.5f         // half width of the mouth
        const val SAG = 5f               // how far the lip contact line sags below the corner-to-corner line
        const val MAX_OPEN = 15f         // maximum jaw drop in portrait pixels

        // mouth bake region (flattened frame: u along the lip line, v perpendicular)
        const val U = 70f                // half width of the lower-lip patch
        const val LOW_H = 56f            // height of the warped lower patch
        const val LIP_RIGID = 22f        // part of the patch that moves rigidly with the lip
        const val BAKE = 2               // supersampling of the baked patch
        const val MESH_W = 28
        const val MESH_H = 10

        const val CROP = 0.985f          // circle clip relative to the view radius
        const val BASE_ZOOM = 1.025f     // hides edges while the head moves
    }

    private class Eye(
        val cx: Float, val cy: Float, val half: Float, val angle: Float,
        val hUp: Float, val hLow: Float, val lid: Int
    ) {
        var shader: Shader? = null
    }

    private val eyes = arrayOf(
        Eye(316f, 189f, 27f, 21f, 15f, 11f, 0xFFE4A09E.toInt()),
        Eye(419f, 218f, 29f, 8f, 15f, 12f, 0xFFD68E8A.toInt())
    )

    // ── assets ──
    private var portrait: Bitmap? = null
    private var lowerPatch: Bitmap? = null

    // ── paints / reusable objects ──
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val lashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f
        color = Color.rgb(52, 28, 24)
    }
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val lidPath = Path()
    private val lashPath = Path()
    private val lensPath = Path()
    private val teethPath = Path()
    private val verts = FloatArray((MESH_W + 1) * (MESH_H + 1) * 2)

    // ── animation state ──
    private var mode = AvatarMode.IDLE
    private var micLevel = 0f
    private var started = false
    private var lastNanos = 0L
    private var timeSec = 0f
    private var lastFrameInvalidateNs = 0L

    private var mouthOpen = 0f          // smoothed 0..1
    private var mouthShape = 0f         // smoothed -1..1
    private var lastSpeakingMs = 0L

    private var blink = 0f              // 0 open .. 1 closed
    private var nextBlinkAt = 1.5f
    private var blinkStart = -1f
    private var pendingDoubleBlink = false

    // blended pose parameters
    private var rotAmp = 0.45f
    private var shiftAmp = 1.2f
    private var poseRot = 0f
    private var poseTx = 0f
    private var poseTy = 0f
    private var droop = 0f
    private var listenNod = 0f
    private var glow = 0f

    private val tmp = FloatArray(2)

    // ───────────────────────────── public API ─────────────────────────────

    fun setMode(newMode: AvatarMode) {
        if (newMode == mode) return
        if (mode == AvatarMode.SPEAKING) lastSpeakingMs = SystemClock.uptimeMillis()
        mode = newMode
        // schedule a natural blink soon after a state change so the face "reacts"
        if (nextBlinkAt - timeSec > 1.2f) nextBlinkAt = timeSec + 0.35f
    }

    /** Microphone amplitude 0..1 from VoiceStateManager (used for the listening reaction). */
    fun setMicLevel(level: Float) {
        micLevel = level.coerceIn(0f, 1f)
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

    // ───────────────────────────── lifecycle ─────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureAssets()
    }

    override fun onDetachedFromWindow() {
        stop()
        releaseAssets()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // Pause the loop while hidden (e.g. activity in background); resume only if it was started.
        if (!isVisible) AnimationClock.unregister(this)
        else if (started) AnimationClock.register(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.rewind()
        clipPath.addCircle(w / 2f, h / 2f, min(w, h) / 2f * CROP, Path.Direction.CW)
        val r = min(w, h) / 2f
        // soft vignette + pink rim light, depth effect
        lightPaint.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(120, 70, 0, 80)),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ───────────────────────────── assets ─────────────────────────────

    private fun ensureAssets() {
        if (portrait != null) return
        try {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.myra_avatar, opts) ?: return
            portrait = bmp
            lowerPatch = bakeLowerPatch(bmp)
            for (e in eyes) {
                e.shader = LinearGradient(
                    0f, -e.hUp, 0f, e.hLow,
                    lighten(e.lid, 0.10f), e.lid, Shader.TileMode.CLAMP
                )
            }
        } catch (t: Throwable) {
            Logger.e(TAG, "Avatar asset load failed: ${t.message}")
        }
    }

    private fun releaseAssets() {
        portrait?.recycle(); portrait = null
        lowerPatch?.recycle(); lowerPatch = null
    }

    private fun lighten(c: Int, f: Float): Int {
        fun ch(v: Int) = (v + (255 - v) * f).toInt().coerceIn(0, 255)
        return Color.rgb(ch(Color.red(c)), ch(Color.green(c)), ch(Color.blue(c)))
    }

    private fun sagAt(u: Float): Float {
        val t = u / M_HALF
        return if (abs(t) >= 1f) 0f else SAG * (1f - t * t)
    }

    /**
     * Cuts the area below the lip line out of the portrait, rotated so the lip line is horizontal and
     * "un-sagged" so its top edge is the straight lip contact line. It is later drawn through a mesh.
     */
    private fun bakeLowerPatch(src: Bitmap): Bitmap {
        val vTop = -10f
        val vBot = LOW_H + SAG + 4f
        val bw = (2 * U * BAKE).toInt()
        val bh = ((vBot - vTop) * BAKE).toInt()
        val flat = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            postTranslate(-MX, -MY)
            postRotate(-M_ANGLE)
            postScale(BAKE.toFloat(), BAKE.toFloat())
            postTranslate(U * BAKE, -vTop * BAKE)
        }
        Canvas(flat).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

        val ph = (LOW_H * BAKE).toInt()
        val flatPx = IntArray(bw * bh)
        flat.getPixels(flatPx, 0, bw, 0, 0, bw, bh)
        val outPx = IntArray(bw * ph)
        for (x in 0 until bw) {
            val u = -U + (x + 0.5f) / BAKE
            val off = ((sagAt(u) - vTop) * BAKE).toInt()
            for (y in 0 until ph) {
                val sy = (y + off).coerceIn(0, bh - 1)
                outPx[y * bw + x] = flatPx[sy * bw + x]
            }
        }
        flat.recycle()
        return Bitmap.createBitmap(outPx, bw, ph, Bitmap.Config.ARGB_8888)
    }

    // ───────────────────────────── animation ─────────────────────────────

    override fun onFrame(frameTimeNanos: Long) {
        if (lastNanos == 0L) lastNanos = frameTimeNanos
        val dt = ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = frameTimeNanos
        timeSec += dt

        val nowMs = SystemClock.uptimeMillis()
        val speakingMode = mode == AvatarMode.SPEAKING
        if (speakingMode) lastSpeakingMs = nowMs

        // ── lip sync: follows the audio that is audible; only allowed while MYRA is speaking
        //    (plus a short tail so the last syllable finishes). Never while the user is talking.
        var targetOpen = 0f
        var targetShape = 0f
        val mouthAllowed = speakingMode || nowMs - lastSpeakingMs < 450
        if (mouthAllowed && LipSyncBus.sample(tmp)) {
            targetOpen = tmp[0]; targetShape = tmp[1]
        }
        val kOpen = if (targetOpen > mouthOpen) 1f - exp(-dt / 0.035f) else 1f - exp(-dt / 0.085f)
        mouthOpen += (targetOpen - mouthOpen) * kOpen
        mouthShape += (targetShape - mouthShape) * (1f - exp(-dt / 0.08f))

        updateBlink(dt)
        updatePose(dt)

        // Idle needs only ~30 fps; speaking/listening get full rate.
        val minInterval = if (mode == AvatarMode.IDLE && mouthOpen < 0.02f && blink == 0f) 30_000_000L else 0L
        if (frameTimeNanos - lastFrameInvalidateNs >= minInterval) {
            lastFrameInvalidateNs = frameTimeNanos
            invalidate()
        }
    }

    private fun exp(x: Float) = kotlin.math.exp(x)

    private fun updateBlink(dt: Float) {
        if (blinkStart < 0f) {
            if (timeSec >= nextBlinkAt) blinkStart = timeSec
        }
        if (blinkStart >= 0f) {
            val dur = 0.17f
            val p = (timeSec - blinkStart) / dur
            if (p >= 1f) {
                blink = 0f
                blinkStart = -1f
                if (pendingDoubleBlink) {
                    pendingDoubleBlink = false
                    nextBlinkAt = timeSec + 0.12f
                } else {
                    val (lo, hi) = when (mode) {
                        AvatarMode.SPEAKING -> 2.2f to 4.2f
                        AvatarMode.LISTENING -> 3.0f to 5.5f
                        AvatarMode.THINKING -> 3.5f to 6.5f
                        AvatarMode.IDLE -> 2.8f to 5.5f
                    }
                    nextBlinkAt = timeSec + lo + (hi - lo) * Math.random().toFloat()
                    pendingDoubleBlink = Math.random() < 0.12
                }
            } else {
                // quick close (40 %), slightly slower open
                blink = if (p < 0.4f) smooth(p / 0.4f) else smooth(1f - (p - 0.4f) / 0.6f)
            }
        }
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun updatePose(dt: Float) {
        val k = 1f - exp(-dt / 0.35f)
        val tRot: Float; val tShift: Float; val tDroop: Float; val tGlow: Float
        when (mode) {
            AvatarMode.IDLE -> { tRot = 0.45f; tShift = 1.1f; tDroop = 0f; tGlow = 0.15f }
            AvatarMode.LISTENING -> { tRot = 0.6f; tShift = 1.4f; tDroop = 0f; tGlow = 0.35f + micLevel * 0.5f }
            AvatarMode.THINKING -> { tRot = 0.9f; tShift = 1.4f; tDroop = 0.16f + 0.06f * sin(timeSec * 1.3f); tGlow = 0.55f }
            AvatarMode.SPEAKING -> { tRot = 0.9f; tShift = 1.8f; tDroop = 0f; tGlow = 0.5f + mouthOpen * 0.5f }
        }
        rotAmp += (tRot - rotAmp) * k
        shiftAmp += (tShift - shiftAmp) * k
        droop += (tDroop - droop) * k
        glow += (tGlow - glow) * (1f - exp(-dt / 0.12f))

        val t = timeSec
        val breath = sin(t * (2f * PI.toFloat() / 4.2f))
        var rot = rotAmp * (0.6f * sin(t * 0.7f) + 0.4f * sin(t * 1.31f + 1.2f))
        var tx = shiftAmp * (0.6f * sin(t * 0.53f + 0.4f) + 0.4f * sin(t * 1.07f))
        var ty = shiftAmp * (0.5f * sin(t * 0.61f + 2f) + 0.5f * breath * 0.8f)

        // listening: attentive lean + tiny nod from the microphone level
        val nodTarget = if (mode == AvatarMode.LISTENING) micLevel else 0f
        listenNod += (nodTarget - listenNod) * (1f - exp(-dt / 0.12f))
        ty += listenNod * 2.2f - if (mode == AvatarMode.LISTENING) 1.2f else 0f

        // thinking: slight head tilt, glance to the side
        if (mode == AvatarMode.THINKING) { rot += 1.8f; tx -= 2.6f }

        // speaking: gentle emphasis nod with the voice
        if (mode == AvatarMode.SPEAKING) {
            ty += mouthOpen * 1.6f
            rot += mouthOpen * 0.35f * sin(t * 3.1f)
        }

        poseRot += (rot - poseRot) * (1f - exp(-dt / 0.08f))
        poseTx += (tx - poseTx) * (1f - exp(-dt / 0.08f))
        poseTy += (ty - poseTy) * (1f - exp(-dt / 0.08f))
    }

    // ───────────────────────────── drawing ─────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val bmp = portrait ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val sc = min(w, h) / SRC

        canvas.save()
        canvas.clipPath(clipPath)

        canvas.translate((w - SRC * sc) / 2f, (h - SRC * sc) / 2f)
        canvas.scale(sc, sc)
        canvas.translate(poseTx, poseTy)
        canvas.rotate(poseRot, SRC / 2f, SRC / 2f)
        val breathZoom = BASE_ZOOM + 0.006f * sin(timeSec * (2f * PI.toFloat() / 4.2f))
        canvas.scale(breathZoom, breathZoom, SRC / 2f, SRC / 2f)

        canvas.drawBitmap(bmp, 0f, 0f, bmpPaint)

        drawEyelids(canvas)
        drawMouth(canvas)

        canvas.restore()

        // soft facial lighting / vignette (view space)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawPaint(lightPaint)
        // pink rim light that breathes with the voice / state
        fillPaint.shader = null
        fillPaint.color = Color.argb((18 + 40 * glow).toInt().coerceIn(0, 90), 255, 80, 200)
        fillPaint.style = Paint.Style.FILL
        canvas.drawPaint(fillPaint)
        canvas.restore()
    }

    private fun drawEyelids(canvas: Canvas) {
        val b = max(blink, droop).coerceIn(0f, 1f)
        if (b < 0.02f) return
        for (e in eyes) {
            canvas.save()
            canvas.translate(e.cx, e.cy)
            canvas.rotate(e.angle)

            val upCtl = -e.hUp * 1.9f
            val lashCtl = upCtl + (e.hLow * 1.9f - upCtl) * b

            lidPath.rewind()
            lidPath.moveTo(-e.half, 0f)
            lidPath.quadTo(0f, upCtl, e.half, 0f)
            lidPath.quadTo(0f, lashCtl, -e.half, 0f)
            lidPath.close()
            fillPaint.shader = e.shader
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = Color.WHITE
            canvas.drawPath(lidPath, fillPaint)
            fillPaint.shader = null

            lashPath.rewind()
            lashPath.moveTo(-e.half, 0f)
            lashPath.quadTo(0f, lashCtl, e.half, 0f)
            lashPaint.alpha = (min(1f, b * 5f) * 255).toInt()
            canvas.drawPath(lashPath, lashPaint)

            canvas.restore()
        }
    }

    private fun drawMouth(canvas: Canvas) {
        val patch = lowerPatch ?: return
        val open = mouthOpen
        if (open < 0.02f) return
        val d = open * MAX_OPEN
        if (d < 0.35f) return

        val shape = mouthShape
        val p = (0.9f - 0.35f * shape).coerceIn(0.5f, 1.3f)   // spread -> broad opening, round -> narrow

        canvas.save()
        canvas.translate(MX, MY)
        canvas.rotate(M_ANGLE)

        // 1) mouth interior (cavity, teeth, tongue) between the lip contact line and the dropped lower lip
        lensPath.rewind()
        val steps = 24
        for (i in 0..steps) {
            val u = -M_HALF + 2f * M_HALF * i / steps
            val y = sagAt(u)
            if (i == 0) lensPath.moveTo(u, y) else lensPath.lineTo(u, y)
        }
        for (i in steps downTo 0) {
            val u = -M_HALF + 2f * M_HALF * i / steps
            lensPath.lineTo(u, sagAt(u) + d * weight(u, p) + 0.8f)
        }
        lensPath.close()

        fillPaint.shader = null
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.rgb(74, 14, 30)
        canvas.drawPath(lensPath, fillPaint)

        canvas.save()
        canvas.clipPath(lensPath)
        // tongue
        if (d > 5f) {
            fillPaint.color = Color.rgb(176, 52, 78)
            val tw = M_HALF * 0.55f
            canvas.drawOval(-tw, sagAt(0f) + d * 0.50f, tw, sagAt(0f) + d * 1.15f, fillPaint)
        }
        // upper teeth
        teethPath.rewind()
        val tSteps = 20
        val tHalf = M_HALF * 0.84f
        for (i in 0..tSteps) {
            val u = -tHalf + 2f * tHalf * i / tSteps
            if (i == 0) teethPath.moveTo(u, sagAt(u) - 0.5f) else teethPath.lineTo(u, sagAt(u) - 0.5f)
        }
        for (i in tSteps downTo 0) {
            val u = -tHalf + 2f * tHalf * i / tSteps
            val th = min(0.46f * d, 7.5f) * weight(u, p).coerceAtLeast(0.15f)
            teethPath.lineTo(u, sagAt(u) + th)
        }
        teethPath.close()
        fillPaint.color = Color.rgb(246, 238, 234)
        canvas.drawPath(teethPath, fillPaint)
        canvas.restore()

        // 2) lower lip + chin patch, smoothly warped down (rigid lip, stretching chin)
        var vi = 0
        for (j in 0..MESH_H) {
            val v = LOW_H * j / MESH_H
            val f = falloff(v)
            for (i in 0..MESH_W) {
                val u = -U + 2f * U * i / MESH_W
                verts[vi++] = u
                verts[vi++] = v + sagAt(u) + d * weight(u, p) * f
            }
        }
        canvas.drawBitmapMesh(patch, MESH_W, MESH_H, verts, 0, null, 0, meshPaint)

        canvas.restore()
    }

    private fun weight(u: Float, p: Float): Float {
        val t = u / M_HALF
        if (abs(t) >= 1f) return 0f
        return (1f - t * t).pow(p)
    }

    private fun falloff(v: Float): Float {
        if (v <= LIP_RIGID) return 1f
        val t = ((v - LIP_RIGID) / (LOW_H - LIP_RIGID)).coerceIn(0f, 1f)
        return 0.5f * (1f + cos(PI.toFloat() * t))
    }
}

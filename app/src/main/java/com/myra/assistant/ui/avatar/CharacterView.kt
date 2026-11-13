package com.myra.assistant.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
import kotlin.math.sin
import kotlin.random.Random

/**
 * MYRA's 3D character, shown from head to mid-thigh (cut at the bottom edge of the screen) so the face is
 * big and close. Source is a transparent 4K image, decoded only at the size the screen needs.
 *
 * Animation (all drawn live, nothing pre-rendered):
 *  - eyes: natural random blinking (double blinks sometimes), eyelids drawn in the skin colour
 *  - mouth: opens with the real voice level of MYRA (LipSyncBus) while she speaks
 *  - hair / sleeve: soft wave using a mesh warp (face stays still), stronger while speaking / dancing
 *  - body: breathing, listening lean, thinking tilt, and commanded actions from [CharacterActionBus]
 *    (dance, spin, jump, wave, bow, sway)
 */
class CharacterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), AnimationClock.Listener, CharacterActionBus.Listener {

    private companion object {
        const val TAG = "CHARACTER"

        // Face landmarks as fractions of the (cropped) character image: x / width, y / height.
        // eye: cx, cy, half-width (fraction of width), tilt (deg)
        val EYE_L = floatArrayOf(0.4206f, 0.1672f, 0.0290f, -12f)
        val EYE_R = floatArrayOf(0.5118f, 0.1483f, 0.0240f, -22f)
        // mouth: cx, cy, half-width (fraction of width), tilt (deg)
        val MOUTH = floatArrayOf(0.4963f, 0.2026f, 0.0309f, -21f)

        const val COLS = 20
        const val ROWS = 36
    }

    private var bitmap: Bitmap? = null
    private var loadedForHeight = 0
    private var skinL = Color.rgb(240, 190, 175)
    private var skinR = Color.rgb(240, 190, 175)
    private var lipColor = Color.rgb(215, 90, 105)

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()
    private val m = Matrix()
    private val cam = Camera()
    private val camM = Matrix()
    private val tmp = FloatArray(2)

    private val orig = FloatArray((COLS + 1) * (ROWS + 1) * 2)
    private val warped = FloatArray(orig.size)

    private var mode = AvatarMode.IDLE
    private var micLevel = 0f
    private var started = false
    private var lastNanos = 0L
    private var t = 0f
    private var voice = 0f
    private var mouth = 0f
    private var speaking = false

    private var blink = 0f
    private var nextBlinkAt = 1.5f
    private var blinkStart = -1f
    private var doubleBlink = false

    private var action: CharacterAction? = null
    private var actionT = 0f
    private var actionDur = 0f
    private var blend = 0f

    fun setMode(newMode: AvatarMode) { mode = newMode }
    fun setMicLevel(level: Float) { micLevel = level.coerceIn(0f, 1f) }

    fun start() {
        if (started) return
        started = true
        lastNanos = 0L
        AnimationClock.register(this)
        CharacterActionBus.register(this)
    }

    fun stop() {
        if (!started) return
        started = false
        AnimationClock.unregister(this)
        CharacterActionBus.unregister(this)
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) AnimationClock.unregister(this) else if (started) AnimationClock.register(this)
    }

    override fun onCharacterAction(action: CharacterAction, durationMs: Long) {
        if (action == CharacterAction.STOP) { actionDur = actionT + 0.45f; return }
        this.action = action
        actionT = 0f
        actionDur = durationMs / 1000f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (h > 0) loadBitmap(h)
        glowPaint.shader = RadialGradient(
            w / 2f, h * 0.40f, max(w, h) * 0.6f,
            intArrayOf(Color.argb(110, 255, 40, 190), Color.argb(45, 255, 40, 190), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    /** Decode the 4K source only as large as needed (≈1.5× view height); never the full 4K in RAM. */
    private fun loadBitmap(viewH: Int) {
        if (bitmap != null && loadedForHeight >= viewH) return
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, R.drawable.myra_character, bounds)
            var sample = 1
            val want = (viewH * 1.5f).toInt()
            while (bounds.outHeight / (sample * 2) >= want) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.myra_character, opts) ?: return
            bitmap = bmp
            loadedForHeight = viewH
            val bw = bmp.width; val bh = bmp.height
            // build the warp grid
            var k = 0
            for (j in 0..ROWS) for (i in 0..COLS) {
                orig[k++] = bw * i / COLS.toFloat(); orig[k++] = bh * j / ROWS.toFloat()
            }
            // sample real skin / lip colours so the eyelids and lip rim match this picture
            skinL = sampleColor(bmp, EYE_L[0], EYE_L[1] + 0.028f)
            skinR = sampleColor(bmp, EYE_R[0], EYE_R[1] + 0.026f)
            lipColor = sampleColor(bmp, MOUTH[0], MOUTH[1] + 0.006f)
        } catch (e: Throwable) {
            Logger.e(TAG, "character decode failed: ${e.message}")
        }
    }

    private fun sampleColor(b: Bitmap, fx: Float, fy: Float): Int {
        val cx = (fx * b.width).toInt(); val cy = (fy * b.height).toInt()
        var r = 0; var g = 0; var bl = 0; var n = 0
        for (dy in -2..2) for (dx in -2..2) {
            val x = (cx + dx * 2).coerceIn(0, b.width - 1); val y = (cy + dy * 2).coerceIn(0, b.height - 1)
            val p = b.getPixel(x, y)
            if (Color.alpha(p) > 200) { r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++ }
        }
        return if (n == 0) Color.rgb(240, 190, 175) else Color.rgb(r / n, g / n, bl / n)
    }

    override fun onFrame(frameTimeNanos: Long) {
        if (!started || !isShown) return
        val dt = if (lastNanos == 0L) 0.016f else min(0.05f, (frameTimeNanos - lastNanos) / 1e9f)
        lastNanos = frameTimeNanos
        t += dt

        // voice: speaking audio level drives mouth + body; mic level drives a small listening motion
        speaking = mode == AvatarMode.SPEAKING && LipSyncBus.sample(tmp)
        val mouthTarget = if (speaking) tmp[0].coerceIn(0f, 1f) else 0f
        mouth += (mouthTarget - mouth) * min(1f, dt * (if (mouthTarget > mouth) 30f else 14f))
        val target = if (speaking) tmp[0] else if (mode == AvatarMode.LISTENING) micLevel * 0.6f else 0f
        voice += (target - voice) * min(1f, dt * 14f)

        // blinking (thinking = slow heavy blinks)
        if (blinkStart < 0f && t >= nextBlinkAt) { blinkStart = t; doubleBlink = Random.nextFloat() < 0.18f }
        if (blinkStart >= 0f) {
            val d = if (mode == AvatarMode.THINKING) 0.28f else 0.17f
            val p = (t - blinkStart) / d
            blink = if (p >= 1f) 0f else sin(PI.toFloat() * p)
            if (p >= 1f) {
                blinkStart = if (doubleBlink) { doubleBlink = false; t + 0.05f } else -1f
                if (blinkStart < 0f) nextBlinkAt = t + 1.8f + Random.nextFloat() * 3.4f
            }
        } else blink = 0f

        val a = action
        if (a != null) {
            actionT += dt
            blend = min(min(1f, actionT / 0.35f), min(1f, max(0f, (actionDur - actionT) / 0.45f)))
            if (actionT >= actionDur) { action = null; blend = 0f }
        } else blend = 0f
        invalidate()
    }

    private fun sstep(e0: Float, e1: Float, x: Float): Float {
        val k = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    private fun warpHair(bw: Float, bh: Float) {
        val lively = 1f + 1.6f * voice + (if (action != null) 2.6f * blend else 0f)
        val amp = bw * 0.0065f * lively
        var k = 0
        while (k < orig.size) {
            val x = orig[k]; val y = orig[k + 1]
            val fx = x / bw; val fy = y / bh
            // long curls on the left of the face and the sleeve below them; the face itself stays still
            val wL = sstep(0.40f, 0.22f, fx) * sstep(0.09f, 0.20f, fy) * sstep(0.62f, 0.42f, fy)
            // lighter strand on the right side of the face
            val wR = sstep(0.66f, 0.74f, fx) * sstep(0.08f, 0.16f, fy) * sstep(0.34f, 0.22f, fy) * 0.6f
            val w = max(wL, wR)
            val ph = t * 1.5f + fy * 7f + fx * 3f
            warped[k] = x + amp * w * sin(ph)
            warped[k + 1] = y + amp * 0.35f * w * cos(ph * 0.9f)
            k += 2
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.clipRect(0f, 0f, w, h)

        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        // Close-up: fill the width; the cut edge (mid-thigh) sits on / just below the bottom of the view.
        val over = h * 0.06f
        val fit = min(w * 1.0f / bw, (h + over) / bh)
        val dw = bw * fit; val dh = bh * fit
        val left = (w - dw) / 2f
        val bottom = h + over
        val top = bottom - dh
        val pivX = w / 2f; val pivY = bottom

        glowPaint.alpha = (150 + 60 * sin(t * 1.6f)).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // ---- pose ----
        var tx = 0f; var ty = 0f; var rot = 0f; var sx = 1f; var sy = 1f; var skew = 0f; var yaw = 0f
        val br = sin(t * 1.9f)
        sy += 0.005f * br; sx -= 0.002f * br
        rot += 0.6f * sin(t * 0.8f)
        tx += w * 0.004f * sin(t * 0.6f)
        when (mode) {
            AvatarMode.LISTENING -> { rot += 1.4f; ty -= h * 0.003f * (1 + sin(t * 3f)) }
            AvatarMode.THINKING -> { rot -= 2.0f; tx -= w * 0.01f }
            else -> {}
        }
        ty -= h * 0.006f * voice
        rot += 0.8f * voice * sin(t * 9f)

        val a = action
        if (a != null && blend > 0f) {
            val p = actionT; val b = blend; val tp = 2f * PI.toFloat()
            when (a) {
                CharacterAction.DANCE -> {
                    val beat = sin(p * tp * 1.8f); val hip = sin(p * tp * 0.9f)
                    ty -= b * h * 0.022f * abs(beat)
                    sy += b * 0.016f * (1f - abs(beat))
                    tx += b * w * 0.04f * hip
                    rot += b * 5.5f * hip
                    skew += b * 0.06f * sin(p * tp * 0.9f + 0.9f)
                }
                CharacterAction.SWAY -> {
                    val s = sin(p * tp * 0.5f)
                    tx += b * w * 0.045f * s; rot += b * 3.5f * s; skew += b * 0.035f * s
                    ty -= b * h * 0.005f * abs(sin(p * tp))
                }
                CharacterAction.SPIN -> {
                    val k = (p / max(0.01f, actionDur)).coerceIn(0f, 1f)
                    yaw = 360f * (k * k * (3f - 2f * k))
                    ty -= h * 0.02f * sin(PI.toFloat() * k)
                }
                CharacterAction.JUMP -> {
                    val cyc = p / 0.85f; val frac = cyc - cyc.toInt()
                    val up = sin(PI.toFloat() * frac)
                    ty -= b * h * 0.05f * up          // stays below the hidden "over" margin, bottom never shows a gap
                    sy += b * 0.03f * up
                }
                CharacterAction.WAVE -> {
                    val s = sin(p * tp * 2.2f)
                    rot += b * 6f * s; tx += b * w * 0.012f * s
                    ty -= b * h * 0.005f * abs(s)
                }
                CharacterAction.BOW -> {
                    val k = (p / max(0.01f, actionDur)).coerceIn(0f, 1f)
                    val d = sin(PI.toFloat() * k)
                    sy -= 0.05f * d; rot += 4f * d; ty += h * 0.008f * d
                }
                CharacterAction.STOP -> {}
            }
        }

        // ---- body transform (pivot = bottom centre) ----
        m.reset()
        m.postScale(fit, fit)
        m.postTranslate(left, top)
        m.postScale(sx, sy, pivX, pivY)
        m.postSkew(skew, 0f, pivX, pivY)
        m.postRotate(rot, pivX, pivY)
        if (yaw != 0f) {
            cam.save(); cam.rotateY(yaw); cam.getMatrix(camM); cam.restore()
            val cy = top + dh * 0.4f
            camM.preTranslate(-pivX, -cy); camM.postTranslate(pivX, cy)
            m.postConcat(camM)
        }
        m.postTranslate(tx, ty)

        canvas.save()
        canvas.concat(m)
        warpHair(bw, bh)
        canvas.drawBitmapMesh(bmp, COLS, ROWS, warped, 0, null, 0, bmpPaint)
        drawEye(canvas, bmp, EYE_L, skinL)
        drawEye(canvas, bmp, EYE_R, skinR)
        drawMouth(canvas, bmp)
        canvas.restore()
    }

    private fun drawEye(c: Canvas, bmp: Bitmap, e: FloatArray, skin: Int) {
        if (blink < 0.04f) return
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        val half = e[2] * bw
        val hUp = half * 0.55f; val hLow = half * 0.42f
        c.save()
        c.translate(e[0] * bw, e[1] * bh)
        c.rotate(e[3])
        val lidY = -hUp + blink * (hUp + hLow)
        rect.set(-half * 1.04f, -hUp * 1.25f, half * 1.04f, lidY)
        fill.color = skin
        c.drawOval(rect, fill)
        if (blink > 0.35f) {                      // closed-eye lash line along the lid edge
            stroke.color = Color.argb((255 * min(1f, blink * 1.2f)).toInt(), 60, 32, 30)
            stroke.strokeWidth = max(1.5f, half * 0.12f)
            rect.set(-half * 0.98f, lidY - (hUp + hLow) * 0.35f, half * 0.98f, lidY + (hUp + hLow) * 0.35f)
            c.drawArc(rect, 8f, 164f, false, stroke)
        }
        c.restore()
    }

    private fun drawMouth(c: Canvas, bmp: Bitmap) {
        if (mouth < 0.05f) return
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        val half = MOUTH[2] * bw
        val open = (mouth * 0.62f + 0.04f) * half
        c.save()
        c.translate(MOUTH[0] * bw, MOUTH[1] * bh)
        c.rotate(MOUTH[3])
        val widthK = 0.78f - 0.12f * mouth
        // lip rim
        rect.set(-half * widthK - half * 0.07f, -open * 0.62f - half * 0.07f, half * widthK + half * 0.07f, open * 0.9f + half * 0.09f)
        fill.color = lipColor
        c.drawOval(rect, fill)
        // dark cavity
        rect.set(-half * widthK, -open * 0.55f, half * widthK, open * 0.82f)
        fill.color = Color.rgb(92, 18, 36)
        c.drawOval(rect, fill)
        // teeth strip + tongue
        if (mouth > 0.25f) {
            fill.color = Color.rgb(250, 244, 240)
            rect.set(-half * widthK * 0.72f, -open * 0.55f, half * widthK * 0.72f, -open * 0.55f + open * 0.30f)
            c.drawOval(rect, fill)
            fill.color = Color.rgb(222, 96, 120)
            rect.set(-half * widthK * 0.5f, open * 0.28f, half * widthK * 0.5f, open * 0.82f)
            c.drawOval(rect, fill)
        }
        c.restore()
    }
}

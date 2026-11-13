package com.myra.assistant.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * MYRA's 3D character, head to mid-thigh, FIXED on the screen (no drifting left/right/up/down).
 *
 *  - mouth: the lower lip + chin of the character picture itself move down with MYRA's real voice
 *    (LipSyncBus), a dark mouth interior and teeth appear in the gap (no pasted-on shape)
 *  - hair: wind animation (gusts blow the long curls left and right), face stays still
 *  - eyes: natural random blinking
 *  - body: completely still. Only when the user explicitly commands dance / spin / jump / wave / bow /
 *    sway does it move, and it returns to the exact same fixed spot afterwards.
 */
class CharacterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), AnimationClock.Listener, CharacterActionBus.Listener {

    private companion object {
        const val TAG = "CHARACTER"

        // Face landmarks as fractions of the cropped character image: x / width, y / height.
        // eye: cx, cy, half-width (fraction of width), tilt (deg)
        val EYE_L = floatArrayOf(0.4206f, 0.1672f, 0.0290f, -12f)
        val EYE_R = floatArrayOf(0.5118f, 0.1483f, 0.0240f, -22f)
        // mouth: cx, cy (lip line), half-width (fraction of width), tilt of the lip line (deg)
        val MOUTH = floatArrayOf(0.4895f, 0.1982f, 0.0330f, -21f)

        const val COLS = 28
        const val ROWS = 48
        const val M_COLS = 20
        const val M_ROWS = 8
    }

    private var bitmap: Bitmap? = null
    private var loadedForHeight = 0
    private var skinL = Color.rgb(240, 190, 175)
    private var skinR = Color.rgb(240, 190, 175)

    // lower lip + chin, cut out in the lip-line frame (axis aligned) so it can be warped downwards
    private var lowerPatch: Bitmap? = null
    private var patchHalf = 0f      // mouth half width in bitmap pixels
    private var patchW = 0f
    private var patchH = 0f

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()
    private val path = Path()
    private val m = Matrix()
    private val cam = Camera()
    private val camM = Matrix()
    private val tmp = FloatArray(2)

    private val orig = FloatArray((COLS + 1) * (ROWS + 1) * 2)
    private val warped = FloatArray(orig.size)
    private val mOrig = FloatArray((M_COLS + 1) * (M_ROWS + 1) * 2)
    private val mWarped = FloatArray(mOrig.size)

    private var mode = AvatarMode.IDLE
    private var micLevel = 0f
    private var started = false
    private var lastNanos = 0L
    private var t = 0f
    private var voice = 0f
    private var mouth = 0f
    private var shape = 0f          // smoothed vowel shape: -1 rounded (oo/oh) .. +1 spread (ee)

    // mouth interior look (unit gradients, scaled to the real gap height every frame)
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderM = Matrix()
    private val interiorShader = LinearGradient(0f, 0f, 0f, 1f,
        intArrayOf(Color.rgb(38, 4, 12), Color.rgb(92, 20, 36), Color.rgb(150, 54, 66)),
        floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
    private val teethShader = LinearGradient(0f, 0f, 0f, 1f,
        intArrayOf(Color.rgb(252, 247, 241), Color.rgb(238, 224, 216)), null, Shader.TileMode.CLAMP)
    private val shadeShader = LinearGradient(0f, 0f, 0f, 1f,
        intArrayOf(Color.argb(120, 28, 3, 10), Color.argb(0, 28, 3, 10)), null, Shader.TileMode.CLAMP)

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
            var k = 0
            for (j in 0..ROWS) for (i in 0..COLS) {
                orig[k++] = bw * i / COLS.toFloat(); orig[k++] = bh * j / ROWS.toFloat()
            }
            skinL = sampleColor(bmp, EYE_L[0], EYE_L[1] + 0.028f)
            skinR = sampleColor(bmp, EYE_R[0], EYE_R[1] + 0.026f)
            buildLowerPatch(bmp)
        } catch (e: Throwable) {
            Logger.e(TAG, "character decode failed: ${e.message}")
        }
    }

    /** Cut the lower lip + chin out of the picture, rotated so the lip line is horizontal. */
    private fun buildLowerPatch(bmp: Bitmap) {
        val half = MOUTH[2] * bmp.width
        patchHalf = half
        patchW = half * 3.6f
        patchH = half * 2.1f
        val pw = patchW.toInt().coerceAtLeast(8); val ph = patchH.toInt().coerceAtLeast(8)
        val patch = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val c = Canvas(patch)
        val mx = Matrix()
        mx.postTranslate(-MOUTH[0] * bmp.width, -MOUTH[1] * bmp.height)
        mx.postRotate(-MOUTH[3])
        mx.postTranslate(pw / 2f, 0f)
        c.drawBitmap(bmp, mx, bmpPaint)
        // feather the very top rows: the old lip-crease line melts into the mouth interior instead of
        // showing up as a second hard lip line under the gap
        val fade = half * 0.09f
        val fp = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, fade, Color.argb(40, 0, 0, 0), Color.BLACK, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, pw.toFloat(), fade, fp)
        lowerPatch = patch
        var k = 0
        for (j in 0..M_ROWS) for (i in 0..M_COLS) {
            mOrig[k++] = -patchW / 2f + patchW * i / M_COLS
            mOrig[k++] = patchH * j / M_ROWS
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

        val speaking = mode == AvatarMode.SPEAKING && LipSyncBus.sample(tmp)
        val mouthTarget = if (speaking) tmp[0].coerceIn(0f, 1f) else 0f
        mouth += (mouthTarget - mouth) * min(1f, dt * (if (mouthTarget > mouth) 30f else 14f))
        val shapeTarget = if (speaking) tmp[1].coerceIn(-1f, 1f) else 0f
        shape += (shapeTarget - shape) * min(1f, dt * 10f)
        val target = if (speaking) tmp[0] else if (mode == AvatarMode.LISTENING) micLevel * 0.6f else 0f
        voice += (target - voice) * min(1f, dt * 14f)

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

    /** Wind: slow gusts push the hair one way, then the other; the free ends move most, the roots stay. */
    private fun warpHair(bw: Float, bh: Float) {
        val gust = 0.65f * sin(t * 1.15f) + 0.35f * sin(t * 2.35f + 1.3f)      // -1 .. 1, changes direction
        val boost = 1f + 0.5f * voice + (if (action != null) 1.2f * blend else 0f)
        val amp = bw * 0.034f * boost
        var k = 0
        while (k < orig.size) {
            val x = orig[k]; val y = orig[k + 1]
            val fx = x / bw; val fy = y / bh
            // long curls on the left (not the sleeve edge), growing towards the tips
            val wL = sstep(0.42f, 0.30f, fx) * sstep(0.14f, 0.24f, fx) * sstep(0.10f, 0.20f, fy) * sstep(0.62f, 0.40f, fy)
            val lenL = sstep(0.10f, 0.50f, fy)                                     // 0 at the roots, 1 at the tips
            // strand on the right of the face
            val wR = sstep(0.64f, 0.72f, fx) * sstep(0.08f, 0.15f, fy) * sstep(0.32f, 0.20f, fy)
            val lenR = sstep(0.08f, 0.26f, fy)
            // ponytail on top of the head, small
            val wT = sstep(0.36f, 0.42f, fx) * sstep(0.66f, 0.56f, fx) * sstep(0.10f, 0.02f, fy) * 0.5f
            val ph = t * 3.1f + fy * 9f
            val flutter = 0.35f * sin(ph) + 0.15f * sin(t * 5.3f + fx * 11f)
            val sway = gust + flutter
            val dx = amp * (wL * lenL + wR * lenR * 0.7f + wT) * sway
            val dy = amp * 0.22f * (wL * lenL + wR * lenR) * (0.5f * sin(t * 1.6f + fy * 5f) - 0.25f * abs(gust))
            warped[k] = x + dx
            warped[k + 1] = y + dy
            k += 2
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.clipRect(0f, 0f, w, h)

        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        // Fixed placement: fills the width, cut edge (mid-thigh) sits just below the bottom of the view.
        val over = h * 0.06f
        val fit = min(w / bw, (h + over) / bh)
        val dw = bw * fit; val dh = bh * fit
        val left = (w - dw) / 2f
        val bottom = h + over
        val top = bottom - dh
        val pivX = w / 2f; val pivY = bottom

        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // ---- body pose: NOTHING moves unless an action was commanded ----
        var tx = 0f; var ty = 0f; var rot = 0f; var sy = 1f; var skew = 0f; var yaw = 0f
        val a = action
        if (a != null && blend > 0f) {
            val p = actionT; val b = blend; val tp = 2f * PI.toFloat()
            when (a) {
                CharacterAction.DANCE -> {
                    val beat = sin(p * tp * 1.8f); val hip = sin(p * tp * 0.9f)
                    ty -= b * h * 0.015f * abs(beat)
                    tx += b * w * 0.03f * hip
                    rot += b * 4.5f * hip
                    skew += b * 0.05f * sin(p * tp * 0.9f + 0.9f)
                }
                CharacterAction.SWAY -> {
                    val s = sin(p * tp * 0.5f)
                    tx += b * w * 0.035f * s; rot += b * 3f * s; skew += b * 0.03f * s
                }
                CharacterAction.SPIN -> {
                    val k = (p / max(0.01f, actionDur)).coerceIn(0f, 1f)
                    yaw = 360f * (k * k * (3f - 2f * k))
                }
                CharacterAction.JUMP -> {
                    val cyc = p / 0.85f; val frac = cyc - cyc.toInt()
                    ty -= b * h * 0.045f * sin(PI.toFloat() * frac)
                }
                CharacterAction.WAVE -> {
                    val s = sin(p * tp * 2.2f)
                    rot += b * 5f * s; tx += b * w * 0.01f * s
                }
                CharacterAction.BOW -> {
                    val k = (p / max(0.01f, actionDur)).coerceIn(0f, 1f)
                    val d = sin(PI.toFloat() * k)
                    sy -= 0.05f * d; rot += 4f * d
                }
                CharacterAction.STOP -> {}
            }
        }

        m.reset()
        m.postScale(fit, fit)
        m.postTranslate(left, top)
        if (sy != 1f) m.postScale(1f, sy, pivX, pivY)
        if (skew != 0f) m.postSkew(skew, 0f, pivX, pivY)
        if (rot != 0f) m.postRotate(rot, pivX, pivY)
        if (yaw != 0f) {
            cam.save(); cam.rotateY(yaw); cam.getMatrix(camM); cam.restore()
            val cy = top + dh * 0.4f
            camM.preTranslate(-pivX, -cy); camM.postTranslate(pivX, cy)
            m.postConcat(camM)
        }
        if (tx != 0f || ty != 0f) m.postTranslate(tx, ty)

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
        if (blink > 0.35f) {
            stroke.color = Color.argb((255 * min(1f, blink * 1.2f)).toInt(), 60, 32, 30)
            stroke.strokeWidth = max(1.5f, half * 0.12f)
            rect.set(-half * 0.98f, lidY - (hUp + hLow) * 0.35f, half * 0.98f, lidY + (hUp + hLow) * 0.35f)
            c.drawArc(rect, 8f, 164f, false, stroke)
        }
        c.restore()
    }

    /**
     * Talking: the character's own lower lip + chin slide down along the jaw (drawBitmapMesh), and the gap that
     * opens between the lips shows a dark mouth interior with teeth. Nothing is pasted on top of the lips.
     */
    private fun drawMouth(c: Canvas, bmp: Bitmap) {
        val patch = lowerPatch ?: return
        if (mouth < 0.04f) return
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        val half = patchHalf
        // natural speech opens the jaw only moderately; rounded vowels (oo/oh) open a bit less and narrow
        val open = (mouth * 0.44f * half) * (1f - 0.18f * max(0f, -shape))
        val reach = half * (1.06f + 0.07f * shape)
        val lift = min(half * 0.035f, open * 0.5f)           // hides the old lip crease above the lip line
        c.save()
        c.translate(MOUTH[0] * bw, MOUTH[1] * bh)
        c.rotate(MOUTH[3])

        // 1) the opening between the lips: soft lens, upper edge = lip line, lower edge = moved lower lip
        val n = 28
        path.reset()
        path.moveTo(-reach, 0f)
        for (i in 0..n) {                                    // upper edge (slightly above the lip line)
            val u = -reach + 2f * reach * i / n
            val q = (1f - (u / reach) * (u / reach)).coerceAtLeast(0f)
            path.lineTo(u, -lift * q)
        }
        for (i in n downTo 0) {                              // lower edge (follows the jaw)
            val u = -reach + 2f * reach * i / n
            val q = (1f - (u / reach) * (u / reach)).coerceAtLeast(0f)
            path.lineTo(u, open * q)
        }
        path.close()

        c.save()
        c.clipPath(path)
        // dark, deep mouth interior (lighter towards the bottom where the tongue is)
        shaderM.setScale(1f, max(open + lift, 1f)); shaderM.postTranslate(0f, -lift)
        interiorShader.setLocalMatrix(shaderM)
        inner.shader = interiorShader
        c.drawRect(-reach, -lift - 1f, reach, open + 1f, inner)

        if (open > half * 0.14f) {
            // tongue hint
            inner.shader = null
            inner.color = Color.argb(215, 196, 88, 100)
            rect.set(-reach * 0.40f, open * 0.58f, reach * 0.40f, open * 1.25f)
            c.drawOval(rect, inner)
            // upper teeth, slightly curved, shaded towards the gum line
            val th = open * 0.36f
            shaderM.setScale(1f, max(th, 1f)); shaderM.postTranslate(0f, -lift * 0.2f)
            teethShader.setLocalMatrix(shaderM)
            inner.shader = teethShader
            rect.set(-reach * 0.50f, -lift * 0.3f - th * 0.4f, reach * 0.50f, th)   // top part hides under the upper lip
            c.drawRoundRect(rect, th * 0.9f, th * 0.9f, inner)
        }
        // shadow cast by the upper lip into the mouth: gives depth instead of a flat dark shape
        shaderM.setScale(1f, max(open * 0.55f, 1f)); shaderM.postTranslate(0f, -lift)
        shadeShader.setLocalMatrix(shaderM)
        inner.shader = shadeShader
        c.drawRect(-reach, -lift - 1f, reach, open * 0.55f, inner)
        inner.shader = null
        c.restore()

        // 2) lower lip + chin, moved down (top edge by the full jaw drop, fading to nothing towards the chin)
        var k = 0
        for (j in 0..M_ROWS) for (i in 0..M_COLS) {
            val u = mOrig[k]; val v = mOrig[k + 1]
            val q = (1f - (u / reach) * (u / reach)).coerceAtLeast(0f)
            val f = 1f - sstep(0.35f * half, patchH, v)
            mWarped[k] = u
            mWarped[k + 1] = v + open * q * f
            k += 2
        }
        c.drawBitmapMesh(patch, M_COLS, M_ROWS, mWarped, 0, null, 0, bmpPaint)
        c.restore()
    }
}

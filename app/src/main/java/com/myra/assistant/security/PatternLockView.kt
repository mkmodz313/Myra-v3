package com.myra.assistant.security

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val GRID_SIZE = 3
        const val DOT_COUNT = 9
        const val MIN_PATTERN_LENGTH = 4
        const val DOT_RADIUS_RATIO = 0.06f
        const val TOUCH_RADIUS_RATIO = 0.14f
    }

    enum class PatternState {
        NORMAL, SUCCESS, ERROR
    }

    interface PatternListener {
        fun onPatternStarted()
        fun onPatternComplete(pattern: List<Int>)
        fun onPatternCleared()
    }

    var listener: PatternListener? = null

    private val handler = Handler(Looper.getMainLooper())

    private val dotCenters = Array(DOT_COUNT) { Pair(0f, 0f) }
    private val selectedDots = mutableListOf<Int>()

    private var currentX = -1f
    private var currentY = -1f
    private var state = PatternState.NORMAL

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = minOf(w, h).toFloat()
        val cell = size / GRID_SIZE
        val startX = (w - size) / 2f
        val startY = (h - size) / 2f

        for (i in 0 until DOT_COUNT) {
            val row = i / GRID_SIZE
            val col = i % GRID_SIZE

            dotCenters[i] = Pair(
                startX + col * cell + cell / 2,
                startY + row * cell + cell / 2
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        val radius = size * DOT_RADIUS_RATIO

        linePaint.color = when (state) {
            PatternState.NORMAL -> 0xFFE91E63.toInt()
            PatternState.SUCCESS -> 0xFF00E676.toInt()
            PatternState.ERROR -> 0xFFFF1744.toInt()
        }

        if (selectedDots.size > 1) {
            val path = Path()
            val first = dotCenters[selectedDots.first()]
            path.moveTo(first.first, first.second)

            for (i in 1 until selectedDots.size) {
                val p = dotCenters[selectedDots[i]]
                path.lineTo(p.first, p.second)
            }

            if (currentX >= 0 && state == PatternState.NORMAL) {
                path.lineTo(currentX, currentY)
            }

            canvas.drawPath(path, linePaint)
        }

        for (i in 0 until DOT_COUNT) {
            val selected = selectedDots.contains(i)

            dotPaint.color = when {
                !selected -> 0xFF90A4AE.toInt()
                state == PatternState.SUCCESS -> 0xFF00E676.toInt()
                state == PatternState.ERROR -> 0xFFFF1744.toInt()
                else -> 0xFFE91E63.toInt()
            }

            val (x, y) = dotCenters[i]
            canvas.drawCircle(x, y, radius, dotPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state != PatternState.NORMAL) return true

        val radius = minOf(width, height) * TOUCH_RADIUS_RATIO

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                clearPatternInternal()
                handleTouch(event.x, event.y, radius)
            }

            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                handleTouch(event.x, event.y, radius)
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                currentX = -1f
                currentY = -1f
                finishPattern()
            }
        }

        return true
    }

    private fun handleTouch(x: Float, y: Float, radius: Float) {
        val dot = findNearestDot(x, y, radius) ?: return

        if (!selectedDots.contains(dot)) {
            if (selectedDots.isEmpty()) listener?.onPatternStarted()

            addSkippedDot(dot)
            selectedDots.add(dot)
            invalidate()
        }
    }

    /**
     * FIX: skipped middle dot auto-select
     */
    private fun addSkippedDot(newDot: Int) {
        if (selectedDots.isEmpty()) return

        val last = selectedDots.last()

        val lastRow = last / GRID_SIZE
        val lastCol = last % GRID_SIZE

        val newRow = newDot / GRID_SIZE
        val newCol = newDot % GRID_SIZE

        val midRow = (lastRow + newRow) / 2
        val midCol = (lastCol + newCol) / 2

        if (abs(lastRow - newRow) == 2 || abs(lastCol - newCol) == 2) {
            val middle = midRow * GRID_SIZE + midCol
            if (!selectedDots.contains(middle)) {
                selectedDots.add(middle)
            }
        }
    }

    private fun findNearestDot(x: Float, y: Float, radius: Float): Int? {
        for (i in dotCenters.indices) {
            val (cx, cy) = dotCenters[i]
            val dist = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
            if (dist <= radius) return i
        }
        return null
    }

    private fun finishPattern() {
        if (selectedDots.size >= MIN_PATTERN_LENGTH) {
            listener?.onPatternComplete(selectedDots.toList())
        } else {
            showError()
        }
    }

    fun showError() {
        state = PatternState.ERROR
        invalidate()

        handler.postDelayed({
            clearPattern()
        }, 900)
    }

    fun showSuccess() {
        state = PatternState.SUCCESS
        invalidate()

        handler.postDelayed({
            clearPattern()
        }, 700)
    }

    fun clearPattern() {
        clearPatternInternal()
        state = PatternState.NORMAL
        invalidate()
        listener?.onPatternCleared()
    }

    private fun clearPatternInternal() {
        selectedDots.clear()
        currentX = -1f
        currentY = -1f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}
package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import kotlin.math.sin
import kotlin.math.abs

// ─── WaveformView ────────────────────────────────────────────────────────────
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var amplitude = 0f
    private var phase = 0f
    private var isAnimating = false

    private val waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.FILL
    }

    private val barCount = 20
    private val barHeights = FloatArray(barCount) { 0.1f }
    private var targetHeights = FloatArray(barCount) { 0.1f }

    fun startAnimation() {
        isAnimating = true
        waveAnimator.start()
    }

    fun stopAnimation() {
        isAnimating = false
        waveAnimator.cancel()
        amplitude = 0f
        invalidate()
    }

    fun setAmplitude(rms: Float) {
        amplitude = ((rms + 10f) / 20f).coerceIn(0f, 1f)
        updateBarHeights()
    }

    fun updateAmplitude(rms: Float) {
        amplitude = rms.coerceIn(0f, 1f)
        if (!isAnimating && amplitude > 0.01f) startAnimation()
        updateBarHeights()
    }

    private fun updateBarHeights() {
        for (i in 0 until barCount) {
            val wave = sin(i * 0.5f + phase)
            targetHeights[i] = (0.1f + amplitude * 0.9f * abs(wave.toFloat()))
                .coerceIn(0.05f, 1f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isAnimating) return
        val w = width.toFloat()
        val h = height.toFloat()
        val barWidth = w / (barCount * 2f)
        val spacing = barWidth

        for (i in 0 until barCount) {
            barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.3f
            val barH = h * barHeights[i]
            val left = i * (barWidth + spacing) + spacing / 2
            val top = (h - barH) / 2
            val right = left + barWidth
            val bottom = top + barH

            val alpha = (180 + (75 * barHeights[i])).toInt().coerceIn(0, 255)
            barPaint.color = Color.argb(alpha, 255, 23, 68)
            canvas.drawRoundRect(left, top, right, bottom, 4f, 4f, barPaint)
        }
    }
}

// ─── ChatMessage data class ──────────────────────────────────────────────────
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── ChatAdapter ─────────────────────────────────────────────────────────────
class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        const val VIEW_USER = 0
        const val VIEW_MYRA = 1
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun getLastBotMessage(): String? {
        return messages.lastOrNull { !it.isUser }?.text
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isUser) VIEW_USER else VIEW_MYRA

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserMessageViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_myra, parent, false)
            MyraMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(msg)
            is MyraMessageViewHolder -> holder.bind(msg)
        }
        // Slide-in animation for newly added messages
        if (position == messages.size - 1) {
            val view = holder.itemView
            val translationX = if (msg.isUser) 80f else -80f
            view.translationX = translationX
            view.alpha = 0f
            view.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    override fun getItemCount() = messages.size

    inner class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val msgText: TextView = view.findViewById(R.id.msgText)
        private val timeText: TextView = view.findViewById(R.id.timeText)

        fun bind(msg: ChatMessage) {
            msgText.text = msg.text
            timeText.text = formatTime(msg.timestamp)

            // ✅ FIX: Ensure text doesn't overflow
            msgText.maxLines = 100
            msgText.isSingleLine = false
        }
    }

    inner class MyraMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val msgText: TextView = view.findViewById(R.id.msgText)
        private val timeText: TextView = view.findViewById(R.id.timeText)

        fun bind(msg: ChatMessage) {
            msgText.text = msg.text
            timeText.text = formatTime(msg.timestamp)

            // ✅ FIX: Ensure text doesn't overflow
            msgText.maxLines = 100
            msgText.isSingleLine = false
        }
    }

    private fun formatTime(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }
}
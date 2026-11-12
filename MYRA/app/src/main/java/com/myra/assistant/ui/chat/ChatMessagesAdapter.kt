package com.myra.assistant.ui.chat

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.data.ChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ChatRow {
    abstract val rowId: Long
    data class Message(val msg: ChatRepository.Msg) : ChatRow() { override val rowId get() = msg.id }
    data class DateHeader(val label: String, val day: Long) : ChatRow() { override val rowId get() = -2L - day }
    object Typing : ChatRow() { override val rowId get() = -1L }
}

class ChatMessagesAdapter : ListAdapter<ChatRow, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val T_MYRA = 0
        private const val T_USER = 1
        private const val T_TYPING = 2
        private const val T_DATE = 3

        private val DIFF = object : DiffUtil.ItemCallback<ChatRow>() {
            override fun areItemsTheSame(a: ChatRow, b: ChatRow) = a.rowId == b.rowId
            override fun areContentsTheSame(a: ChatRow, b: ChatRow) = a == b
        }
    }

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private var lastAnimated = -1
    private var firstLoad = true

    override fun submitList(list: List<ChatRow>?, commitCallback: Runnable?) {
        if (firstLoad && !list.isNullOrEmpty()) {
            firstLoad = false
            lastAnimated = list.size - 1   // history that was restored is shown instantly, only new rows animate
        }
        super.submitList(list, commitCallback)
    }

    override fun getItemViewType(position: Int) = when (val r = getItem(position)) {
        is ChatRow.Message -> if (r.msg.fromUser) T_USER else T_MYRA
        is ChatRow.Typing -> T_TYPING
        is ChatRow.DateHeader -> T_DATE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_USER -> MsgHolder(inf.inflate(R.layout.item_msg_user, parent, false), true)
            T_MYRA -> MsgHolder(inf.inflate(R.layout.item_msg_myra, parent, false), false)
            T_TYPING -> TypingHolder(inf.inflate(R.layout.item_msg_typing, parent, false))
            else -> DateHolder(inf.inflate(R.layout.item_msg_date, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ChatRow.Message -> {
                val h = holder as MsgHolder
                h.text.text = row.msg.text
                h.time.text = timeFmt.format(Date(row.msg.time))
                h.ticks?.visibility = if (row.msg.streaming) View.INVISIBLE else View.VISIBLE
            }
            is ChatRow.DateHeader -> (holder as DateHolder).text.text = row.label
            is ChatRow.Typing -> {}
        }
        val v = holder.itemView
        if (position > lastAnimated) {
            lastAnimated = position
            v.alpha = 0f
            v.translationY = 48f
            v.animate().alpha(1f).translationY(0f).setDuration(280).start()
        } else {
            v.animate().cancel()
            v.alpha = 1f
            v.translationY = 0f
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        (holder as? TypingHolder)?.start()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        (holder as? TypingHolder)?.stop()
    }

    class MsgHolder(v: View, user: Boolean) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.msgText)
        val time: TextView = v.findViewById(R.id.msgTime)
        val ticks: TextView? = if (user) v.findViewById(R.id.msgTicks) else null
    }

    class DateHolder(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.dateText)
    }

    class TypingHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val dots = listOf<View>(v.findViewById(R.id.dot1), v.findViewById(R.id.dot2), v.findViewById(R.id.dot3))
        private val anims = ArrayList<ObjectAnimator>()

        fun start() {
            stop()
            dots.forEachIndexed { i, d ->
                val a = ObjectAnimator.ofFloat(d, View.ALPHA, 0.25f, 1f).apply {
                    duration = 450
                    startDelay = i * 150L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                }
                anims.add(a)
                a.start()
            }
        }

        fun stop() {
            anims.forEach { it.cancel() }
            anims.clear()
            dots.forEach { it.alpha = 1f }
        }
    }
}

package com.myra.assistant.ui.chat

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.control.PhoneControl
import com.myra.assistant.data.ChatRepository
import com.myra.assistant.services.ForegroundVoiceService
import com.myra.assistant.ui.avatar.AmbientFxView
import com.myra.assistant.utils.Constants
import com.myra.assistant.utils.prefs
import com.myra.assistant.voice.VoiceStateManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Premium neon chat. Voice transcripts, typed text and MYRA's answers all share [ChatRepository]. */
class ChatActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var statusText: TextView
    private lateinit var fx: AmbientFxView
    private lateinit var emojiPanel: View
    private val adapter = ChatMessagesAdapter()
    private val ui = Handler(Looper.getMainLooper())

    private var messages: List<ChatRepository.Msg> = emptyList()
    private var voiceState: String = Constants.STATE_IDLE

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        var name = "attachment"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) name = c.getString(i)
            }
        } catch (_: Exception) {}
        // Shown in the conversation only; the live Gemini voice session does not receive file contents.
        ChatRepository.addUser("📎 $name")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat)
        ChatRepository.init(applicationContext)

        list = findViewById(R.id.chatList)
        input = findViewById(R.id.chatInput)
        statusText = findViewById(R.id.chatStatus)
        fx = findViewById(R.id.chatFx)
        emojiPanel = findViewById(R.id.emojiPanel)

        list.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        list.adapter = adapter
        list.itemAnimator?.changeDuration = 0   // streaming text updates must not flicker

        com.myra.assistant.ui.NetworkBanner.attach(this)
        setupInsets()
        setupButtons()
        buildEmojiPanel()

        ChatRepository.messages.observe(this) { msgs ->
            messages = msgs
            refresh()
        }
        VoiceStateManager.state.observe(this) { st ->
            voiceState = st ?: Constants.STATE_IDLE
            refresh()
        }
    }

    private fun setupInsets() {
        val header = findViewById<View>(R.id.chatHeader)
        val bar = findViewById<View>(R.id.inputBar)
        val hTop = header.paddingTop
        val bBottom = bar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chatRoot)) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            header.setPadding(header.paddingLeft, hTop + sys.top, header.paddingRight, header.paddingBottom)
            bar.setPadding(bar.paddingLeft, bar.paddingTop, bar.paddingRight, bBottom + maxOf(sys.bottom, ime.bottom))
            if (ime.bottom > 0) scrollToEnd()
            insets
        }
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.chatBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnSend).setOnClickListener { sendTyped() }
        findViewById<ImageButton>(R.id.btnEmoji).setOnClickListener {
            emojiPanel.visibility = if (emojiPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<ImageButton>(R.id.btnAttach).setOnClickListener { pickFile.launch("*/*") }
        findViewById<ImageButton>(R.id.btnCamera).setOnClickListener {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            } catch (_: Exception) {
                ChatRepository.addAssistant("⚠️ Camera app open nahi ho paaya.")
            }
        }
        findViewById<ImageButton>(R.id.chatMenu).setOnClickListener { v ->
            PopupMenu(this, v).apply {
                menu.add(0, 2, 1, "Clear chat")
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        2 -> AlertDialog.Builder(this@ChatActivity)
                            .setTitle("Clear chat?")
                            .setMessage("Poori chat history delete ho jayegi.")
                            .setPositiveButton("Clear") { _, _ -> ChatRepository.clear() }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    true
                }
            }.show()
        }
    }

    private fun buildEmojiPanel() {
        val row = findViewById<LinearLayout>(R.id.emojiRow)
        "😊 😍 🥰 😘 ❤️ 💖 💕 😂 🤗 😎 🙏 👍 👋 🌹 ✨ 🔥 🎉 😢 😮 🤔 😴 💜 🦋 🌸".split(" ").forEach { e ->
            row.addView(TextView(this).apply {
                text = e
                textSize = 26f
                gravity = Gravity.CENTER
                setPadding(14, 0, 14, 0)
                setOnClickListener { input.append(e) }
            })
        }
    }

    // ── sending ─────────────────────────────────────────────────────────────────────────────────
    private fun sendTyped() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        ChatRepository.addUser(text)

        // Phone-control commands ("open WhatsApp", "open a chat with +92…", "yes") are handled locally.
        val out = PhoneControl.handle(this, text)
        if (out.handled) {
            ChatRepository.addAssistant(out.reply)
            return
        }
        sendToGemini(text, 0)
    }

    private fun sendToGemini(text: String, attempt: Int) {
        val key = com.myra.assistant.utils.ApiKeys.gemini(this)
        if (key.isEmpty()) {
            ChatRepository.addAssistant("⚠️ API Key required. Settings → Gemini API Key daalein.")
            return
        }
        val svc = ForegroundVoiceService.instance
        if (svc == null) {
            if (attempt == 0) try { ContextCompat.startForegroundService(this, Intent(this, ForegroundVoiceService::class.java)) } catch (e: Exception) { }
        } else if (svc.isGeminiConnected()) {
            svc.sendTextToGemini(text)
            return
        }
        if (attempt >= 14) {
            ChatRepository.addAssistant("⚠️ MYRA se connection nahi ban paaya. Internet check karke dobara bhejein.")
            return
        }
        ui.postDelayed({ if (!isDestroyed) sendToGemini(text, attempt + 1) }, 700)
    }

    // ── rendering ───────────────────────────────────────────────────────────────────────────────
    private fun refresh() {
        val rows = ArrayList<ChatRow>()
        var lastDay = -1L
        val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        for (m in messages) {
            val cal = Calendar.getInstance().apply { timeInMillis = m.time }
            val day = cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
            if (day != lastDay) {
                lastDay = day
                rows.add(ChatRow.DateHeader(dayLabel(m.time, dayFmt), day))
            }
            rows.add(ChatRow.Message(m))
        }
        val streamingAssistant = messages.lastOrNull()?.let { it.streaming && !it.fromUser } == true
        val thinking = voiceState == Constants.STATE_THINKING && !streamingAssistant
        if (thinking) rows.add(ChatRow.Typing)

        statusText.text = when {
            thinking -> "typing…"
            voiceState == Constants.STATE_SPEAKING -> "speaking…"
            voiceState == Constants.STATE_LISTENING -> "listening…"
            else -> "Online"
        }

        val nearBottom = !list.canScrollVertically(1)
        adapter.submitList(rows) { if (nearBottom || thinking) scrollToEnd() }
    }

    private fun dayLabel(t: Long, fmt: SimpleDateFormat): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = t }
        fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        if (same(now, c)) return "Today"
        now.add(Calendar.DAY_OF_YEAR, -1)
        if (same(now, c)) return "Yesterday"
        return fmt.format(Date(t))
    }

    private fun scrollToEnd() {
        if (adapter.itemCount > 0) list.smoothScrollToPosition(adapter.itemCount - 1)
    }

    override fun onResume() {
        super.onResume()
        fx.start()
    }

    override fun onPause() {
        fx.stop()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

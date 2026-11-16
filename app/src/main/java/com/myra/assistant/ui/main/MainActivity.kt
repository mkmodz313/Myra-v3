package com.myra.assistant.ui.main

import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.view.WindowManager
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.security.BiometricManager
import com.myra.assistant.security.SecuritySettingsActivity
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.services.ForegroundVoiceService
import com.myra.assistant.ui.avatar.AmbientFxView
import com.myra.assistant.ui.avatar.AvatarMode
import com.myra.assistant.ui.avatar.AvatarStageView
import com.myra.assistant.ui.avatar.NeonWaveformView
import com.myra.assistant.ui.chat.ChatActivity
import com.myra.assistant.wallpaper.WallpaperActivity
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.utils.Constants
import com.myra.assistant.utils.Logger
import com.myra.assistant.utils.PermissionUtils
import com.myra.assistant.utils.prefs
import com.myra.assistant.viewmodel.MainViewModel
import com.myra.assistant.voice.VoiceStateManager
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "MAIN"
    private val viewModel: MainViewModel by viewModels()

    private lateinit var avatarStage: AvatarStageView
    private lateinit var characterView: com.myra.assistant.ui.avatar.CharacterView
    private lateinit var waveformView: NeonWaveformView
    private lateinit var ambientFx: AmbientFxView
    private lateinit var micGlow: View
    private lateinit var chatOverlay: View
    private lateinit var panelMessageRow: View
    private lateinit var panelMessageIcon: ImageView
    private lateinit var panelMessage: TextView
    private lateinit var statusText: TextView
    private lateinit var micButton: ImageButton
    private lateinit var chatRecycler: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var batteryText: TextView
    private lateinit var ramText: TextView
    private lateinit var timeText: TextView

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            updateSystemInfo()
            timeHandler.postDelayed(this, 1000)
        }
    }

    private val responseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text") ?: return
            addBotMessage(text)
        }
    }

    companion object {
        const val PERM_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        initViews()
        applySystemBarInsets()
        updateSystemInfo()
        timeHandler.post(timeRunnable)

        BiometricManager.authenticate(
            this,
            onSuccess = { setupAssistant() },
            onError = { setupAssistant() },
            onFallback = { setupAssistant() }
        )
    }

    private fun setupAssistant() {
        PermissionUtils.requestMissing(this, PERM_CODE)
        observeViewModel()
        observeVoiceState()
        startVoiceService()
        checkAccessibility()
    }

    private fun initViews() {
        avatarStage = findViewById(R.id.avatarStage)
        characterView = findViewById(R.id.characterView)
        waveformView = findViewById(R.id.waveformView)
        ambientFx   = findViewById(R.id.ambientFx)
        micGlow     = findViewById(R.id.micGlow)
        chatOverlay = findViewById(R.id.chatOverlay)
        panelMessageRow = findViewById(R.id.panelMessageRow)
        panelMessageIcon = findViewById(R.id.panelMessageIcon)
        panelMessage = findViewById(R.id.panelMessage)
        statusText  = findViewById(R.id.statusText)
        micButton   = findViewById(R.id.micButton)
        chatRecycler = findViewById(R.id.chatRecycler)
        batteryText = findViewById(R.id.batteryText)
        ramText     = findViewById(R.id.ramText)
        timeText    = findViewById(R.id.timeText)

        chatAdapter = ChatAdapter()
        chatRecycler.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        chatRecycler.adapter = chatAdapter

        micButton.setOnClickListener {
            val svc = ForegroundVoiceService.instance
            if (svc != null) {
                val text = "Hey MYRA"
                svc.sendTextToGemini(text)
                addUserMessage(text)
            } else {
                startVoiceService()
            }
        }

        micButton.setOnLongClickListener {
            ForegroundVoiceService.instance?.reconnectGemini()
            addBotMessage("Reconnecting...")
            true
        }

        findViewById<ImageButton>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        com.myra.assistant.ui.NetworkBanner.attach(this)
        WelcomeDialog.maybeShow(this)

        // Fixed 4-button navigation card: CHAT · VOICE (big, centre = micButton above) · WALLPAPER
        bindNavButton(findViewById(R.id.navChat)) {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        bindNavButton(findViewById(R.id.navWallpaper)) {
            startActivity(Intent(this, WallpaperActivity::class.java))
        }
        bindNavButton(micButton) { /* click handled by the existing mic listener above */ }
    }

    /** Press feedback (scale + light haptic) for the 3D navigation buttons; never replaces an existing click listener. */
    private fun bindNavButton(v: View, onClick: () -> Unit) {
        val hasClick = v.hasOnClickListeners()
        if (!hasClick) v.setOnClickListener { onClick() }
        v.setOnTouchListener { view, ev ->
            when (ev.action) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    /** Keeps the header and the bottom navigation clear of the status / navigation bars (edge-to-edge scene). */
    private fun applySystemBarInsets() {
        val header = listOf(R.id.crownTop, R.id.batteryBlock, R.id.clockBlock).map { findViewById<View>(it) }
        val hint = findViewById<View>(R.id.navWrap)
        fun View.lp() = layoutParams as android.view.ViewGroup.MarginLayoutParams
        val topBase = header.map { it.lp().topMargin }
        val bottomBase = hint.lp().bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.forEachIndexed { i, v ->
                v.lp().topMargin = topBase[i] + bars.top
                v.requestLayout()
            }
            hint.lp().bottomMargin = bottomBase + bars.bottom
            hint.requestLayout()
            insets
        }
    }

    private fun observeVoiceState() {
        // The avatar, rings and waveform follow the real VoiceStateManager state.
        VoiceStateManager.state.observe(this) { state ->
            val mode = AvatarMode.fromVoiceState(state)
            avatarStage.setMode(mode)
            characterView.setMode(mode)
            waveformView.setMode(mode)
            when (state) {
                Constants.STATE_LISTENING -> micButton.setImageResource(R.drawable.ic_mic_neon_on)
                else -> micButton.setImageResource(R.drawable.ic_mic_neon_off)
            }
            micGlow.alpha = if (mode == AvatarMode.IDLE) 0.45f else 0.8f
        }
        // Microphone amplitude (only produced while the user is being heard).
        VoiceStateManager.amplitude.observe(this) { amp ->
            avatarStage.setMicLevel(amp)
            characterView.setMicLevel(amp)
            waveformView.setMicLevel(amp)
        }
        VoiceStateManager.statusMessage.observe(this) { msg ->
            statusText.text = msg
        }
    }

    private fun observeViewModel() {
        viewModel.aiResponse.observe(this) { text ->
            if (!text.isNullOrBlank()) addBotMessage(text)
        }
    }

    private fun startVoiceService() {
        val apiKey = com.myra.assistant.utils.ApiKeys.gemini(this)
        if (apiKey.isEmpty()) {
            addBotMessage("⚠️ API Key required. Please go to Settings → Enter Gemini API Key.")
            return
        }
        try { ContextCompat.startForegroundService(this, Intent(this, ForegroundVoiceService::class.java)) } catch (e: Exception) { }
        Logger.d(TAG, "Voice service started")
    }

    private fun checkAccessibility() {
        if (!AccessibilityHelperService.isEnabled(this)) {
            addBotMessage("⚠️ Enable Accessibility Service for app control. Settings → Accessibility.")
        }
    }

    fun addUserMessage(text: String) = runOnUiThread {
        chatAdapter.addMessage(ChatMessage(text, true))
        chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
    }

    fun addBotMessage(text: String) = runOnUiThread {
        chatAdapter.addMessage(ChatMessage(text, false))
        chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
        showPanelMessage(text)
    }

    /** Latest MYRA message shown inside the status panel; warnings (⚠️) get the neon warning icon. */
    private fun showPanelMessage(text: String) {
        val isWarning = text.startsWith("⚠")
        panelMessage.text = if (isWarning) text.removePrefix("⚠️").removePrefix("⚠").trim() else text
        panelMessageIcon.visibility = if (isWarning) View.VISIBLE else View.GONE
        panelMessageRow.visibility = View.VISIBLE
    }

    private fun updateSystemInfo() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeText.text = sdf.format(Date())
        val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        batteryText.text = "${bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0}%"
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        ramText.text = "${(mi.totalMem - mi.availMem) / 1048576}MB"
    }

    override fun onResume() {
        super.onResume()
        // (Re)start the shared animation clock only while the screen is visible.
        avatarStage.start()
        characterView.start()
        waveformView.start()
        ambientFx.start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(responseReceiver, IntentFilter("MYRA_RESPONSE"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(responseReceiver, IntentFilter("MYRA_RESPONSE"))
        }
    }

    override fun onPause() {
        // Stop every animation loop so nothing runs in the background (zero CPU while paused).
        avatarStage.stop()
        characterView.stop()
        waveformView.stop()
        ambientFx.stop()
        super.onPause()
        try { unregisterReceiver(responseReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        timeHandler.removeCallbacks(timeRunnable)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startVoiceService()
        }
    }
}

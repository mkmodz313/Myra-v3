package com.myra.assistant.security

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.myra.assistant.R
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.utils.LiveAudioManager
import java.util.Locale

/**
 * SecuritySettingsActivity — MYRA Security Configuration
 */
class SecuritySettingsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var appLockSwitch: Switch
    private lateinit var appLockStatusText: TextView
    private lateinit var setPinBtn: Button
    private lateinit var pinStatusText: TextView
    private lateinit var setPatternBtn: Button
    private lateinit var patternStatusText: TextView
    private lateinit var setVoiceBtn: Button
    private lateinit var voiceStatusText: TextView
    private lateinit var selectAppsBtn: Button
    private lateinit var usageStatsBtn: Button
    private lateinit var overlayPermissionBtn: Button
    private lateinit var fingerprintSwitch: Switch
    private lateinit var deviceLockSwitch: Switch
    private lateinit var privateModeSwitch: Switch
    private lateinit var privateModeStatusText: TextView
    private lateinit var encryptionStatusText: TextView

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var firstPattern: List<Int>? = null
    private var geminiClient: GeminiLiveClient? = null
    private var liveAudioManager: LiveAudioManager? = null
    private var isGeminiConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_settings)
        initViews()
        liveAudioManager = LiveAudioManager(this)
        initGemini()
        tts = TextToSpeech(this, this)
        loadCurrentStatus()
        setupListeners()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun initViews() {
        appLockSwitch      = findViewById(R.id.appLockSwitch)
        appLockStatusText  = findViewById(R.id.appLockStatusText)
        selectAppsBtn      = findViewById(R.id.selectAppsBtn)
        usageStatsBtn      = findViewById(R.id.usageStatsBtn)
        overlayPermissionBtn = findViewById(R.id.overlayPermissionBtn)
        fingerprintSwitch     = findViewById(R.id.fingerprintSwitch)
        deviceLockSwitch      = findViewById(R.id.deviceLockSwitch)
        setPinBtn          = findViewById(R.id.setPinBtn)
        pinStatusText      = findViewById(R.id.pinStatusText)
        setPatternBtn      = findViewById(R.id.setPatternBtn)
        patternStatusText  = findViewById(R.id.patternStatusText)
        setVoiceBtn        = findViewById(R.id.setVoiceBtn)
        voiceStatusText    = findViewById(R.id.voiceStatusText)
        privateModeSwitch  = findViewById(R.id.privateModeSwitch)
        privateModeStatusText = findViewById(R.id.privateModeStatusText)
        encryptionStatusText  = findViewById(R.id.encryptionStatusText)
    }

    private fun loadCurrentStatus() {
        val lockOn = SecurityManager.isAppLockEnabled(this) || PatternManager.isPatternLockEnabled(this)
        appLockSwitch.isChecked = lockOn
        appLockStatusText.text = if (lockOn) "🔒 App Lock ON" else "🔓 App Lock OFF"
        appLockStatusText.setTextColor(if (lockOn) 0xFF00E676.toInt() else 0xFF888888.toInt())

        checkPermissions()
        
        pinStatusText.text = if (SecurityManager.hasPin(this)) "✅ PIN is set" else "❌ PIN not set"
        pinStatusText.setTextColor(if (SecurityManager.hasPin(this)) 0xFF00E676.toInt() else 0xFF888888.toInt())

        patternStatusText.text = if (PatternManager.isPatternSet(this)) "✅ Pattern is set" else "❌ Pattern not set"
        patternStatusText.setTextColor(if (PatternManager.isPatternSet(this)) 0xFF00E676.toInt() else 0xFF888888.toInt())

        voiceStatusText.text = if (SecurityManager.hasVoicePassphrase(this)) "✅ Voice passphrase set" else "❌ Not set"
        voiceStatusText.setTextColor(if (SecurityManager.hasVoicePassphrase(this)) 0xFF00E676.toInt() else 0xFF888888.toInt())

        // Check Biometric hardware
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            fingerprintSwitch.visibility = View.VISIBLE
            fingerprintSwitch.isChecked = SecurityManager.isBiometricEnabled(this)
        } else {
            fingerprintSwitch.visibility = View.GONE
            SecurityManager.setBiometricEnabled(this, false)
        }

        deviceLockSwitch.isChecked = SecurityManager.isDeviceLockEnabled(this)

        val pm = SecurityManager.isPrivateModeActive(this)
        privateModeSwitch.isChecked = pm
        privateModeStatusText.text = if (pm) "🙈 Private Mode ON" else "👁️ Private Mode OFF"
        privateModeStatusText.setTextColor(if (pm) 0xFFFFAB40.toInt() else 0xFF888888.toInt())

        encryptionStatusText.text = "🔐 AES-256-GCM (Android Keystore) — Always ON"
        encryptionStatusText.setTextColor(0xFF00E676.toInt())
    }

    private fun setupListeners() {
        appLockSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!SecurityManager.hasPin(this) && !PatternManager.isPatternSet(this)) {
                    appLockSwitch.isChecked = false
                    toast("Pehle PIN ya Pattern set karo")
                    speak("Pehle PIN ya pattern set karo tab lock enable hoga", true)
                    return@setOnCheckedChangeListener
                }
                SecurityManager.setAppLockEnabled(this, true)
                if (PatternManager.isPatternSet(this)) {
                    PatternManager.enablePatternLock(this)
                }
                loadCurrentStatus()
                speak("App lock on ho gaya!", true)
            } else {
                SecurityManager.setAppLockEnabled(this, false)
                PatternManager.disablePatternLock(this)
                loadCurrentStatus()
                AppLockActivity.isUnlockedThisSession = true
                speak("App lock band kar diya", true)
            }
        }
        setPinBtn.setOnClickListener { showPinSetupDialog() }
        setPatternBtn.setOnClickListener { startPatternSetup() }
        setVoiceBtn.setOnClickListener { showVoiceSetupDialog() }

        fingerprintSwitch.setOnCheckedChangeListener { _, checked ->
            SecurityManager.setBiometricEnabled(this, checked)
            speak(if (checked) "Fingerprint unlock enable ho gaya" else "Fingerprint unlock band kar diya", true)
        }

        deviceLockSwitch.setOnCheckedChangeListener { _, checked ->
            SecurityManager.setDeviceLockEnabled(this, checked)
            speak(if (checked) "System screen lock enable ho gaya" else "System lock band kar diya", true)
        }

        selectAppsBtn.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }
        usageStatsBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        overlayPermissionBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
        privateModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                SecurityManager.enablePrivateMode(this)
                privateModeStatusText.text = "🙈 Private Mode ON — Chat history hidden"
                privateModeStatusText.setTextColor(0xFFFFAB40.toInt())
                speak("Private mode on. Chat history chhup jayegi.", true)
            } else {
                SecurityManager.disablePrivateMode(this)
                privateModeStatusText.text = "👁️ Private Mode OFF"
                privateModeStatusText.setTextColor(0xFF888888.toInt())
                speak("Private mode band.", true)
            }
        }
    }

    private fun showPinSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_pin, null)
        val newPinInput  = dialogView.findViewById<EditText>(R.id.newPinInput)
        val confPinInput = dialogView.findViewById<EditText>(R.id.confirmPinInput)
        val errorText    = dialogView.findViewById<TextView>(R.id.pinErrorText)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Set PIN")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin  = newPinInput.text.toString().trim()
                val conf = confPinInput.text.toString().trim()
                when {
                    pin.length != 4 -> {
                        errorText.text = "PIN must be exactly 4 digits"
                        errorText.visibility = View.VISIBLE
                    }
                    pin != conf -> {
                        errorText.text = "PINs do not match"
                        errorText.visibility = View.VISIBLE
                    }
                    else -> {
                        SecurityManager.setPin(this, pin)
                        SecurityManager.setAppLockEnabled(this, true)
                        loadCurrentStatus()
                        speak("PIN set ho gaya!", true)
                        dialog.dismiss()
                    }
                }
            }
            if (SecurityManager.hasPin(this)) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    SecurityManager.removePin(this)
                    loadCurrentStatus()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun startPatternSetup() {
        val intent = Intent(this, PatternSetupActivity::class.java)
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            loadCurrentStatus()
        }
    }

    private fun showVoiceSetupDialog() {
        // ... (rest of the voice setup logic)
    }

    private fun initGemini() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isEmpty()) return

        geminiClient = GeminiLiveClient(apiKey, "You are MYRA's security voice. Keep responses very short and professional.", object : GeminiLiveClient.LiveListener {
            override fun onAudioReceived(data: ByteArray) {
                liveAudioManager?.playChunk(data)
            }
            override fun onTextReceived(text: String) {}
            override fun onConnected() { isGeminiConnected = true }
            override fun onTurnComplete() {}
            override fun onError(msg: String) { isGeminiConnected = false }
        })
        geminiClient?.start()
    }

    private fun checkPermissions() {
        val usageStatsGranted = isUsageStatsEnabled()
        usageStatsBtn.text = if (usageStatsGranted) "✅ Usage Stats Allowed" else "Allow Usage Stats"
        usageStatsBtn.isEnabled = !usageStatsGranted

        val overlayGranted = Settings.canDrawOverlays(this)
        overlayPermissionBtn.text = if (overlayGranted) "✅ Overlay Allowed" else "Allow Display Over Apps"
        overlayPermissionBtn.isEnabled = !overlayGranted
    }

    private fun isUsageStatsEnabled(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun speak(text: String, hindi: Boolean) {
        if (isGeminiConnected) {
            geminiClient?.sendTextMessage(text)
        } else if (isTtsReady) {
            tts?.language = if (hindi) Locale("hi", "IN") else Locale.ENGLISH
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SEC_TTS_${System.currentTimeMillis()}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        tts?.shutdown()
        speechRecognizer?.destroy()
        geminiClient?.disconnect()
        liveAudioManager?.stop()
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, SecuritySettingsActivity::class.java))
        }
    }
}

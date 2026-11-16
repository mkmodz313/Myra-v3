package com.myra.assistant.security

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.myra.assistant.R
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.utils.LiveAudioManager
import com.myra.assistant.ui.main.MainActivity
import java.util.Locale

/**
 * AppLockActivity — MYRA Complete Lock Screen
 */
class AppLockActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tabPin: TextView
    private lateinit var tabPattern: TextView
    private lateinit var tabVoice: TextView
    private lateinit var tabFinger: TextView
    private lateinit var pinSection: View
    private lateinit var patternSection: View
    private lateinit var voiceSection: View
    private lateinit var fingerSection: View
    private lateinit var pinDisplay: TextView
    private lateinit var pinStatusText: TextView
    private lateinit var pinAttemptsText: TextView
    private lateinit var patternLockView: PatternLockView
    private lateinit var patternInstructionText: TextView
    private lateinit var patternAttemptsText: TextView
    private lateinit var voiceBtn: ImageButton
    private lateinit var voiceStatusText: TextView
    private lateinit var voiceInstructionText: TextView
    private lateinit var fingerBtn: ImageButton
    private lateinit var fingerStatusText: TextView
    private lateinit var lockoutOverlay: LinearLayout
    private lateinit var lockoutTimerText: TextView

    private var enteredPin = ""
    private var tts: TextToSpeech? = null
    private var geminiClient: GeminiLiveClient? = null
    private var liveAudioManager: LiveAudioManager? = null
    private var isGeminiConnected = false
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lockoutRunnable: Runnable? = null
    private var currentTab = Tab.PATTERN

    enum class Tab { PIN, PATTERN, VOICE, FINGER }

    companion object {
        var isUnlockedThisSession = false
        fun launch(context: Context) {
            context.startActivity(Intent(context, AppLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure Fingerprint tab is visible if enabled
        val isFingerprintEnabled = SecurityManager.isBiometricEnabled(this)
        
        val anyLockEnabled = SecurityManager.isAppLockEnabled(this) || 
                            isFingerprintEnabled || 
                            SecurityManager.isDeviceLockEnabled(this)
        
        if (!anyLockEnabled) {
            finish(); return
        }
        setContentView(R.layout.activity_app_lock)
        initViews()
        
        // Force refresh Fingerprint tab visibility
        tabFinger.visibility = if (isFingerprintEnabled) View.VISIBLE else View.GONE
        
        liveAudioManager = LiveAudioManager(this)
        initGemini()
        tts = TextToSpeech(this, this)
        setupTabs()
        setupPinPad()
        setupPattern()
        setupVoice()
        setupFinger()
        checkLockout()

        // Device lock auto-trigger if enabled
        if (SecurityManager.isDeviceLockEnabled(this)) {
            handler.postDelayed({ launchBiometric(true) }, 500)
        }
        
        // Default to first available lock - Priority: Fingerprint > Pattern > PIN
        val defaultTab = when {
            isFingerprintEnabled -> Tab.FINGER
            PatternManager.isPatternSet(this) -> Tab.PATTERN
            SecurityManager.hasPin(this) -> Tab.PIN
            SecurityManager.hasVoicePassphrase(this) -> Tab.VOICE
            else -> Tab.PIN
        }
        switchTab(defaultTab)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts?.language = Locale("hi", "IN")
            // Fallback in case WebSocket doesn't connect quickly
            handler.postDelayed({ 
                if (!isGeminiConnected) {
                    val mode = getSharedPreferences("myra_prefs", MODE_PRIVATE).getString("personality_mode", "gf") ?: "gf"
                    val greeting = if (mode == "gf") "Pehle unlock karo jaan, phir use karne dungi." else "Please unlock to continue."
                    speak(greeting, true) 
                }
            }, 3000)
        }
    }

    private fun initViews() {
        tabPin     = findViewById(R.id.tabPin)
        tabPattern = findViewById(R.id.tabPattern)
        tabVoice   = findViewById(R.id.tabVoice)
        tabFinger  = findViewById(R.id.tabFinger)
        pinSection     = findViewById(R.id.pinSection)
        patternSection = findViewById(R.id.patternSection)
        voiceSection   = findViewById(R.id.voiceSection)
        fingerSection  = findViewById(R.id.fingerSection)
        pinDisplay      = findViewById(R.id.pinDisplay)
        pinStatusText   = findViewById(R.id.pinStatusText)
        pinAttemptsText = findViewById(R.id.pinAttemptsText)
        patternLockView        = findViewById(R.id.patternLockView)
        patternInstructionText = findViewById(R.id.patternInstructionText)
        patternAttemptsText    = findViewById(R.id.patternAttemptsText)
        voiceBtn             = findViewById(R.id.voiceUnlockBtn)
        voiceStatusText      = findViewById(R.id.voiceStatusText)
        voiceInstructionText = findViewById(R.id.voiceInstructionText)
        fingerBtn            = findViewById(R.id.fingerBtn)
        fingerStatusText     = findViewById(R.id.fingerStatusText)
        lockoutOverlay   = findViewById(R.id.lockoutOverlay)
        lockoutTimerText = findViewById(R.id.lockoutTimerText)
    }

    private fun setupTabs() {
        val hasPin = SecurityManager.hasPin(this)
        val hasPattern = PatternManager.isPatternSet(this)
        val hasVoice = SecurityManager.hasVoicePassphrase(this)
        val hasFinger = SecurityManager.isBiometricEnabled(this)

        tabPin.visibility = if (hasPin) View.VISIBLE else View.GONE
        tabPattern.visibility = if (hasPattern) View.VISIBLE else View.GONE
        tabVoice.visibility = if (hasVoice) View.VISIBLE else View.GONE
        tabFinger.visibility = if (hasFinger) View.VISIBLE else View.GONE

        tabPin.setOnClickListener     { switchTab(Tab.PIN) }
        tabPattern.setOnClickListener { switchTab(Tab.PATTERN) }
        tabVoice.setOnClickListener   { switchTab(Tab.VOICE) }
        tabFinger.setOnClickListener  { switchTab(Tab.FINGER) }
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        pinSection.visibility     = if (tab == Tab.PIN)     View.VISIBLE else View.GONE
        patternSection.visibility = if (tab == Tab.PATTERN) View.VISIBLE else View.GONE
        voiceSection.visibility   = if (tab == Tab.VOICE)   View.VISIBLE else View.GONE
        fingerSection.visibility  = if (tab == Tab.FINGER)  View.VISIBLE else View.GONE
        val pink = 0xFFE91E8C.toInt(); val grey = 0xFF888888.toInt()
        tabPin.setTextColor(if (tab == Tab.PIN) pink else grey)
        tabPattern.setTextColor(if (tab == Tab.PATTERN) pink else grey)
        tabVoice.setTextColor(if (tab == Tab.VOICE) pink else grey)
        tabFinger.setTextColor(if (tab == Tab.FINGER) pink else grey)

        if (tab == Tab.FINGER) launchBiometric(false)
    }

    private fun setupPinPad() {
        val map = mapOf(R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8", R.id.btn9 to "9")
        map.forEach { (id, d) -> findViewById<Button>(id)?.setOnClickListener { appendPin(d) } }
        findViewById<ImageButton>(R.id.backspaceBtn)?.setOnClickListener { backspacePin() }
    }

    private fun appendPin(d: String) {
        if (enteredPin.length >= 4) return
        enteredPin += d
        pinDisplay.text = "●".repeat(enteredPin.length).padEnd(4, '○')
        if (enteredPin.length == 4) handler.postDelayed({ submitPin() }, 250)
    }

    private fun backspacePin() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            pinDisplay.text = "●".repeat(enteredPin.length).padEnd(4, '○')
        }
    }

    private fun submitPin() {
        val result = SecurityManager.verifyPin(this, enteredPin)
        if (result is SecurityManager.PinResult.CORRECT) {
            onSuccess()
        } else {
            enteredPin = ""
            pinDisplay.text = "○○○○"
            when (result) {
                SecurityManager.PinResult.NOT_SET -> onSuccess()
                is SecurityManager.PinResult.WRONG -> onPinWrong(result)
                is SecurityManager.PinResult.LOCKED_OUT -> startLockout(result.secondsRemaining)
                else -> {}
            }
        }
    }

    private fun onPinWrong(r: SecurityManager.PinResult.WRONG) {
        pinAttemptsText.text = "Wrong: ${r.attempts}/3"
        pinAttemptsText.setTextColor(if (r.attempts >= 2) 0xFFFF1744.toInt() else 0xFFFFAB40.toInt())
        shakeView(pinDisplay)
        pinStatusText.text = if (r.lockedOut) "🔒 Locked 30s" else "❌ Wrong PIN"
        pinStatusText.setTextColor(0xFFFF1744.toInt())
        if (r.lockedOut) startLockout(30)
        else handler.postDelayed({ pinStatusText.text = "Enter PIN"; pinStatusText.setTextColor(0xFFFFFFFF.toInt()) }, 1500)
        speakWrong(r.attempts, r.lockedOut, "pin")
    }

    private fun setupPattern() {
        patternInstructionText.text = "Draw your unlock pattern"
        patternLockView.listener = object : PatternLockView.PatternListener {
            override fun onPatternStarted() { patternInstructionText.text = "Keep drawing..." }
            override fun onPatternComplete(pattern: List<Int>) {
                handler.postDelayed({ verifyPattern(pattern) }, 150)
            }
            override fun onPatternCleared() { patternInstructionText.text = "Draw your unlock pattern" }
        }
    }

    private fun verifyPattern(pattern: List<Int>) {
        when (val r = PatternManager.verify(this, pattern)) {
            PatternManager.PatternResult.CORRECT -> { patternLockView.showSuccess(); onSuccess() }
            PatternManager.PatternResult.NOT_SET -> onSuccess()
            PatternManager.PatternResult.TOO_SHORT -> {
                patternLockView.showError()
                patternInstructionText.text = "Too short — connect at least 4 dots"
                speak("Kam se kam 4 dots connect karo", true)
                handler.postDelayed({ patternLockView.clearPattern() }, 900)
            }
            is PatternManager.PatternResult.WRONG -> {
                patternLockView.showError()
                val rem = PatternManager.getRemainingAttempts(this)
                patternAttemptsText.text = "Wrong — $rem attempts left"
                patternAttemptsText.setTextColor(if (r.attempts >= 3) 0xFFFF1744.toInt() else 0xFFFFAB40.toInt())
                if (r.lockedOut) startLockout(30)
                else handler.postDelayed({ patternLockView.clearPattern()
                    patternInstructionText.text = "Draw your unlock pattern" }, 900)
                speakWrong(r.attempts, r.lockedOut, "pattern")
            }
            is PatternManager.PatternResult.LOCKED_OUT -> {
                patternLockView.showError()
                startLockout(r.secondsRemaining)
            }
        }
    }

    private fun setupVoice() {
        val has = SecurityManager.hasVoicePassphrase(this)
        voiceInstructionText.text = if (has) "Tap mic & say your passphrase" else "Voice passphrase not configured"
        voiceBtn.alpha = if (has) 1f else 0.4f
        voiceBtn.isEnabled = has
        voiceBtn.setOnClickListener { if (has) startVoiceListen() }
    }

    private fun setupFinger() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS || SecurityManager.isBiometricEnabled(this)) {
            fingerBtn.setOnClickListener { launchBiometric(false) }
            tabFinger.visibility = View.VISIBLE
        } else {
            tabFinger.visibility = View.GONE
            Log.d("MYRA_LOCK", "Biometric not available: $canAuth")
        }
    }

    private fun launchBiometric(allowDeviceCredential: Boolean) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                fingerStatusText.text = "✅ Success!"
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                fingerStatusText.text = "❌ $errString"
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    switchTab(Tab.PATTERN)
                }
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                fingerStatusText.text = "❌ Authentication failed"
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("MYRA Unlock")
            .setSubtitle("Confirm your identity")
            .apply {
                if (SecurityManager.isDeviceLockEnabled(this@AppLockActivity) || allowDeviceCredential) {
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                } else {
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    setNegativeButtonText("Use Pattern/PIN")
                }
            }
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun startVoiceListen() {
        voiceStatusText.text = "🎙️ Listening for passphrase..."
        voiceBtn.setImageResource(R.drawable.ic_mic_on)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                voiceBtn.setImageResource(R.drawable.ic_mic_off)
                if (SecurityManager.verifyVoicePassphrase(this@AppLockActivity, spoken)) {
                    voiceStatusText.text = "✅ Verified!"
                    onSuccess()
                } else {
                    voiceStatusText.text = "❌ Passphrase did not match"
                    shakeView(voiceBtn)
                    speakWrong(1, false, "voice")
                    handler.postDelayed({ voiceStatusText.text = "Tap mic to try again" }, 2000)
                }
            }
            override fun onError(e: Int) {
                voiceBtn.setImageResource(R.drawable.ic_mic_off)
                voiceStatusText.text = "Could not hear — tap to retry"
                speak("Samajh nahi aaya, dobara try karo", true)
            }
            override fun onReadyForSpeech(p: Bundle?) { voiceStatusText.text = "Say your passphrase..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { voiceStatusText.text = "Processing..." }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        })
    }

    private fun onSuccess() {
        isUnlockedThisSession = true
        val prefs = getSharedPreferences("myra_prefs", MODE_PRIVATE)
        val name  = prefs.getString("user_name", "Jaan") ?: "Jaan"
        val mode  = prefs.getString("personality_mode", "gf") ?: "gf"
        val msg = when (mode) {
            "gf"           -> "Lock khul gaya! Aa gaye $name! Welcome back!"
            "professional" -> "Lock khul gaya. Welcome back $name."
            else           -> "Lock khul gaya! Unlock successful."
        }
        speak(msg, true)
        handler.postDelayed({ 
            finishAndRemoveTask()
        }, 1300)
    }

    private fun startLockout(seconds: Long) {
        lockoutOverlay.visibility = View.VISIBLE
        var rem = seconds
        lockoutRunnable?.let { handler.removeCallbacks(it) }
        lockoutRunnable = object : Runnable {
            override fun run() {
                if (rem <= 0) {
                    lockoutOverlay.visibility = View.GONE
                    PatternManager.resetAttempts(this@AppLockActivity)
                    speak("Ab try kar sakte ho", true)
                    return
                }
                lockoutTimerText.text = "Try again in ${rem}s"
                rem--
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(lockoutRunnable!!)
    }

    private fun checkLockout() {
        val rem = PatternManager.getLockoutRemaining(this)
        if (rem > 0) startLockout(rem)
    }

    private fun speakWrong(attempts: Int, lockedOut: Boolean, type: String) {
        val mode = getSharedPreferences("myra_prefs", MODE_PRIVATE)
            .getString("personality_mode", "gf") ?: "gf"
        val rem = 3 - attempts
        val msg = when {
            lockedOut -> when (mode) {
                "gf"  -> "Yaar! Teen baar galat? Ruk ja, 30 second ke liye lock kar diya!"
                "professional" -> "Too many failed attempts. Locked for 30 seconds."
                else  -> "Bahut galat attempts. 30 second ruko."
            }
            type == "voice" -> when (mode) {
                "gf"  -> "Yeh passphrase nahi tha jaan! Sahi bolke try karo."
                "professional" -> "Voice passphrase mismatch. Please try again."
                else  -> "Galat passphrase. Dobara try karo."
            }
            attempts == 1 -> when (mode) {
                "gf"  -> "Arre galat hai! Dhyan se daalo, $rem mauke bache hain."
                "professional" -> "Incorrect. $rem attempts remaining."
                else  -> "Galat! $rem baar aur try kar sakte ho."
            }
            attempts == 2 -> when (mode) {
                "gf"  -> "Phir galat?! Ek aur galat aur lock ho jayega! Soch ke!"
                "professional" -> "Second failure. One attempt remaining before lockout."
                else  -> "Dobara galat! Ek mauka bacha hai!"
            }
            else -> "Galat! Dobara try karo."
        }
        speak(msg, true)
    }

    private fun initGemini() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isEmpty()) return

        val personality = prefs.getString("personality_mode", "gf") ?: "gf"
        val systemPrompt = when(personality) {
            "gf" -> "You are MYRA, the user's caring and emotional girlfriend. Keep security responses short, sweet, and in Hinglish. Use words like 'jaan', 'babu' occasionally but keep it professional for security."
            "professional" -> "You are MYRA, a professional security assistant. Keep responses very short, formal and in English."
            else -> "You are MYRA, a friendly assistant. Keep responses short and balanced."
        }

        geminiClient = GeminiLiveClient(apiKey, systemPrompt, object : GeminiLiveClient.LiveListener {
            override fun onAudioReceived(data: ByteArray) { liveAudioManager?.playChunk(data) }
            override fun onTextReceived(text: String) {}
            override fun onConnected() { 
                isGeminiConnected = true 
                Log.d("MYRA_LOCK", "Gemini WebSocket Connected ✅")
                runOnUiThread {
                    val mode = getSharedPreferences("myra_prefs", MODE_PRIVATE).getString("personality_mode", "gf") ?: "gf"
                    val greeting = if (mode == "gf") "Pehle unlock karo jaan, phir use karne dungi." else "Please unlock to continue."
                    speak(greeting, true)
                }
            }
            override fun onTurnComplete() {}
            override fun onError(msg: String) { 
                isGeminiConnected = false 
                Log.e("MYRA_LOCK", "Gemini WebSocket Error: $msg")
            }
        })
        geminiClient?.start()
    }

    private fun speak(text: String, hindi: Boolean) {
        if (isGeminiConnected) {
            geminiClient?.sendTextMessage(text)
        } else if (isTtsReady) {
            tts?.language = if (hindi) Locale("hi", "IN") else Locale.ENGLISH
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LOCK_${System.currentTimeMillis()}")
        }
    }

    private fun shakeView(v: View) {
        v.animate().translationX(16f).setDuration(55).withEndAction {
            v.animate().translationX(-16f).setDuration(55).withEndAction {
                v.animate().translationX(8f).setDuration(45).withEndAction {
                    v.animate().translationX(0f).setDuration(45).start()
                }.start()
            }.start()
        }.start()
    }

    override fun onBackPressed() { 
        speak("Pehle unlock karo!", true)
        // If they try to back out, go to home screen so they can't access the app
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onDestroy() {
        lockoutRunnable?.let { handler.removeCallbacks(it) }
        tts?.shutdown()
        speechRecognizer?.destroy()
        geminiClient?.disconnect()
        liveAudioManager?.stop()
        super.onDestroy()
    }
}

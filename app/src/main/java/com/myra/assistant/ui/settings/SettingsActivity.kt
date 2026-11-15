package com.myra.assistant.ui.settings

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.myra.assistant.R
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.service.MyraDeviceAdminReceiver
import com.myra.assistant.security.SecuritySettingsActivity


class SettingsActivity : AppCompatActivity() {

    // ── Original Views ──────────────────────────────────────────
    private lateinit var apiKeyInput: EditText
    private lateinit var ttsApiKeyInput: EditText
    private lateinit var userNameInput: EditText
    private lateinit var primeNameInput: EditText
    private lateinit var primeNumberInput: EditText
    private lateinit var personalityGroup: RadioGroup
    private lateinit var voiceTypeGroup: RadioGroup
    private lateinit var liveModeSwitch: Switch
    private lateinit var saveBtn: Button
    private lateinit var accessibilityStatus: TextView
    private lateinit var adminStatusText: TextView
    private lateinit var pickContactBtn: ImageButton

    // ── NEW Views ─────────────────────────────────────────────
    private lateinit var callAnnounceSwitch: Switch
    private lateinit var callAnnounceStatusText: TextView
    private lateinit var grantPermissionsBtn: Button
    private lateinit var setDefaultAssistantBtn: Button
    private lateinit var permissionsStatusText: TextView

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            handleContactResult(contactUri)
        }
    }

    // Required permissions list
    private val allPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyraDeviceAdminReceiver::class.java)

        initViews()
        loadPreferences()
        setupListeners()
        updateStatus()
    }

    private fun initViews() {
        // Original views
        apiKeyInput = findViewById(R.id.apiKeyInput)
        ttsApiKeyInput = findViewById(R.id.ttsApiKeyInput)
        userNameInput = findViewById(R.id.userNameInput)
        primeNameInput = findViewById(R.id.primeNameInput)
        primeNumberInput = findViewById(R.id.primeNumberInput)
        personalityGroup = findViewById(R.id.personalityGroup)
        voiceTypeGroup = findViewById(R.id.voiceTypeGroup)
        liveModeSwitch = findViewById(R.id.liveModeSwitch)
        saveBtn = findViewById(R.id.saveBtn)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        adminStatusText = findViewById(R.id.adminStatusText)
        pickContactBtn = findViewById(R.id.pickContactBtn)

        // NEW views — add these to your XML layout
        callAnnounceSwitch = findViewById(R.id.callAnnounceSwitch)
        callAnnounceStatusText = findViewById(R.id.callAnnounceStatusText)
        grantPermissionsBtn = findViewById(R.id.grantPermissionsBtn)
        setDefaultAssistantBtn = findViewById(R.id.setDefaultAssistantBtn)
        permissionsStatusText = findViewById(R.id.permissionsStatusText)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("api_key", ""))
        ttsApiKeyInput.setText(prefs.getString("tts_api_key", ""))
        userNameInput.setText(prefs.getString("user_name", "Sir"))
        primeNameInput.setText(prefs.getString("prime_name", ""))
        primeNumberInput.setText(prefs.getString("prime_number", ""))

        when (prefs.getString("personality_mode", "gf")) {
            "gf" -> findViewById<RadioButton>(R.id.gfRadio).isChecked = true
            "professional" -> findViewById<RadioButton>(R.id.proRadio).isChecked = true
            else -> findViewById<RadioButton>(R.id.assistantRadio).isChecked = true
        }

        when (prefs.getString("voice_engine", "system")) {
            "elevenlabs" -> findViewById<RadioButton>(R.id.radioEleven).isChecked = true
            else -> findViewById<RadioButton>(R.id.radioSystem).isChecked = true
        }

        liveModeSwitch.isChecked = prefs.getBoolean("live_mode_enabled", false)

        // Call announce (default ON for better UX)
        val announceEnabled = prefs.getBoolean("call_announce_enabled", true)
        callAnnounceSwitch.isChecked = announceEnabled
        updateCallAnnounceStatus(announceEnabled)
    }

    private fun setupListeners() {
        saveBtn.setOnClickListener { savePreferences() }

        pickContactBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        }

        // Original cards
        findViewById<View>(R.id.accessibilityCard).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<View>(R.id.deviceAdminCard).setOnClickListener {
            if (!devicePolicyManager.isAdminActive(componentName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "MYRA needs admin to control system.")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Admin is already active ✅", Toast.LENGTH_SHORT).show()
            }
        }

        // Girlfriend mode + announcements
        val sp2 = getSharedPreferences(com.myra.assistant.utils.Constants.PREFS_NAME, MODE_PRIVATE)
        findViewById<Switch>(R.id.girlfriendSwitch).apply {
            isChecked = sp2.getBoolean("girlfriend_mode", false)
            setOnCheckedChangeListener { _, on ->
                sp2.edit().putBoolean("girlfriend_mode", on).apply()
                com.myra.assistant.services.ForegroundVoiceService.instance?.reconnectGemini()
                Toast.makeText(this@SettingsActivity, if (on) "💖 Girlfriend Mode ON" else "Girlfriend Mode OFF", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Switch>(R.id.chargingSwitch).apply {
            isChecked = sp2.getBoolean("charging_announce", true)
            setOnCheckedChangeListener { _, on -> sp2.edit().putBoolean("charging_announce", on).apply() }
        }
        findViewById<Switch>(R.id.messageSwitch).apply {
            isChecked = sp2.getBoolean("message_announce", true)
            setOnCheckedChangeListener { _, on -> sp2.edit().putBoolean("message_announce", on).apply() }
        }
        findViewById<View>(R.id.notifAccessBtn).setOnClickListener {
            try { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this, "Settings khul nahi saki", Toast.LENGTH_SHORT).show() }
        }

        findViewById<View>(R.id.announceTestBtn).setOnClickListener {
            com.myra.assistant.utils.PowerWatcher.announce(this, true)
            Toast.makeText(this, "Awaaz test chal raha hai...", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.crashLogBtn).setOnClickListener { showCrashLog() }

        findViewById<View>(R.id.designsCard).setOnClickListener {
            startActivity(Intent(this, com.myra.assistant.design.DesignsActivity::class.java))
        }

        findViewById<View>(R.id.adminPanelCard).setOnClickListener {
            startActivity(Intent(this, com.myra.assistant.admin.AdminLoginActivity::class.java))
        }

        findViewById<View>(R.id.securitySettingsCard).setOnClickListener {
            startActivity(Intent(this, SecuritySettingsActivity::class.java))
        }

        // NEW: Call announce switch
        callAnnounceSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateCallAnnounceStatus(isChecked)
        }

        // NEW: Grant permissions button
        grantPermissionsBtn.setOnClickListener {
            checkAndRequestPermissions()
        }

        // NEW: Set default assistant button
        setDefaultAssistantBtn.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                Toast.makeText(this, "MYRA ko Default Assistant chuno 👆", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Settings → Apps → Default Apps → Assistant → MYRA", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * NEW: Check and request all required permissions
     */
    private fun checkAndRequestPermissions() {
        val missing = allPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            Toast.makeText(this, "Sab permissions already granted! ✅", Toast.LENGTH_SHORT).show()
            updatePermissionsStatus()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val granted = grantResults.count { it == PackageManager.PERMISSION_GRANTED }
            val total = permissions.size

            if (granted == total) {
                Toast.makeText(this, "Sab permissions mil gayi! ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "$granted/$total permissions mili ⚠️", Toast.LENGTH_LONG).show()

                val permanentlyDenied = permissions.filterIndexed { index, _ ->
                    grantResults[index] == PackageManager.PERMISSION_DENIED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[index])
                }

                if (permanentlyDenied.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Permissions Required ⚠️")
                        .setMessage("Kuch permissions permanently deny ho gayi hain. Settings se manually enable karo.\n\n" +
                                permanentlyDenied.joinToString("\n") { "• ${it.split('.').last()}" })
                        .setPositiveButton("Open Settings") { _, _ ->
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Settings open nahi ho paya", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            updatePermissionsStatus()
        }
    }

    private fun updateCallAnnounceStatus(enabled: Boolean) {
        callAnnounceStatusText.text = if (enabled) {
            "📢 Call aane pe MYRA naam bolegi"
        } else {
            "🔇 Call announce band hai"
        }
        callAnnounceStatusText.setTextColor(
            if (enabled) 0xFF00E676.toInt() else 0xFFFF1744.toInt()
        )
    }

    private fun updatePermissionsStatus() {
        val missing = allPermissions.count {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        permissionsStatusText.text = when {
            missing == 0 -> "✅ All Permissions Granted"
            missing <= 2 -> "⚠️ $missing permissions pending"
            else -> "❌ $missing permissions missing"
        }
        permissionsStatusText.setTextColor(when {
            missing == 0 -> 0xFF00E676.toInt()
            missing <= 2 -> 0xFFFFA726.toInt()
            else -> 0xFFFF1744.toInt()
        })
    }

    private fun handleContactResult(uri: android.net.Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor?.moveToFirst() == true) {
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            primeNameInput.setText(cursor.getString(nameIndex))
            primeNumberInput.setText(cursor.getString(numIndex).replace(" ", ""))
        }
        cursor?.close()
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE).edit()

        prefs.putString("api_key", apiKeyInput.text.toString().trim())
        prefs.putString("tts_api_key", ttsApiKeyInput.text.toString().trim())
        prefs.putString("user_name", userNameInput.text.toString().trim())
        prefs.putString("prime_name", primeNameInput.text.toString().trim())
        prefs.putString("prime_number", primeNumberInput.text.toString().trim())

        val personality = when (personalityGroup.checkedRadioButtonId) {
            R.id.gfRadio -> "gf"
            R.id.proRadio -> "professional"
            else -> "assistant"
        }
        prefs.putString("personality_mode", personality)

        val voiceEngine = when (voiceTypeGroup.checkedRadioButtonId) {
            R.id.radioEleven -> "elevenlabs"
            else -> "system"
        }
        prefs.putString("voice_engine", voiceEngine)

        prefs.putBoolean("live_mode_enabled", liveModeSwitch.isChecked)
        prefs.putBoolean("call_announce_enabled", callAnnounceSwitch.isChecked)

        prefs.apply()
        Toast.makeText(this, "Settings Saved! MYRA updated. ✅", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateStatus() {
        val enabled = AccessibilityHelperService.isEnabled(this)
        accessibilityStatus.text = if (enabled) "✅ Full Control On" else "❌ Accessibility Off"
        accessibilityStatus.setTextColor(if (enabled) 0xFF00E676.toInt() else 0xFFFF1744.toInt())

        val adminActive = devicePolicyManager.isAdminActive(componentName)
        adminStatusText.text = if (adminActive) "✅ Admin Active" else "❌ Admin Inactive"
        adminStatusText.setTextColor(if (adminActive) 0xFF00E676.toInt() else 0xFFFF1744.toInt())

        updatePermissionsStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        val on = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        findViewById<TextView>(R.id.notifAccessStatus).text =
            if (on) "✅ Notification access ON - message announce chalega" else "⚠️ Notification access OFF - message announce ke liye allow karein"
    }

    private fun showCrashLog() {
        val f = java.io.File(filesDir, com.myra.assistant.MyraApp.CRASH_FILE)
        val text = if (f.exists() && f.length() > 0) f.readText().takeLast(3500) else "Koi crash record nahi hai. 👍"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Last crash report")
            .setMessage(text)
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("MYRA crash", text))
                Toast.makeText(this, "Copy ho gaya", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Clear") { _, _ -> f.delete() }
            .setNegativeButton("Close", null)
            .show()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 200
    }
}
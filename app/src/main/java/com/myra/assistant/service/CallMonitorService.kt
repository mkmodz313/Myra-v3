package com.myra.assistant.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myra.assistant.R
import com.myra.assistant.ui.main.CallAssistantActivity
import java.util.*

class CallMonitorService : Service(), TextToSpeech.OnInitListener {

    private var telephonyManager: TelephonyManager? = null
    private var phoneListener: PhoneStateListener? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var announced = false

    companion object {
        const val ACTION_CALL_ACTIVE = "com.myra.assistant.CALL_ACTIVE"
        const val ACTION_CALL_ENDED = "com.myra.assistant.CALL_ENDED"
        const val ACTION_CALL_RINGING = "com.myra.assistant.CALL_RINGING"
        private const val CHANNEL_ID = "myra_call_channel"
        private const val TAG = "MYRA_CALL"
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForegroundService()

        tts = TextToSpeech(this, this)

        setupPhoneListener()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            ttsReady = true
        }
    }

    private fun setupPhoneListener() {
        telephonyManager =
            getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        phoneListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, number: String?) {
                super.onCallStateChanged(state, number)

                when (state) {

                    TelephonyManager.CALL_STATE_RINGING -> {
                        if (!announced) {
                            announced = true
                            handleIncomingCall(number)
                        }
                    }

                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        sendBroadcast(Intent(ACTION_CALL_ACTIVE))
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {
                        announced = false
                        sendBroadcast(Intent(ACTION_CALL_ENDED))
                    }
                }

                lastState = state
            }
        }

        telephonyManager?.listen(
            phoneListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    }

    private fun handleIncomingCall(number: String?) {
        val callerName = resolveCallerName(number)

        Log.d(TAG, "Incoming: $callerName")

        val intent = Intent(this, CallAssistantActivity::class.java).apply {
            putExtra("CALLER_NAME", callerName)
            putExtra("PHONE_NUMBER", number ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(intent)
    }

    private fun resolveCallerName(number: String?): String {
        if (number.isNullOrEmpty()) return "Unknown Caller"

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Unknown Caller"
        }

        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )

            contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: "Unknown Caller"
                } else {
                    "Unknown Caller"
                }
            } ?: "Unknown Caller"

        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "")
            "Unknown Caller"
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA Running")
            .setContentText("Monitoring calls")
            .setSmallIcon(R.mipmap.img)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(1001, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Call Monitor",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        telephonyManager?.listen(
            phoneListener,
            PhoneStateListener.LISTEN_NONE
        )

        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }
}
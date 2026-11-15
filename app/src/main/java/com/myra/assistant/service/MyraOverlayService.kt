package com.myra.assistant.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.myra.assistant.R
import com.myra.assistant.ui.main.MainActivity

class MyraOverlayService : Service() {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "myra_overlay_channel"
        const val NOTIF_ID = 1001
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isVisible = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW_OVERLAY" -> showOverlay()
            "HIDE_OVERLAY" -> hideOverlay()
            "TOGGLE_OVERLAY" -> if (isVisible) hideOverlay() else showOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (isVisible || overlayView != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_orb, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        windowManager?.addView(overlayView, params)
        isVisible = true

        // Start orb animation
        setupOverlayInteraction()
        startOrbPulse()

        // Auto open main activity
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(mainIntent)
    }

    private fun setupOverlayInteraction() {
        overlayView?.apply {
            val orbContainer = findViewById<FrameLayout>(R.id.orbContainer)
            val closeBtn = findViewById<ImageView>(R.id.closeOverlayBtn)
            val myraLabel = findViewById<TextView>(R.id.respondingLabel)

            closeBtn?.setOnClickListener { hideOverlay() }

            orbContainer?.setOnClickListener {
                val intent = Intent(this@MyraOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }

            // Drag support
            var initialX = 0; var initialY = 0
            var initialTouchX = 0f; var initialTouchY = 0f
            val overlayParams = layoutParams as WindowManager.LayoutParams

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = overlayParams.x
                        initialY = overlayParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        overlayParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        overlayParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, overlayParams)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun startOrbPulse() {
        // Orb pulsing animation is handled in the OrbAnimationView
    }

    fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
        isVisible = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MYRA is running in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA is active ❤️")
            .setContentText("Press power button to activate overlay")
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        hideOverlay()
        super.onDestroy()
    }
}

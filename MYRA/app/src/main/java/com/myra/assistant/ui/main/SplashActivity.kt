package com.myra.assistant.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.myra.assistant.R

/** Short branded splash (full-screen MYRA artwork + animated neon loading bar), then opens the normal home screen. */
class SplashActivity : AppCompatActivity() {

    private var anim: ObjectAnimator? = null
    private var opened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        val loader = findViewById<View>(R.id.splashLoader)
        val baseBottom = loader.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splashRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            loader.setPadding(loader.paddingLeft, loader.paddingTop, loader.paddingRight, baseBottom + bars.bottom)
            insets
        }

        loader.alpha = 0f
        loader.animate().alpha(1f).setDuration(350).start()

        val bar = findViewById<ProgressBar>(R.id.splashBar)
        anim = ObjectAnimator.ofInt(bar, "progress", 0, 1000).apply {
            duration = 1900
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { openHome() }
            })
            start()
        }
    }

    private fun openHome() {
        if (opened || isFinishing || isDestroyed) return
        opened = true
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        anim?.removeAllListeners()
        anim?.cancel()
        super.onDestroy()
    }
}

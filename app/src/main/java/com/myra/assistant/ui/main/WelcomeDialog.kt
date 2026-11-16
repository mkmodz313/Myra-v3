package com.myra.assistant.ui.main

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.myra.assistant.R
import com.myra.assistant.ui.chat.ChatActivity
import com.myra.assistant.utils.Constants

/** Premium neon welcome dialog with the WhatsApp channel button. Shown once per app start (unless "Dobara mat dikhao"). */
object WelcomeDialog {
    const val CHANNEL_URL = "https://whatsapp.com/channel/0029VbDRuV990x2tlVt0FW2Y"
    private const val PREF_HIDE = "welcome_hide"
    private var shownThisProcess = false

    fun maybeShow(activity: AppCompatActivity) {
        if (shownThisProcess) return
        val sp = activity.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (sp.getBoolean(PREF_HIDE, false)) return
        shownThisProcess = true
        Handler(Looper.getMainLooper()).postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) try { show(activity) } catch (_: Exception) { }
        }, 700)
    }

    fun show(activity: AppCompatActivity) {
        val sp = activity.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_welcome)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.8f)
        }
        val dont = d.findViewById<CheckBox>(R.id.welcomeDontShow)
        d.setOnDismissListener { if (dont.isChecked) sp.edit().putBoolean(PREF_HIDE, true).apply() }

        val join = d.findViewById<Button>(R.id.welcomeJoin)
        join.setOnClickListener {
            try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL))) }
            catch (_: Exception) { Toast.makeText(activity, "Link khul nahi saka", Toast.LENGTH_SHORT).show() }
        }
        d.findViewById<Button>(R.id.welcomeStart).setOnClickListener {
            d.dismiss()
            activity.startActivity(Intent(activity, ChatActivity::class.java))
        }
        d.findViewById<View>(R.id.welcomeClose).setOnClickListener { d.dismiss() }

        // entrance + gentle pulse on the channel button
        val card = d.findViewById<View>(R.id.welcomeCard)
        card.alpha = 0f; card.scaleX = 0.86f; card.scaleY = 0.86f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(1.1f)).start()
        ObjectAnimator.ofPropertyValuesHolder(
            join,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.035f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.035f)
        ).apply { duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start() }
        d.show()
    }
}

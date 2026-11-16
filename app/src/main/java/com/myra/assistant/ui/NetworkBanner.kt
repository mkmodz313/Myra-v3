package com.myra.assistant.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myra.assistant.utils.NetworkState

/** Small pill at the top of the screen: "No internet connection" / "Poor network". */
object NetworkBanner {
    fun attach(activity: AppCompatActivity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val density = activity.resources.displayMetrics.density
        val sb = activity.resources.getIdentifier("status_bar_height", "dimen", "android").let {
            if (it > 0) activity.resources.getDimensionPixelSize(it) else (24 * density).toInt()
        }
        val tv = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((18 * density).toInt(), (9 * density).toInt(), (18 * density).toInt(), (9 * density).toInt())
            elevation = 40f
            alpha = 0f
            visibility = View.GONE
        }
        content.addView(tv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).also { it.topMargin = sb + (8 * density).toInt() })

        fun bg(color: Int) = GradientDrawable().apply {
            cornerRadius = 40 * density
            setColor(color)
            setStroke((1.5f * density).toInt(), Color.parseColor("#FFFFD6F8"))
        }

        NetworkState.state.observe(activity) { s ->
            when (s) {
                NetworkState.OFFLINE -> {
                    tv.text = "📶  No internet connection"
                    tv.background = bg(Color.parseColor("#E0B0124A"))
                    tv.visibility = View.VISIBLE
                    tv.animate().alpha(1f).translationY(0f).setDuration(220).start()
                }
                NetworkState.POOR -> {
                    tv.text = "⚠️  Poor network"
                    tv.background = bg(Color.parseColor("#E0B8620C"))
                    tv.visibility = View.VISIBLE
                    tv.animate().alpha(1f).translationY(0f).setDuration(220).start()
                }
                else -> tv.animate().alpha(0f).setDuration(220).withEndAction { tv.visibility = View.GONE }.start()
            }
        }
    }
}

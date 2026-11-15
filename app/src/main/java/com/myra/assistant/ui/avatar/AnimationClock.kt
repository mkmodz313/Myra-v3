package com.myra.assistant.ui.avatar

import android.view.Choreographer
import com.myra.assistant.utils.Constants

/** Visual state of the companion. Mapped 1:1 from the existing VoiceStateManager states. */
enum class AvatarMode {
    IDLE, LISTENING, THINKING, SPEAKING;

    companion object {
        fun fromVoiceState(state: String?): AvatarMode = when (state) {
            Constants.STATE_LISTENING -> LISTENING
            Constants.STATE_THINKING -> THINKING
            Constants.STATE_SPEAKING -> SPEAKING
            else -> IDLE
        }
    }
}

/**
 * One shared Choreographer loop for every MYRA visual (avatar, rings, particles, waveform).
 *
 * - Main thread only.
 * - The loop posts frames ONLY while at least one listener is registered, so when the
 *   Activity is paused/destroyed (all views unregister) there is zero background work.
 */
object AnimationClock {

    interface Listener {
        fun onFrame(frameTimeNanos: Long)
    }

    private val listeners = ArrayList<Listener>()
    private var running = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (listeners.isEmpty()) {
                running = false
                return
            }
            val snapshot = listeners.toTypedArray()
            for (l in snapshot) l.onFrame(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun register(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        if (!running) {
            running = true
            Choreographer.getInstance().postFrameCallback(callback)
        }
    }

    fun unregister(listener: Listener) {
        listeners.remove(listener)
        // The loop stops itself on the next frame when the list is empty.
    }
}

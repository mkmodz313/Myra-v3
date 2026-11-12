package com.myra.assistant.utils

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speaks short announcements (charging, new message) with the phone's text-to-speech voice.
 * Hindi voice present -> the Devanagari sentence is spoken; otherwise the Roman (Hinglish) sentence is spoken so
 * it is never silent.
 */
object Announcer : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var hindiOk = false
    private val waiting = ArrayList<Pair<String, String>>()

    fun speak(context: Context, hindi: String, roman: String) {
        val app = context.applicationContext
        main.post {
            synchronized(this) {
                try {
                    if (tts == null) tts = TextToSpeech(app, this)
                    if (ready) say(hindi, roman) else waiting.add(hindi to roman)
                } catch (_: Throwable) { tts = null }
            }
        }
    }

    @Synchronized
    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) { tts = null; waiting.clear(); return }
        val hi = t.setLanguage(Locale("hi", "IN"))
        hindiOk = hi >= TextToSpeech.LANG_AVAILABLE
        if (!hindiOk) {
            val en = t.setLanguage(Locale("en", "IN"))
            if (en < TextToSpeech.LANG_AVAILABLE) t.setLanguage(Locale.getDefault())
        }
        t.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        ready = true
        waiting.forEach { say(it.first, it.second) }
        waiting.clear()
    }

    private fun say(hindi: String, roman: String) {
        tts?.speak(if (hindiOk) hindi else roman, TextToSpeech.QUEUE_ADD, null, "myra_${System.nanoTime()}")
    }
}

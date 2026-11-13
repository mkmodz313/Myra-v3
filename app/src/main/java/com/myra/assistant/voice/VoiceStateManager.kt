package com.myra.assistant.voice

import androidx.lifecycle.MutableLiveData
import com.myra.assistant.utils.Constants

object VoiceStateManager {

    val state = MutableLiveData(Constants.STATE_IDLE)
    val statusMessage = MutableLiveData("SYSTEM READY")
    val amplitude = MutableLiveData(0f)
    val isMicMuted = MutableLiveData(false)

    // Read by the mic thread: must change instantly (LiveData.postValue lags a frame behind).
    @Volatile private var speakingNow = false
    @Volatile private var micResumeAt = 0L
    private const val MIC_TAIL_MS = 220L   // room echo of the last syllable must fade before the mic re-opens

    private fun leaveSpeaking() {
        if (speakingNow) { speakingNow = false; micResumeAt = System.currentTimeMillis() + MIC_TAIL_MS }
    }

    fun setListening() {
        leaveSpeaking()
        state.postValue(Constants.STATE_LISTENING)
        statusMessage.postValue("LISTENING...")
        isMicMuted.postValue(false)
    }

    fun setThinking() {
        state.postValue(Constants.STATE_THINKING)
        statusMessage.postValue("THINKING...")
    }

    fun setSpeaking() {
        speakingNow = true
        state.postValue(Constants.STATE_SPEAKING)
        statusMessage.postValue("SPEAKING...")
        isMicMuted.postValue(true)
    }

    fun setIdle() {
        leaveSpeaking()
        state.postValue(Constants.STATE_IDLE)
        statusMessage.postValue("SYSTEM READY")
        isMicMuted.postValue(false)
        amplitude.postValue(0f)
    }

    fun setError(msg: String) {
        leaveSpeaking()
        state.postValue(Constants.STATE_IDLE)
        statusMessage.postValue(msg)
    }

    fun updateAmplitude(rms: Float) {
        amplitude.postValue(rms.coerceIn(0f, 1f))
    }

    fun isAiSpeaking() = speakingNow || System.currentTimeMillis() < micResumeAt

    fun isListening() = state.value == Constants.STATE_LISTENING
}

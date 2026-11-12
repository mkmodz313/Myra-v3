package com.myra.assistant.voice

import androidx.lifecycle.MutableLiveData
import com.myra.assistant.utils.Constants

object VoiceStateManager {

    val state = MutableLiveData(Constants.STATE_IDLE)
    val statusMessage = MutableLiveData("SYSTEM READY")
    val amplitude = MutableLiveData(0f)
    val isMicMuted = MutableLiveData(false)

    fun setListening() {
        state.postValue(Constants.STATE_LISTENING)
        statusMessage.postValue("LISTENING...")
        isMicMuted.postValue(false)
    }

    fun setThinking() {
        state.postValue(Constants.STATE_THINKING)
        statusMessage.postValue("THINKING...")
    }

    fun setSpeaking() {
        state.postValue(Constants.STATE_SPEAKING)
        statusMessage.postValue("SPEAKING...")
        isMicMuted.postValue(true)
    }

    fun setIdle() {
        state.postValue(Constants.STATE_IDLE)
        statusMessage.postValue("SYSTEM READY")
        isMicMuted.postValue(false)
        amplitude.postValue(0f)
    }

    fun setError(msg: String) {
        state.postValue(Constants.STATE_IDLE)
        statusMessage.postValue(msg)
    }

    fun updateAmplitude(rms: Float) {
        amplitude.postValue(rms.coerceIn(0f, 1f))
    }

    fun isAiSpeaking() = state.value == Constants.STATE_SPEAKING

    fun isListening() = state.value == Constants.STATE_LISTENING
}

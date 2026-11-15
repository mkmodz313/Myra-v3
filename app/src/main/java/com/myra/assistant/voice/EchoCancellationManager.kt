package com.myra.assistant.voice

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.myra.assistant.utils.Logger

object EchoCancellationManager {
    private val TAG = "ECHO"

    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    fun attach(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
                Logger.d(TAG, "AEC enabled")
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
                Logger.d(TAG, "NS enabled")
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
                Logger.d(TAG, "AGC enabled")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to attach effects: ${e.message}")
        }
    }

    fun release() {
        aec?.release(); aec = null
        ns?.release(); ns = null
        agc?.release(); agc = null
    }
}

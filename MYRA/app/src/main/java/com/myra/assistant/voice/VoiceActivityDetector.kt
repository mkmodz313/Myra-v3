package com.myra.assistant.voice

import com.myra.assistant.utils.AudioUtils
import com.myra.assistant.utils.Logger

/**
 * Simple energy-based Voice Activity Detector
 */
class VoiceActivityDetector(
    private val onSpeechStart: () -> Unit,
    private val onSpeechEnd: () -> Unit
) {
    private val TAG = "VAD"

    private val SPEECH_THRESHOLD = 0.015f   // RMS threshold to detect speech
    private val SILENCE_FRAMES = 25          // ~500ms silence to detect end of speech
    private val SPEECH_FRAMES = 3            // Consecutive frames to confirm speech start

    private var silenceCount = 0
    private var speechCount = 0
    private var isSpeechActive = false

    fun processChunk(pcm: ByteArray) {
        val rms = AudioUtils.calculateRms(pcm)
        VoiceStateManager.updateAmplitude(rms)

        if (rms > SPEECH_THRESHOLD) {
            silenceCount = 0
            speechCount++

            if (!isSpeechActive && speechCount >= SPEECH_FRAMES) {
                isSpeechActive = true
                Logger.d(TAG, "Speech detected (rms=$rms)")
                onSpeechStart()
            }
        } else {
            speechCount = 0
            if (isSpeechActive) {
                silenceCount++
                if (silenceCount >= SILENCE_FRAMES) {
                    isSpeechActive = false
                    silenceCount = 0
                    Logger.d(TAG, "Silence detected")
                    onSpeechEnd()
                }
            }
        }
    }

    fun reset() {
        silenceCount = 0
        speechCount = 0
        isSpeechActive = false
    }
}

package com.myra.assistant.utils

import android.media.AudioFormat
import android.media.AudioRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtils {

    fun getMinBufferSize(sampleRate: Int = Constants.SAMPLE_RATE_IN): Int =
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).let { if (it == AudioRecord.ERROR || it == AudioRecord.ERROR_BAD_VALUE) 4096 else it * 2 }

    fun pcm16ToFloat(pcm: ByteArray): FloatArray {
        val shorts = ShortArray(pcm.size / 2)
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return FloatArray(shorts.size) { shorts[it] / 32768f }
    }

    fun calculateRms(pcm: ByteArray): Float {
        if (pcm.isEmpty()) return 0f
        val floats = pcm16ToFloat(pcm)
        val sum = floats.sumOf { (it * it).toDouble() }
        return Math.sqrt(sum / floats.size).toFloat()
    }

    /**
     * Convert PCM bytes to base64 for WebSocket transmission
     */
    fun pcmToBase64(pcm: ByteArray): String =
        android.util.Base64.encodeToString(pcm, android.util.Base64.NO_WRAP)
}

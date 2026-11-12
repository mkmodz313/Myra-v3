package com.myra.assistant.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

class LiveAudioManager(private val context: Context) {
    private val TAG = "MYRA_AUDIO"

    // Gemini Live API sends 24kHz, 16-bit PCM mono audio
    private val SAMPLE_RATE = 24000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isPlaying = false
    private var playbackThread: Thread? = null

    // Buffer for accumulating audio chunks
    private val audioBuffer = mutableListOf<ByteArray>()

    init {
        initAudioTrack()
        startPlaybackThread()
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AUDIO_FORMAT)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2,
                AudioTrack.MODE_STREAM
            )
        }

        audioTrack?.play()
        Log.d(TAG, "AudioTrack initialized")
    }

    private fun startPlaybackThread() {
        playbackThread = Thread {
            while (!Thread.interrupted()) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    try {
                        audioTrack?.write(chunk, 0, chunk.size)
                    } catch (e: Exception) {
                        Log.e(TAG, "Audio write error: ${e.message}")
                    }
                } else {
                    Thread.sleep(10)
                }
            }
        }.apply {
            name = "AudioPlaybackThread"
            isDaemon = true
            start()
        }
    }

    /**
     * Play audio chunk - for PCM data from Gemini Live API
     */
    fun playChunk(data: ByteArray) {
        if (data.isEmpty()) return

        // If data is base64 encoded (which shouldn't be but just in case)
        val pcmData = try {
            if (isBase64(data)) {
                android.util.Base64.decode(data, android.util.Base64.DEFAULT)
            } else {
                data
            }
        } catch (e: Exception) {
            data
        }

        audioQueue.offer(pcmData)
        Log.d(TAG, "Queued audio chunk: ${pcmData.size} bytes")
    }

    /**
     * Play MP3 audio from URL or file path
     */
    fun playAudioFromPath(path: String) {
        try {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { release() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer error: ${e.message}")
        }
    }

    /**
     * Play MP3 from byte array
     */
    fun playMp3Data(mp3Data: ByteArray) {
        try {
            // Write to temp file and play
            val tempFile = File.createTempFile("myra_audio", ".mp3", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(mp3Data) }

            MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    release()
                    tempFile.delete()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MP3 play error: ${e.message}")
        }
    }

    fun stop() {
        try {
            playbackThread?.interrupt()
            playbackThread = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            audioQueue.clear()
            audioBuffer.clear()

            Log.d(TAG, "Audio stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        }
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioTrack?.pause()
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioTrack?.play()
        }
    }

    private fun isBase64(data: ByteArray): Boolean {
        if (data.size < 100) return false // PCM audio will be at least a few hundred bytes
        // Check if all bytes are valid base64 characters
        val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".toSet()
        return data.all { base64Chars.contains(it.toInt().toChar()) }
    }

    fun clearQueue() {
        audioQueue.clear()
        audioBuffer.clear()
    }
}

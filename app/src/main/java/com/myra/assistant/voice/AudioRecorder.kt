package com.myra.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.myra.assistant.utils.AudioUtils
import com.myra.assistant.utils.Logger

class AudioRecorder(
    private val context: Context,
    private val onChunk: (ByteArray) -> Unit
) {
    private val TAG = "RECORDER"
    private val SAMPLE_RATE = 16000
    private val CHUNK_SIZE = 640   // 20 ms at 16 kHz mono 16-bit: smaller chunks reach the server sooner

    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    @Volatile private var isRecording = false

    fun start() {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Logger.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        val bufferSize = AudioUtils.getMinBufferSize(SAMPLE_RATE)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) {
                Logger.e(TAG, "AudioRecord not initialized (mic busy?)")
                try { audioRecord?.release() } catch (_: Exception) {}
                audioRecord = null
                return
            }
            EchoCancellationManager.attach(audioRecord!!.audioSessionId)
            audioRecord!!.startRecording()
            isRecording = true

            recordThread = Thread {
                val buffer = ByteArray(CHUNK_SIZE)
                var errors = 0
                while (isRecording && !Thread.interrupted()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        errors = 0
                        if (!VoiceStateManager.isAiSpeaking()) onChunk(buffer.copyOf(read))
                    } else if (++errors > 25) {
                        // mic was taken away (call / other app): reopen it instead of spinning silently
                        Logger.e(TAG, "mic read failing ($read) - restarting")
                        Thread { stop(); try { Thread.sleep(300) } catch (_: Exception) {}; start() }.start()
                        break
                    } else {
                        try { Thread.sleep(20) } catch (_: InterruptedException) { break }
                    }
                }
            }.also {
                it.name = "MYRA_RecordThread"
                it.isDaemon = true
                it.start()
            }

            Logger.d(TAG, "Recording started")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start recording: ${e.message}")
        }
    }

    fun stop() {
        isRecording = false
        recordThread?.interrupt()
        recordThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            EchoCancellationManager.release()
        } catch (e: Exception) {
            Logger.e(TAG, "Stop error: ${e.message}")
        }
        Logger.d(TAG, "Recording stopped")
    }

    fun isActive() = isRecording
}

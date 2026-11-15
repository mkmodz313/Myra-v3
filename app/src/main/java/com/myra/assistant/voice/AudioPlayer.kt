package com.myra.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import com.myra.assistant.utils.Constants
import com.myra.assistant.utils.Logger
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AudioPlayer {
    private val TAG = "PLAYER"
    private val SAMPLE_RATE = Constants.SAMPLE_RATE_OUT
    private val PRIME_MS = 70            // audio collected before a reply starts playing
    private val PRIME_MAX_WAIT_MS = 120L // never wait longer than this for it
    private val FINISH_GAP_MS = 300L     // silence from the server that really means "reply finished"

    private var audioTrack: AudioTrack? = null
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var isPlaying = false
    private var playThread: Thread? = null

    /** Total PCM frames handed to the AudioTrack so far (used to time-align lip-sync with the speaker). */
    private var framesWritten = 0L

    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackFinished: (() -> Unit)? = null

    init {
        initTrack()
        startPlayThread()
    }

    private fun initTrack() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

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
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4,
                AudioTrack.MODE_STREAM
            )
        }
        audioTrack?.play()
        attachLipSync()
    }

    /**
     * Lets [LipSyncBus] ask "which frame is the speaker playing right now?" so the avatar's mouth is
     * aligned with the audible voice rather than with the (earlier) moment the chunk was queued.
     */
    private fun attachLipSync() {
        LipSyncBus.reset()
        LipSyncBus.playedFramesProvider = {
            // playbackHeadPosition is an unsigned 32-bit frame counter
            (audioTrack?.playbackHeadPosition?.toLong() ?: 0L) and 0xFFFFFFFFL
        }
    }

    private fun writeChunk(chunk: ByteArray) {
        try {
            val start = framesWritten
            framesWritten += chunk.size / 2
            // Speaker first (never let analysis delay the audio), then the lip-sync analyser with the same PCM.
            audioTrack?.write(chunk, 0, chunk.size)
            VoiceStateManager.noteAudio()
            LipSyncBus.onPcm(chunk, start)
        } catch (e: Exception) {
            Logger.e(TAG, "Write error: ${e.message}")
        }
    }

    private fun startPlayThread() {
        playThread = Thread {
            // Real-time audio priority: the busy UI (character animation) must never starve the speaker.
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) } catch (_: Throwable) {}
            val primeBytes = SAMPLE_RATE * 2 * PRIME_MS / 1000      // 16-bit mono
            var wasPlaying = false
            var lastChunkAt = 0L
            try {
                while (!Thread.interrupted()) {
                  try {
                    val chunk = queue.poll(15, TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        if (!wasPlaying) {
                            // Start of a reply: collect a few tens of ms first, so network jitter in the first
                            // chunks cannot leave gaps (crackle) in the voice.
                            val buf = java.io.ByteArrayOutputStream()
                            buf.write(chunk)
                            val t0 = System.currentTimeMillis()
                            while (buf.size() < primeBytes && System.currentTimeMillis() - t0 < PRIME_MAX_WAIT_MS) {
                                val more = queue.poll(8, TimeUnit.MILLISECONDS) ?: continue
                                buf.write(more)
                            }
                            wasPlaying = true
                            VoiceStateManager.setSpeaking()
                            onPlaybackStarted?.invoke()
                            writeChunk(buf.toByteArray())
                        } else {
                            writeChunk(chunk)
                        }
                        lastChunkAt = System.currentTimeMillis()
                    } else if (wasPlaying && System.currentTimeMillis() - lastChunkAt > FINISH_GAP_MS) {
                        // Only a real end of the reply counts. A short network hiccup must NOT re-open the
                        // microphone (it would hear MYRA's own voice, trigger an interruption and cut the speech).
                        wasPlaying = false
                        onPlaybackFinished?.invoke()
                    }
                  } catch (e: InterruptedException) {
                      throw e
                  } catch (e: Throwable) {
                      Logger.e(TAG, "play loop: ${e.message}")
                      wasPlaying = false
                      VoiceStateManager.setListening()
                  }
                }
            } catch (_: InterruptedException) {
            }
        }.also {
            it.name = "MYRA_PlayThread"
            it.isDaemon = true
            it.start()
        }
    }

    fun playChunk(data: ByteArray) {
        if (data.isNotEmpty()) queue.offer(data)
    }

    /** Drops audio that has not been written yet (stale socket / interrupted turn). Lip-sync counters untouched. */
    fun dropPending() {
        queue.clear()
    }

    fun clearAndStop() {
        queue.clear()
        VoiceStateManager.setListening()
    }

    fun release() {
        LipSyncBus.playedFramesProvider = null
        LipSyncBus.reset()
        playThread?.interrupt()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        queue.clear()
    }
}

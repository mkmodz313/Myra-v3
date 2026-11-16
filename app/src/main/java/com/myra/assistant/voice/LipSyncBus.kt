package com.myra.assistant.voice

import com.myra.assistant.utils.Constants
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Audio-driven lip-sync data for the MYRA avatar.
 *
 * Fed by [AudioPlayer] with the SAME PCM chunks that are written to the speaker, so the mouth
 * follows MYRA's actual generated voice (not a random animation).
 *
 * Gemini Live returns raw PCM only (no phoneme/viseme timing), so this implements the
 * amplitude-based approach: every 20 ms slice gets
 *   - open  (0..1)  from the speech-normalised RMS energy
 *   - shape (-1..1) from the zero-crossing rate: -1 = rounded vowel (oo/oh), +1 = spread/fricative (ee/s/f)
 *
 * Timing: slices are stamped with their frame index in the AudioTrack stream. The avatar reads the
 * slice for the frame the speaker is *currently playing* (AudioTrack.playbackHeadPosition), so the lips
 * are aligned to what is audible even though audio is buffered ahead of the speaker.
 *
 * Threading: onPcm() runs on the player thread, sample() on the UI thread. Guarded by a lock.
 */
object LipSyncBus {

    private const val SLICE_MS = 20
    private const val CAP = 600                    // ~12 s of history, plenty for buffered playback
    private val sliceFrames = Constants.SAMPLE_RATE_OUT * SLICE_MS / 1000

    /** Frames of speaker latency compensation (positive = mouth leads the sound). */
    @Volatile var latencyCompensationFrames: Long = 0L

    /** Set by AudioPlayer: frames the speaker has played so far (unsigned playbackHeadPosition). */
    @Volatile var playedFramesProvider: (() -> Long)? = null

    private val lock = Any()
    private val starts = LongArray(CAP)
    private val opens = FloatArray(CAP)
    private val shapes = FloatArray(CAP)
    private var count = 0

    // running accumulator for the slice currently being built
    private var accN = 0
    private var accSq = 0.0
    private var accZc = 0
    private var prevSample = 0
    private var accStart = 0L
    private var peak = 0.08f

    /** @param startFrame index (in PCM frames, mono 16-bit) of the first sample of [chunk] in the AudioTrack stream. */
    fun onPcm(chunk: ByteArray, startFrame: Long) {
        try {
            synchronized(lock) {
                val n = chunk.size / 2
                var i = 0
                while (i < n) {
                    val lo = chunk[2 * i].toInt() and 0xFF
                    val hi = chunk[2 * i + 1].toInt()
                    val s = (hi shl 8) or lo            // sign-extended 16-bit sample
                    if (accN == 0) {
                        accStart = startFrame + i
                        accSq = 0.0
                        accZc = 0
                    }
                    accSq += s.toDouble() * s
                    if ((s >= 0) != (prevSample >= 0)) accZc++
                    prevSample = s
                    accN++
                    if (accN >= sliceFrames) emitSlice()
                    i++
                }
            }
        } catch (_: Throwable) {
            // Never let lip-sync analysis interfere with audio playback.
        }
    }

    private fun emitSlice() {
        val rms = (sqrt(accSq / accN) / 32768.0).toFloat()
        val zcr = accZc.toFloat() / accN
        peak = max(rms, peak * 0.9985f).coerceAtLeast(0.05f)   // slow-decay speech peak = automatic gain
        val level = rms / peak
        var open = if (rms < 0.006f) 0f else ((level - 0.10f) / 0.75f).coerceIn(0f, 1f).pow(0.85f)
        val shape = ((zcr - 0.10f) / 0.15f).coerceIn(-1f, 1f)
        open *= 1f - 0.40f * max(0f, shape)          // fricatives/“ee” open the mouth less
        val idx = count % CAP
        starts[idx] = accStart
        opens[idx] = open
        shapes[idx] = shape
        count++
        accN = 0
    }

    /**
     * Writes the mouth targets for the audio that is audible RIGHT NOW into out[0]=open, out[1]=shape.
     * @return true while speech audio is actually playing, false when silent / nothing queued.
     */
    fun sample(out: FloatArray): Boolean {
        val provider = playedFramesProvider
        synchronized(lock) {
            if (count == 0) {
                out[0] = 0f; out[1] = 0f
                return false
            }
            val latest = count - 1
            val oldest = max(0, count - CAP)
            val pos: Long = if (provider != null) {
                try { provider() + latencyCompensationFrames } catch (_: Throwable) { starts[latest % CAP] }
            } else {
                starts[latest % CAP]
            }
            var i = latest
            while (i >= oldest && starts[i % CAP] > pos) i--
            if (i < oldest) {
                out[0] = 0f; out[1] = 0f
                return false
            }
            val end = starts[i % CAP] + sliceFrames
            if (i == latest && pos >= end) {          // played past the last analysed slice -> silence
                out[0] = 0f; out[1] = 0f
                return false
            }
            out[0] = opens[i % CAP]
            out[1] = shapes[i % CAP]
            return true
        }
    }

    fun reset() {
        synchronized(lock) {
            count = 0
            accN = 0
            accSq = 0.0
            accZc = 0
            prevSample = 0
            peak = 0.08f
        }
    }
}

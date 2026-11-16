package com.myra.assistant.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.myra.assistant.R

/** Key click sounds: 14 different sounds, played with SoundPool (works even when the phone's own touch sounds are off). */
object KbSound {
    val names = listOf(
        "Soft Click", "Tick", "Pop", "Bubble", "Typewriter", "Mechanical", "Water Drop",
        "Bell", "Wood Tap", "Laser", "Piano", "Bamboo", "Marimba", "Glass Ping"
    )
    private val res = intArrayOf(
        R.raw.ksnd_01, R.raw.ksnd_02, R.raw.ksnd_03, R.raw.ksnd_04, R.raw.ksnd_05, R.raw.ksnd_06, R.raw.ksnd_07,
        R.raw.ksnd_08, R.raw.ksnd_09, R.raw.ksnd_10, R.raw.ksnd_11, R.raw.ksnd_12, R.raw.ksnd_13, R.raw.ksnd_14
    )
    private var pool: SoundPool? = null
    private val ids = IntArray(14)
    private val ready = BooleanArray(14)

    @Synchronized
    fun ensure(c: Context) {
        if (pool != null) return
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attr).build()
        p.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                for (i in ids.indices) if (ids[i] == sampleId) ready[i] = true
            }
        }
        val app = c.applicationContext
        for (i in res.indices) {
            try {
                ids[i] = p.load(app, res[i], 1)
            } catch (e: Throwable) {
            }
        }
        pool = p
    }

    /** idx = 0..13, volume = 0..100 */
    fun play(c: Context, idx: Int, volume: Int) {
        try {
            ensure(c)
            val i = idx.coerceIn(0, 13)
            val v = (volume.coerceIn(0, 100) / 100f)
            if (ready[i]) pool?.play(ids[i], v, v, 1, 0, 1f)
        } catch (e: Throwable) {
        }
    }
}

package com.myra.assistant.ui.avatar

import android.os.Handler
import android.os.Looper
import java.util.Locale

/** Things the 3D character can be told to do (by voice, via Gemini function call or keyword fallback). */
enum class CharacterAction(val key: String, val defaultMs: Long) {
    DANCE("dance", 12000L),
    SPIN("spin", 2400L),
    JUMP("jump", 2600L),
    WAVE("wave", 4000L),
    BOW("bow", 2200L),
    SWAY("sway", 9000L),
    STOP("stop", 0L);

    companion object {
        fun fromKey(raw: String?): CharacterAction? {
            val k = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
            return values().firstOrNull { it.key == k }
                ?: when {
                    k.contains("dance") || k.contains("nach") || k.contains("naach") -> DANCE
                    k.contains("spin") || k.contains("twirl") || k.contains("ghoom") || k.contains("chakkar") -> SPIN
                    k.contains("jump") || k.contains("kud") -> JUMP
                    k.contains("wave") || k.contains("hila") -> WAVE
                    k.contains("bow") || k.contains("jhuk") || k.contains("salam") || k.contains("namaste") -> BOW
                    k.contains("sway") || k.contains("jhoom") -> SWAY
                    k.contains("stop") || k.contains("ruk") || k.contains("bas") -> STOP
                    else -> null
                }
        }
    }
}

/**
 * Tiny main-thread bus between whoever understands a command (voice service, Gemini tool call) and the
 * [CharacterView]. Safe to call from any thread.
 */
object CharacterActionBus {

    interface Listener {
        fun onCharacterAction(action: CharacterAction, durationMs: Long)
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<Listener>()
    @Volatile private var current: CharacterAction? = null
    @Volatile private var currentUntil = 0L

    fun register(l: Listener) { main.post { if (!listeners.contains(l)) listeners.add(l) } }
    fun unregister(l: Listener) { main.post { listeners.remove(l) } }

    /** Perform an action. Returns a short human readable result for the model. */
    fun perform(action: CharacterAction, durationMs: Long = action.defaultMs): String {
        val now = System.currentTimeMillis()
        if (action != CharacterAction.STOP) {
            // Same action asked twice in a row (partial transcripts) -> ignore the duplicate.
            if (current == action && now < currentUntil) return "already ${action.key}ing"
            current = action
            currentUntil = now + durationMs
        } else {
            current = null
            currentUntil = 0L
        }
        main.post { for (l in ArrayList(listeners)) l.onCharacterAction(action, durationMs) }
        return if (action == CharacterAction.STOP) "stopped" else "started ${action.key}"
    }

    /** Keyword fallback used on the live transcript of what the user said (Hindi / Urdu / English). */
    fun detect(spoken: String) {
        val t = spoken.lowercase(Locale.ROOT)
        if (t.isBlank()) return
        val stopWords = listOf("stop dancing", "dance band", "naach band", "nach band", "ruk jao", "ruk ja", "bas karo", "stop it")
        if (stopWords.any { t.contains(it) }) { if (current != null) perform(CharacterAction.STOP); return }
        val a = when {
            listOf("dance", "naach", "nacho", "nachna", "nach ").any { t.contains(it) } -> CharacterAction.DANCE
            listOf("spin", "twirl", "ghoom", "chakkar").any { t.contains(it) } -> CharacterAction.SPIN
            listOf("jump", "kudo", "kud ").any { t.contains(it) } -> CharacterAction.JUMP
            listOf("wave", "hath hila", "haath hila").any { t.contains(it) } -> CharacterAction.WAVE
            listOf("bow", "jhuko", "salaam", "salam kar", "namaste kar").any { t.contains(it) } -> CharacterAction.BOW
            else -> null
        }
        if (a != null) perform(a)
    }
}

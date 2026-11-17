package com.myra.assistant.utils

import android.content.Context

/**
 * API keys live in assets/keys.txt as NAME=VALUE lines. In the source / GitHub every value is a ❤️ placeholder;
 * the real key is typed into the built APK (MT Manager). A value that is empty or still has the ❤️ means "no key".
 */
object Keys {
    private var map: Map<String, String> = emptyMap()
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val m = HashMap<String, String>()
        try {
            val txt = context.applicationContext.assets.open("keys.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            for (raw in txt.lines()) {
                val line = raw.trim().trimStart('\uFEFF')
                if (line.isEmpty() || line.startsWith("#")) continue
                val i = line.indexOf('=')
                if (i <= 0) continue
                val name = line.substring(0, i).trim()
                val v = line.substring(i + 1).trim()
                if (v.isNotEmpty() && !v.contains('❤') && !v.contains('\u2764')) m[name] = v
            }
        } catch (e: Throwable) {
        }
        map = m
        loaded = true
    }

    /**
     * Second place for a key (for MT Manager "DEX editor"): each key has its OWN placeholder string in the app code.
     * Replace the whole string (❤️1 = Gemini, ❤️2 = Web Builder, ❤️3 = Supabase) with the real key.
     * Either this OR assets/keys.txt is enough.
     */
    private val inCode: Map<String, String> = mapOf(
        "GEMINI_KEY" to "❤️1",
        "WEB_BUILDER_KEY" to "❤️2",
        "SUPABASE_KEY" to "❤️3"
    )

    private fun real(v: String?): String {
        val t = v?.trim() ?: return ""
        return if (t.isEmpty() || t.contains('❤') || t.contains('\u2764')) "" else t
    }

    fun get(context: Context, name: String): String {
        if (!loaded) init(context)
        return get(name)
    }

    fun get(name: String): String {
        val a = real(map[name])
        if (a.isNotEmpty()) return a
        return real(inCode[name])
    }
}

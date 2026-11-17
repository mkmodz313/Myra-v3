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

    fun get(context: Context, name: String): String {
        if (!loaded) init(context)
        return map[name] ?: ""
    }

    fun get(name: String): String = map[name] ?: ""
}

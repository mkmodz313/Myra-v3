package com.myra.assistant.keyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** All MYRA Keyboard settings. Every setter bumps a "stamp" so the running keyboard re-applies them. */
object KbPrefs {

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("myra_kb", Context.MODE_PRIVATE)

    private fun putS(c: Context, k: String, v: String) {
        sp(c).edit().putString(k, v).putLong("stamp", System.nanoTime()).apply()
    }

    private fun putI(c: Context, k: String, v: Int) {
        sp(c).edit().putInt(k, v).putLong("stamp", System.nanoTime()).apply()
    }

    private fun putB(c: Context, k: String, v: Boolean) {
        sp(c).edit().putBoolean(k, v).putLong("stamp", System.nanoTime()).apply()
    }

    fun touch(c: Context) {
        sp(c).edit().putLong("stamp", System.nanoTime()).apply()
    }

    fun stamp(c: Context): Long = sp(c).getLong("stamp", 0L)

    fun clearAll(c: Context) {
        sp(c).edit().clear().putLong("stamp", System.nanoTime()).apply()
    }

    // ---- look ----
    fun themeId(c: Context): String = sp(c).getString("theme", "pinkheart") ?: "pinkheart"
    fun setThemeId(c: Context, v: String) = putS(c, "theme", v)

    /** 3d | glass | flat | bubble | png */
    /** Old plain styles (3D / glass / flat / bubble) are retired: they fall back to the shiny design of the theme. */
    fun style(c: Context): String {
        val v = sp(c).getString("style", "") ?: ""
        return if (v == "3d" || v == "glass" || v == "flat" || v == "bubble") "" else v
    }
    fun setStyle(c: Context, v: String) = putS(c, "style", v)

    /** Key opacity 20..100 */
    fun opacity(c: Context): Int = sp(c).getInt("opacity", 80)
    fun setOpacity(c: Context, v: Int) = putI(c, "opacity", v)

    /** Keyboard height percent 70..140 */
    fun heightPct(c: Context): Int = sp(c).getInt("height", 100)
    fun setHeightPct(c: Context, v: Int) = putI(c, "height", v)

    // ---- behaviour ----
    fun sound(c: Context): Boolean = sp(c).getBoolean("sound", false)
    fun setSound(c: Context, v: Boolean) = putB(c, "sound", v)

    fun vibrate(c: Context): Boolean = sp(c).getBoolean("vibrate", true)
    fun setVibrate(c: Context, v: Boolean) = putB(c, "vibrate", v)

    fun autoCaps(c: Context): Boolean = sp(c).getBoolean("autocaps", true)
    fun setAutoCaps(c: Context, v: Boolean) = putB(c, "autocaps", v)

    fun hints(c: Context): Boolean = sp(c).getBoolean("hints", true)
    fun setHints(c: Context, v: Boolean) = putB(c, "hints", v)

    fun preview(c: Context): Boolean = sp(c).getBoolean("preview", true)
    fun setPreview(c: Context, v: Boolean) = putB(c, "preview", v)

    // ---- font style (typing in stylish fonts) ----
    fun fontStyle(c: Context): String = sp(c).getString("font_style", "normal") ?: "normal"
    fun setFontStyle(c: Context, v: String) {
        sp(c).edit().putString("font_style", v).apply()
    }

    // ---- languages ----
    fun langs(c: Context): List<String> =
        (sp(c).getString("langs", "en,hi") ?: "en,hi").split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun setLangs(c: Context, v: List<String>) = putS(c, "langs", v.joinToString(","))

    fun curLang(c: Context): String = sp(c).getString("cur_lang", "en") ?: "en"

    /** Current language is remembered without bumping the stamp (it changes on every globe tap). */
    fun setCurLang(c: Context, v: String) {
        sp(c).edit().putString("cur_lang", v).apply()
    }

    // ---- clipboard history ----
    private fun readList(c: Context, key: String): MutableList<String> {
        val out = ArrayList<String>()
        try {
            val arr = JSONArray(sp(c).getString(key, "[]") ?: "[]")
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (e: Throwable) {
        }
        return out
    }

    private fun writeList(c: Context, key: String, list: List<String>) {
        val arr = JSONArray()
        for (s in list) arr.put(s)
        sp(c).edit().putString(key, arr.toString()).apply()
    }

    fun clips(c: Context): List<String> = readList(c, "clips")

    fun addClip(c: Context, text: String) {
        val l = readList(c, "clips")
        l.remove(text)
        l.add(0, text)
        while (l.size > 25) l.removeAt(l.size - 1)
        writeList(c, "clips", l)
    }

    fun clearClips(c: Context) = writeList(c, "clips", emptyList())

    // ---- recent emoji ----
    fun recents(c: Context): List<String> = readList(c, "recent_emoji")

    fun addRecent(c: Context, e: String) {
        val l = readList(c, "recent_emoji")
        l.remove(e)
        l.add(0, e)
        while (l.size > 32) l.removeAt(l.size - 1)
        writeList(c, "recent_emoji", l)
    }
}

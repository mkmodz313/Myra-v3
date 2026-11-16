package com.myra.assistant.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.File

class KbTheme(
    val id: String,
    val name: String,
    val bgAsset: String?,
    val pvAsset: String?,
    val gradTop: Int,
    val gradBottom: Int,
    val keyFill: Int,
    val specialFill: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val dark: Boolean,
    val defStyle: String
)

object KbThemes {

    const val CUSTOM_BG = "kb_custom_bg.png"
    const val CUSTOM_KEY = "kb_custom_key.png"

    private fun c(v: Long): Int = v.toInt()
    private fun hintOf(t: Int): Int = (t and 0x00FFFFFF) or (0xAA shl 24)

    private fun img(
        id: String, name: String, key: Long, special: Long, text: Long, accent: Long,
        dark: Boolean, style: String
    ): KbTheme = KbTheme(
        id, name, "keyboard/bg_$id.jpg", "keyboard/pv_$id.jpg",
        c(special), c(key), c(key), c(special), c(text), hintOf(c(text)), c(accent), dark, style
    )

    private fun gen(
        id: String, name: String, top: Long, bottom: Long, key: Long, special: Long,
        text: Long, accent: Long, dark: Boolean, style: String
    ): KbTheme = KbTheme(
        id, name, null, null, c(top), c(bottom), c(key), c(special),
        c(text), hintOf(c(text)), c(accent), dark, style
    )

    val all: List<KbTheme> = listOf(
        // ---- themes made from your 12 designs ----
        img("pinkheart", "Pink Diamond Heart", 0xFFFFE3F4, 0xFFF7B6DE, 0xFF9A0F6E, 0xFFE91E8C, false, "3d"),
        img("teal", "Teal Diamond Love", 0xFFE6FFFB, 0xFFA5E8E0, 0xFF0B5F59, 0xFF14B8A6, false, "3d"),
        img("queen", "Queen Pink Glitter", 0xFFFFE0EE, 0xFFF2A6CB, 0xFF8A1F55, 0xFFD81B7A, false, "3d"),
        img("leopard", "Pink Leopard Hearts", 0xFF2B0A1E, 0xFF4A1136, 0xFFFFC2E6, 0xFFFF2D9B, true, "3d"),
        img("zebra", "Pink Zebra Leopard", 0xFF3A0030, 0xFF5E0A4C, 0xFFFFD0F4, 0xFFFF1FA8, true, "3d"),
        img("blue", "Blue Butterfly Roses", 0xFFE8F4FF, 0xFFB5DBFF, 0xFF1B3FC9, 0xFF2F6BFF, false, "3d"),
        img("purple", "Purple Bow Love", 0xFF1B1024, 0xFF3A1B4D, 0xFFFFFFFF, 0xFF9C27B0, true, "3d"),
        img("pinkfly", "Pink Diamond Butterfly", 0xFFFFE9F8, 0xFFF6B8E4, 0xFF9B1D79, 0xFFE040A8, false, "3d"),
        img("pinkglass", "Pink Butterfly Glass", 0xFFFFEAF7, 0xFFF5C0E6, 0xFFB0309A, 0xFFE056C0, false, "3d"),
        img("swans", "Purple Diamond Swans", 0xFFFFF0FB, 0xFFE7B9F2, 0xFF8A2A9E, 0xFFB744D1, false, "3d"),
        img("hearts", "Neon Pink Hearts", 0xFF3D0A33, 0xFF661257, 0xFFFFC9F2, 0xFFFF3DBB, true, "3d"),
        img("bunny", "Kawaii Bunny", 0xFFFFF3F9, 0xFFFFC9E3, 0xFFD8467F, 0xFFFF6FA8, false, "3d"),
        // ---- 4K style designs: every picture has its own matching key colours ----
        img("galaxy", "Galaxy Nebula", 0xFF1B1440, 0xFF2E2266, 0xFFEDE7FF, 0xFF8B5CFF, true, "3d"),
        img("aurora", "Aurora Lights", 0xFF0B2B2E, 0xFF124A4A, 0xFFD9FFF4, 0xFF2DFFC0, true, "3d"),
        img("sunset", "Golden Sunset", 0xFFFFE9D6, 0xFFFFB98A, 0xFF7A2A12, 0xFFFF5E3A, false, "3d"),
        img("ocean", "Deep Ocean", 0xFFE3F6FF, 0xFF9ADBF5, 0xFF05405F, 0xFF0096D6, false, "3d"),
        img("gold", "Royal Gold", 0xFF1A1508, 0xFF2E2410, 0xFFFFE08A, 0xFFFFC21A, true, "3d"),
        img("emerald", "Emerald Luxe", 0xFF06281C, 0xFF0C4630, 0xFFD8FFE9, 0xFF1FD18B, true, "3d"),
        img("rosebokeh", "Rose Bokeh", 0xFFFFE6EF, 0xFFFFB4CE, 0xFF8E1745, 0xFFFF4D8D, false, "3d"),
        img("synthwave", "Neon Synthwave", 0xFF1A0B33, 0xFF2D1259, 0xFFFFD6FF, 0xFFFF2BD6, true, "3d"),
        img("marble", "White Marble Gold", 0xFFFFFFFF, 0xFFE6E0D6, 0xFF3A3226, 0xFFC9A24B, false, "3d"),
        img("carbon", "Carbon Red", 0xFF1C1C20, 0xFF2B1013, 0xFFFFFFFF, 0xFFFF1F3D, true, "3d"),
        img("ice", "Ice Crystal", 0xFFEFFFFF, 0xFFBDEFF0, 0xFF0B5560, 0xFF1CC6D0, false, "3d"),
        img("lavender", "Lavender Dream", 0xFFF3EAFF, 0xFFD7BFFF, 0xFF4A1E8C, 0xFF9B5CFF, false, "3d"),
        img("islamic", "Islamic Geometry", 0xFF0A2A1F, 0xFF12402F, 0xFFFFE9A8, 0xFFE2B84A, true, "3d"),
        img("crimson", "Crimson Night", 0xFF2A0509, 0xFF4A0A12, 0xFFFFD9DC, 0xFFFF3040, true, "3d"),
        // ---- generated 3D looks ----
        gen("neon", "MYRA Neon", 0xFF2A0038, 0xFF08000F, 0xFF241038, 0xFF3A1757, 0xFFFFE6FF, 0xFFFF2DAA, true, "3d"),
        gen("midnight", "Midnight Blue", 0xFF0D1B2A, 0xFF000814, 0xFF1B2A41, 0xFF263A5A, 0xFFFFFFFF, 0xFF00B8FF, true, "3d"),
        gen("rosegold", "Rose Gold", 0xFFF7D9D0, 0xFFE7A8A0, 0xFFFFF1EC, 0xFFF3C4BC, 0xFF7A3B3B, 0xFFD9776F, false, "3d"),
        gen("dark", "Classic Dark", 0xFF1C1C1E, 0xFF101012, 0xFF3A3A3C, 0xFF2A2A2C, 0xFFFFFFFF, 0xFF4C8DFF, true, "flat"),
        gen("light", "Classic Light", 0xFFE9ECF2, 0xFFD9DDE6, 0xFFFFFFFF, 0xFFC9CED9, 0xFF1B1B1F, 0xFF3D7BFF, false, "flat"),
        // ---- user's own photo / PNG ----
        KbTheme(
            "custom", "My Photo / PNG", null, null, c(0xFF222222), c(0xFF000000),
            c(0xFF1E1E26), c(0xFF30303C), c(0xFFFFFFFF), hintOf(c(0xFFFFFFFF)), c(0xFFFF4DA6), true, "glass"
        )
    )

    fun byId(id: String): KbTheme = all.firstOrNull { it.id == id } ?: all[0]

    fun bgVersion(ctx: Context, t: KbTheme): String {
        if (t.id != "custom") return ""
        val f = File(ctx.filesDir, CUSTOM_BG)
        return if (f.exists()) f.lastModified().toString() else "none"
    }

    fun hasCustomBg(ctx: Context): Boolean = File(ctx.filesDir, CUSTOM_BG).exists()
    fun hasCustomKey(ctx: Context): Boolean = File(ctx.filesDir, CUSTOM_KEY).exists()

    /** Background bitmap already cropped/scaled to w x h (null => use gradient). */
    fun loadBg(ctx: Context, t: KbTheme, w: Int, h: Int): Bitmap? {
        return try {
            val asset = t.bgAsset
            val src: Bitmap? = if (t.id == "custom") {
                decodeFile(File(ctx.filesDir, CUSTOM_BG), w, h)
            } else if (asset != null) {
                ctx.assets.open(asset).use { BitmapFactory.decodeStream(it) }
            } else null
            if (src == null) null else cover(src, w, h)
        } catch (e: Throwable) {
            null
        }
    }

    fun loadPreview(ctx: Context, t: KbTheme): Bitmap? {
        return try {
            val asset = t.pvAsset
            if (t.id == "custom") {
                decodeFile(File(ctx.filesDir, CUSTOM_BG), 300, 540)
            } else if (asset != null) {
                ctx.assets.open(asset).use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (e: Throwable) {
            null
        }
    }

    fun loadCustomKey(ctx: Context): Bitmap? =
        try {
            decodeFile(File(ctx.filesDir, CUSTOM_KEY), 320, 320)
        } catch (e: Throwable) {
            null
        }

    fun decodeFile(f: File, reqW: Int, reqH: Int): Bitmap? {
        if (!f.exists()) return null
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.absolutePath, o)
        var s = 1
        while (o.outWidth / (s * 2) >= reqW && o.outHeight / (s * 2) >= reqH) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        return BitmapFactory.decodeFile(f.absolutePath, o2)
    }

    private fun cover(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val sa = src.width.toFloat() / src.height.toFloat()
        val da = w.toFloat() / h.toFloat()
        val sr: Rect
        if (sa > da) {
            val nw = (src.height * da).toInt()
            val x = (src.width - nw) / 2
            sr = Rect(x, 0, x + nw, src.height)
        } else {
            val nh = (src.width / da).toInt()
            val y = (src.height - nh) / 2
            sr = Rect(0, y, src.width, y + nh)
        }
        Canvas(out).drawBitmap(src, sr, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        if (src !== out) src.recycle()
        return out
    }
}

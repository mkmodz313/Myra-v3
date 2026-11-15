package com.myra.assistant.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Thumbnail loader with a PERMANENT disk cache (app storage) + memory cache.
 * Once a thumbnail has been seen it is kept on the phone, so the gallery shows everything instantly on the next
 * launch - even offline. New thumbnails are downloaded in the background. Never loads videos.
 */
object ThumbLoader {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    private fun dir(ctx: Context) = File(ctx.filesDir, "thumbs").apply { mkdirs() }

    private fun name(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".img"

    fun load(context: Context, url: String, into: ImageView) {
        into.tag = url
        cache.get(url)?.let { into.setImageBitmap(it); return }
        into.setImageDrawable(null)
        val app = context.applicationContext
        pool.execute {
            val bmp = try { obtain(app, url) } catch (_: Throwable) { null }
            if (bmp != null) cache.put(url, bmp)
            main.post { if (into.tag == url && bmp != null) into.setImageBitmap(bmp) }
        }
    }

    /** Loads thumbnails (disk first, network only if missing) into memory in the background. */
    fun warm(context: Context, urls: Collection<String>) {
        val app = context.applicationContext
        urls.distinct().forEach { url ->
            if (url.isEmpty() || cache.get(url) != null) return@forEach
            pool.execute {
                try { obtain(app, url)?.let { cache.put(url, it) } } catch (_: Throwable) { }
            }
        }
    }

    /** Removes saved thumbnails that are no longer part of the catalog. */
    fun prune(context: Context, keep: Collection<String>) {
        try {
            val names = keep.map { name(it) }.toSet()
            dir(context).listFiles()?.filter { it.name !in names && !it.name.endsWith(".part") }?.forEach { it.delete() }
        } catch (_: Throwable) { }
    }

    private fun obtain(ctx: Context, url: String): Bitmap? {
        if (url.startsWith("asset:")) {
            return decode(ctx.assets.open(url.removePrefix("asset:")).use { it.readBytes() })
        }
        val f = File(dir(ctx), name(url))
        if (f.exists() && f.length() > 0) {
            decode(f.readBytes())?.let { return it }
            f.delete()
        }
        val bytes = WallpaperRepository.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.bytes() ?: return null
        }
        try {
            val tmp = File(f.parentFile, f.name + ".part")
            tmp.writeBytes(bytes)
            tmp.renameTo(f)
        } catch (_: Exception) { }
        return decode(bytes)
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / sample > 720) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

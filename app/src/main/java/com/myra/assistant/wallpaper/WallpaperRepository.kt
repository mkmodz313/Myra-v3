package com.myra.assistant.wallpaper

import android.content.Context
import com.myra.assistant.utils.prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Catalog + downloads. Talks to Supabase through its REST API (no extra SDK / plugin needed). Only published
 * wallpapers are readable by the app - enforced server-side by the Row Level Security policies.
 *
 * Only thumbnails are fetched for the gallery. A video is downloaded ONLY when the user taps SET WALLPAPER;
 * the detail preview streams it instead.
 */
object WallpaperRepository {

    private const val CACHE_KEY = "wp_catalog_cache"
    private val JSON = "application/json".toMediaType()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Last catalog stored on the phone (shown instantly / offline). */
    fun cached(context: Context): List<WallpaperItem> = try {
        val arr = JSONArray(context.prefs().getString(CACHE_KEY, "[]"))
        (0 until arr.length()).map { WallpaperItem.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    /** Fetches published wallpapers from Supabase. Throws on network / server errors. */
    fun fetchCloud(context: Context): List<WallpaperItem> {
        if (!CloudConfig.isConfigured) return emptyList()
        val req = Request.Builder()
            .url("${CloudConfig.base()}/rest/v1/wallpapers?select=*&published=eq.true&order=created_at.desc&limit=200")
            .header("apikey", CloudConfig.SUPABASE_KEY)
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException("Catalog error ${r.code}")
            val arr = JSONArray(text)
            val out = ArrayList<WallpaperItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val video = o.optString("video_url")
                val id = o.optString("id")
                if (id.isEmpty() || video.isEmpty()) continue
                out.add(WallpaperItem(
                    id, o.optString("title"), o.optString("description"), o.optString("category", "General").ifEmpty { "General" },
                    video, o.optString("thumb_url"), o.optLong("size_bytes"), o.optLong("created_at"),
                    o.optLong("downloads"), o.optLong("views")
                ))
            }
            context.prefs().edit().putString(CACHE_KEY, JSONArray(out.map { it.toJson() }).toString()).apply()
            // keep every thumbnail on the phone so the next launch shows the whole gallery instantly
            val thumbs = out.map { it.thumbUrl }
            ThumbLoader.prune(context, thumbs)
            ThumbLoader.warm(context, thumbs)
            return out
        }
    }

    /** Fire-and-forget statistics (+1) through a safe server function (only downloads/views of published items). */
    fun bump(item: WallpaperItem, field: String) {
        if (item.isBundled || !CloudConfig.isConfigured) return
        try {
            val body = JSONObject().put("wp_id", item.id).put("field", field)
            val req = Request.Builder()
                .url("${CloudConfig.base()}/rest/v1/rpc/bump_stat")
                .header("apikey", CloudConfig.SUPABASE_KEY)
                .post(body.toString().toRequestBody(JSON)).build()
            http.newCall(req).execute().close()
        } catch (_: Exception) { }
    }

    fun localFile(context: Context, item: WallpaperItem) = File(File(context.filesDir, "wallpapers").apply { mkdirs() }, "${item.id}.mp4")

    /**
     * Makes the video available as a local file (copies the bundled sample or downloads the cloud video).
     * [onProgress] gets 0..100. Returns the file or throws.
     */
    fun ensureVideo(context: Context, item: WallpaperItem, onProgress: (Int) -> Unit): File {
        val out = localFile(context, item)
        if (out.exists() && out.length() > 0) { onProgress(100); return out }
        val tmp = File(out.parentFile, "${item.id}.part")
        try {
            if (item.isBundled) {
                context.assets.open(item.videoUrl.removePrefix("asset:")).use { i ->
                    FileOutputStream(tmp).use { o -> i.copyTo(o) }
                }
                onProgress(100)
            } else {
                val req = Request.Builder().url(item.videoUrl).build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw RuntimeException("Download failed (${r.code})")
                    val body = r.body ?: throw RuntimeException("Empty response")
                    val total = body.contentLength()
                    var done = 0L
                    body.byteStream().use { i ->
                        FileOutputStream(tmp).use { o ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = i.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                done += n
                                if (total > 0) onProgress((done * 100 / total).toInt())
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(out)) throw RuntimeException("Could not save video")
            return out
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}

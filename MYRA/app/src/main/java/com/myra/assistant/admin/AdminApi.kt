package com.myra.assistant.admin

import com.myra.assistant.wallpaper.CloudConfig
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class AdminWallpaper(
    val id: String, val title: String, val description: String, val category: String,
    val videoUrl: String, val thumbUrl: String, val videoPath: String, val thumbPath: String,
    val sizeBytes: Long, val createdAt: Long, val downloads: Long, val views: Long, val published: Boolean
)

/**
 * Admin backend calls: Supabase Auth + Postgres (REST) + Storage.
 *
 * The admin password is typed on the login screen, sent over HTTPS to Supabase's sign-in endpoint and never
 * stored - not in the APK, not on the phone. Every write is checked on Supabase's servers by Row Level Security /
 * Storage policies (only CloudConfig.ADMIN_EMAIL), so modifying the APK cannot give anyone upload rights.
 */
object AdminApi {

    class ApiError(message: String) : Exception(message)

    private val JSON = "application/json".toMediaType()
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    @Volatile private var accessToken: String? = null
    @Volatile private var refreshToken: String? = null
    @Volatile private var expiresAt = 0L

    fun isSignedIn() = accessToken != null
    fun signOut() { accessToken = null; refreshToken = null; expiresAt = 0L }

    private fun req(path: String) = Request.Builder().url("${CloudConfig.base()}$path").header("apikey", CloudConfig.SUPABASE_KEY)
    private fun authed(path: String) = req(path).header("Authorization", "Bearer ${token()}")

    // ── Auth ─────────────────────────────────────────────────────────────────────────────────────
    fun signIn(email: String, password: String) {
        if (!CloudConfig.isConfigured) throw ApiError("CloudConfig.kt mein Supabase URL aur key abhi nahi daali gayi.")
        if (!email.trim().equals(CloudConfig.ADMIN_EMAIL, ignoreCase = true)) throw ApiError("Ye account admin nahi hai.")
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val r = req("/auth/v1/token?grant_type=password").post(body.toString().toRequestBody(JSON)).build()
        http.newCall(r).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiError(authMessage(text))
            val o = JSONObject(text)
            val mail = o.optJSONObject("user")?.optString("email").orEmpty()
            if (!mail.equals(CloudConfig.ADMIN_EMAIL, true)) throw ApiError("Ye account admin nahi hai.")
            store(o)
        }
    }

    private fun store(o: JSONObject) {
        accessToken = o.getString("access_token")
        refreshToken = o.optString("refresh_token", refreshToken ?: "")
        expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000 - 90_000
    }

    private fun authMessage(body: String): String {
        val j = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val m = listOf(j.optString("error_description"), j.optString("msg"), j.optString("message")).firstOrNull { it.isNotEmpty() } ?: ""
        return when {
            m.contains("Invalid login", true) || m.contains("invalid_credentials", true) -> "Email ya password galat hai."
            m.contains("not confirmed", true) -> "Supabase mein is user ko 'Auto Confirm' karein (Authentication → Users)."
            m.contains("Invalid API key", true) || m.contains("apikey", true) -> "Supabase key sahi nahi hai (CloudConfig.kt)."
            m.contains("rate limit", true) || m.contains("too many", true) -> "Bohot zyada koshishen. Thodi der baad try karein."
            m.isNotEmpty() -> "Login failed: $m"
            else -> "Login failed. Internet / Supabase URL check karein."
        }
    }

    private fun refresh() {
        val rt = refreshToken?.takeIf { it.isNotEmpty() } ?: throw ApiError("Session khatam. Dobara login karein.")
        val r = req("/auth/v1/token?grant_type=refresh_token")
            .post(JSONObject().put("refresh_token", rt).toString().toRequestBody(JSON)).build()
        http.newCall(r).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) { signOut(); throw ApiError("Session khatam. Dobara login karein.") }
            store(JSONObject(text))
        }
    }

    @Synchronized
    private fun token(): String {
        if (accessToken == null) throw ApiError("Login zaroori hai.")
        if (System.currentTimeMillis() > expiresAt) refresh()
        return accessToken ?: throw ApiError("Login zaroori hai.")
    }

    private fun httpError(code: Int, body: String, what: String): ApiError {
        val j = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val msg = listOf(j.optString("message"), j.optString("error"), j.optString("msg")).firstOrNull { it.isNotEmpty() } ?: ""
        val c = j.optString("code")
        return ApiError(when {
            code == 401 -> "Session khatam ya key galat. Dobara login karein."
            msg.contains("Bucket not found", true) -> "$what: Storage bucket 'wallpapers' nahi bana. backend/supabase_setup.sql run karein."
            c == "PGRST205" || c == "42P01" || msg.contains("does not exist", true) || msg.contains("schema cache", true) ->
                "$what: table nahi mili. backend/supabase_setup.sql Supabase SQL Editor mein run karein."
            code == 403 || c == "42501" || msg.contains("row-level security", true) || msg.contains("violates", true) ->
                "$what: permission nahi mili. supabase_setup.sql (policies) run karein aur admin email se login karein."
            code == 413 || msg.contains("exceeded the maximum", true) || msg.contains("too large", true) ->
                "$what: file bohot badi hai. Supabase free plan mein max 50 MB. Video compress karein."
            else -> "$what failed ($code) $msg"
        })
    }

    // ── Database ─────────────────────────────────────────────────────────────────────────────────
    fun list(): List<AdminWallpaper> {
        http.newCall(authed("/rest/v1/wallpapers?select=*&order=created_at.desc&limit=300").build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw httpError(r.code, text, "Wallpapers list")
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AdminWallpaper(
                    o.optString("id"), o.optString("title"), o.optString("description"), o.optString("category", "General").ifEmpty { "General" },
                    o.optString("video_url"), o.optString("thumb_url"), o.optString("video_path"), o.optString("thumb_path"),
                    o.optLong("size_bytes"), o.optLong("created_at"), o.optLong("downloads"), o.optLong("views"), o.optBoolean("published")
                )
            }
        }
    }

    fun create(w: AdminWallpaper) {
        val body = JSONObject()
            .put("id", w.id).put("title", w.title).put("description", w.description).put("category", w.category)
            .put("video_url", w.videoUrl).put("thumb_url", w.thumbUrl).put("video_path", w.videoPath).put("thumb_path", w.thumbPath)
            .put("size_bytes", w.sizeBytes).put("created_at", w.createdAt).put("published", w.published)
        http.newCall(authed("/rest/v1/wallpapers").header("Prefer", "return=minimal").post(body.toString().toRequestBody(JSON)).build())
            .execute().use { r ->
                if (!r.isSuccessful) throw httpError(r.code, r.body?.string().orEmpty(), "Save")
            }
    }

    fun update(id: String, title: String? = null, description: String? = null, category: String? = null, published: Boolean? = null) {
        val body = JSONObject()
        title?.let { body.put("title", it) }
        description?.let { body.put("description", it) }
        category?.let { body.put("category", it) }
        published?.let { body.put("published", it) }
        if (body.length() == 0) return
        http.newCall(authed("/rest/v1/wallpapers?id=eq.$id").header("Prefer", "return=representation").patch(body.toString().toRequestBody(JSON)).build())
            .execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw httpError(r.code, text, "Update")
                if (JSONArray(text).length() == 0) throw ApiError("Update: permission nahi mili (admin email se login karein / policies run karein).")
            }
    }

    fun deleteDoc(id: String) {
        http.newCall(authed("/rest/v1/wallpapers?id=eq.$id").header("Prefer", "return=representation").delete().build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw httpError(r.code, text, "Delete")
            if (JSONArray(text).length() == 0) throw ApiError("Delete: permission nahi mili (admin email se login karein / policies run karein).")
        }
    }

    // ── Storage ──────────────────────────────────────────────────────────────────────────────────
    private class StreamBody(
        val type: MediaType?, val size: Long, val open: () -> InputStream, val onProgress: (Int) -> Unit
    ) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = size
        override fun writeTo(sink: BufferedSink) {
            open().use { input ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    sink.write(buf, 0, n)
                    done += n
                    if (size > 0) {
                        val pct = (done * 100 / size).toInt()
                        if (pct != last) { last = pct; onProgress(pct) }
                    }
                }
            }
        }
    }

    /** Streams a file into the public Storage bucket and returns its public URL. */
    fun upload(path: String, mime: String, size: Long, open: () -> InputStream, onProgress: (Int) -> Unit): String {
        val b = CloudConfig.BUCKET
        val r = authed("/storage/v1/object/$b/$path")
            .header("x-upsert", "false")
            .post(StreamBody(mime.toMediaTypeOrNull(), size, open, onProgress)).build()
        http.newCall(r).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, text, "Upload")
        }
        return "${CloudConfig.base()}/storage/v1/object/public/$b/$path"
    }

    fun deleteFile(path: String) {
        if (path.isEmpty()) return
        try { http.newCall(authed("/storage/v1/object/${CloudConfig.BUCKET}/$path").delete().build()).execute().close() } catch (_: Exception) { }
    }
}

package com.myra.assistant.wallpaper

import org.json.JSONObject

data class WallpaperItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val videoUrl: String,      // https url (cloud) or "asset:samples/xxx.mp4" (bundled sample)
    val thumbUrl: String,      // https url (cloud) or "asset:samples/xxx.jpg"
    val sizeBytes: Long = 0,
    val createdAt: Long = 0,
    val downloads: Long = 0,
    val views: Long = 0
) {
    val isBundled: Boolean get() = videoUrl.startsWith("asset:")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("description", description).put("category", category)
        .put("videoUrl", videoUrl).put("thumbUrl", thumbUrl).put("sizeBytes", sizeBytes)
        .put("createdAt", createdAt).put("downloads", downloads).put("views", views)

    companion object {
        fun fromJson(o: JSONObject) = WallpaperItem(
            o.getString("id"), o.optString("title"), o.optString("description"), o.optString("category", "General"),
            o.optString("videoUrl"), o.optString("thumbUrl"), o.optLong("sizeBytes"), o.optLong("createdAt"),
            o.optLong("downloads"), o.optLong("views")
        )

        /** The two demo wallpapers shipped inside the APK (real mp4 files in assets/samples). */
        val SAMPLES = listOf(
            WallpaperItem(
                "sample_aurora", "Neon Aurora", "Slow, glowing pink-violet aurora loop. Bundled sample wallpaper.",
                "Neon", "asset:samples/aurora.mp4", "asset:samples/aurora.jpg", createdAt = 1L
            ),
            WallpaperItem(
                "sample_waves", "Midnight Waves", "Flowing magenta waves in the dark. Bundled sample wallpaper.",
                "Abstract", "asset:samples/waves.mp4", "asset:samples/waves.jpg", createdAt = 0L
            )
        )
    }
}

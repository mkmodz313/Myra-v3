package com.myra.assistant.wallpaper

/**
 * Backend settings: Supabase (Auth + Postgres + Storage) - works on the free plan.
 *
 * SUPABASE_URL / SUPABASE_KEY are PUBLIC client values (Project Settings -> API: "Project URL" and the
 * anon / publishable key). They are not secrets: security comes from the Row Level Security policies created by
 * backend/supabase_setup.sql, which only let the admin e-mail write. There is NO admin password in the APK.
 *
 * Until both values are filled in, the app shows only the 2 bundled sample wallpapers and says so.
 */
object CloudConfig {
    const val SUPABASE_URL = "https://pxwhprlrkbdbtggwhkrh.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_yAYblg-E0-liA_poz-e0cQ_BZ9fugOB"

    /** Storage bucket (created by supabase_setup.sql). */
    const val BUCKET = "wallpapers"

    /** The ONLY account allowed to use the in-app Admin Panel (also enforced server-side by the policies). */
    const val ADMIN_EMAIL = "umarmalang496@gmail.com"

    /** How often the open gallery re-checks the catalog (periodic refresh). */
    const val REFRESH_INTERVAL_MS = 45_000L

    val isConfigured: Boolean
        get() = !SUPABASE_URL.contains("YOUR_") && !SUPABASE_KEY.startsWith("YOUR_")

    fun base() = SUPABASE_URL.trimEnd('/')
}

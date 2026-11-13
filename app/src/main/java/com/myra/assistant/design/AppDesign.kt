package com.myra.assistant.design

import android.graphics.Color
import com.myra.assistant.R

/** One launcher-style look ("Premium Apps Box"). Everything is drawn from these parameters, so adding a design = one line. */
data class AppDesign(
    val id: String,
    val name: String,
    val category: String,          // "Premium", "Android", "iOS"
    val bgTop: Int, val bgBottom: Int,
    val bgStyle: Int,              // 0 plain, 1 gold streaks, 2 neon glow, 3 soft blobs, 4 waves, 5 dots
    val accent: Int, val accent2: Int,
    val iconShape: Int,            // 0 circle, 1 squircle (iOS), 2 rounded, 3 slightly rounded
    val frameStyle: Int,           // 0 none, 1 thin, 2 glow, 3 double
    val frameColor: Int,
    val iconBg: Int,
    val labelPill: Boolean,
    val dockStyle: Int,            // 0 glass, 1 solid, 2 neon pill
    val textColor: Int,
    val art: Int = 0               // optional header artwork
) {
    /** corner radius as a fraction of the icon size */
    val cornerFraction: Float get() = when (iconShape) { 0 -> 0.5f; 1 -> 0.30f; 2 -> 0.22f; else -> 0.12f }
}

object AppDesigns {
    private fun c(h: String) = Color.parseColor(h)

    val ALL: List<AppDesign> = listOf(
        // ── Premium ─────────────────────────────────────────────────────────────
        AppDesign("mk_phantom", "MK Gold Phantom", "Premium", c("#050008"), c("#12041E"), 1, c("#FFC83D"), c("#8B2BFF"), 2, 3, c("#FFC83D"), c("#33000000"), true, 2, c("#FFFFFFFF"), R.drawable.design_art_phantom),
        AppDesign("lock_gold", "Premium Lock Gold", "Premium", c("#040004"), c("#1A0630"), 1, c("#FFB800"), c("#B13CFF"), 2, 3, c("#FFD25A"), c("#33000000"), true, 2, c("#FFFFFFFF"), R.drawable.design_art_lock),
        AppDesign("royal_purple", "Royal Purple", "Premium", c("#12002A"), c("#2B0B55"), 1, c("#E4B7FF"), c("#FFD36B"), 2, 2, c("#E4B7FF"), c("#33FFFFFF"), true, 2, c("#FFFFFFFF")),
        AppDesign("black_diamond", "Black Diamond", "Premium", c("#000000"), c("#101018"), 2, c("#CFE8FF"), c("#6E8BFF"), 2, 3, c("#DDEBFF"), c("#22FFFFFF"), true, 2, c("#FFFFFFFF")),
        AppDesign("rose_gold", "Rose Gold Luxe", "Premium", c("#1B0A12"), c("#3A1424"), 3, c("#FFB6A3"), c("#FF6FA8"), 1, 3, c("#FFC9B8"), c("#33FFFFFF"), true, 0, c("#FFFFFFFF")),
        AppDesign("myra_neon", "MYRA Neon Pink", "Premium", c("#12001A"), c("#2A0838"), 2, c("#FF4FD8"), c("#B01E9E"), 2, 2, c("#FF5BE0"), c("#33FFFFFF"), true, 2, c("#FFFFFFFF")),
        AppDesign("emerald_king", "Emerald King", "Premium", c("#00110C"), c("#032B20"), 1, c("#35F2B0"), c("#F5D76E"), 2, 3, c("#6CFFD0"), c("#22FFFFFF"), true, 2, c("#FFFFFFFF")),
        AppDesign("crimson_gold", "Crimson Gold", "Premium", c("#150004"), c("#3A000D"), 1, c("#FF3B3B"), c("#FFC83D"), 3, 3, c("#FFC83D"), c("#33000000"), true, 2, c("#FFFFFFFF")),
        // ── Android ─────────────────────────────────────────────────────────────
        AppDesign("md_blue", "Material You Blue", "Android", c("#0B1B3A"), c("#16336B"), 3, c("#8AB4FF"), c("#5B8CFF"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("md_dark", "Material Dark", "Android", c("#0D0F12"), c("#1A1F27"), 0, c("#A8C7FA"), c("#6DD58C"), 0, 0, c("#00000000"), c("#2AFFFFFF"), false, 1, c("#FFE3E8F0")),
        AppDesign("pixel_mint", "Pixel Mint", "Android", c("#0A2A24"), c("#12463C"), 3, c("#7BF1D3"), c("#A8FFE9"), 0, 1, c("#7BF1D3"), c("#26FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("amoled_green", "AMOLED Green", "Android", c("#000000"), c("#021208"), 5, c("#3DFF7A"), c("#1FB85A"), 3, 1, c("#3DFF7A"), c("#1A3DFF7A"), false, 1, c("#FFE8FFEF")),
        AppDesign("sunset_orange", "Sunset Orange", "Android", c("#2A0F05"), c("#6B2A0A"), 4, c("#FFB067"), c("#FF6B3D"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("ocean_teal", "Ocean Teal", "Android", c("#031C2B"), c("#06415C"), 4, c("#59E1FF"), c("#2BA3C7"), 2, 1, c("#59E1FF"), c("#26FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("cyber_violet", "Cyber Violet", "Android", c("#0A0220"), c("#1B0A45"), 2, c("#B28CFF"), c("#00E5FF"), 3, 2, c("#B28CFF"), c("#26FFFFFF"), false, 2, c("#FFFFFFFF")),
        AppDesign("cherry_red", "Cherry Red", "Android", c("#220309"), c("#4A0A18"), 3, c("#FF8FA3"), c("#FF3D5E"), 0, 1, c("#FF8FA3"), c("#2AFFFFFF"), false, 0, c("#FFFFFFFF")),
        // ── iOS ────────────────────────────────────────────────────────────────
        AppDesign("ios_light", "iOS Light Glass", "iOS", c("#CFE3FF"), c("#F4E4FF"), 3, c("#7FB2FF"), c("#FFB3E1"), 1, 0, c("#00000000"), c("#66FFFFFF"), false, 0, c("#FF1B1F2A")),
        AppDesign("ios_dark", "iOS Dark", "iOS", c("#0A0A12"), c("#1B1B2B"), 3, c("#4C6BFF"), c("#8E4CFF"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("ios_wave", "iOS Blue Wave", "iOS", c("#0B3A8F"), c("#2D8CFF"), 4, c("#A8D4FF"), c("#FFFFFF"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("ios_candy", "iOS Pink Candy", "iOS", c("#FFB3D9"), c("#FF7FB8"), 3, c("#FFFFFF"), c("#FFD1EC"), 1, 0, c("#00000000"), c("#55FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("ios_sunset", "iOS Sunset", "iOS", c("#3A1C71"), c("#FF8A5B"), 3, c("#FFD36B"), c("#FF6FA8"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("ios_mint", "iOS Mint", "iOS", c("#B6F2D8"), c("#5BD4B3"), 3, c("#FFFFFF"), c("#D8FFF1"), 1, 0, c("#00000000"), c("#55FFFFFF"), false, 0, c("#FF0B3B2E")),
        AppDesign("ios_graphite", "iOS Graphite", "iOS", c("#1C1C21"), c("#3A3A44"), 0, c("#BFC3D6"), c("#8A8FA8"), 1, 1, c("#44FFFFFF"), c("#26FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("ios_lavender", "iOS Lavender", "iOS", c("#DCCBFF"), c("#B79BFF"), 3, c("#FFFFFF"), c("#F3E8FF"), 1, 0, c("#00000000"), c("#55FFFFFF"), false, 0, c("#FF2A1B4D")),
        AppDesign("ios_midnight", "iOS Midnight", "iOS", c("#020617"), c("#0F1E4A"), 3, c("#5B7BFF"), c("#00D1FF"), 1, 0, c("#00000000"), c("#22FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("ios_aurora", "iOS Aurora", "iOS", c("#0A1B3D"), c("#1B0F3D"), 4, c("#4DFFB8"), c("#B28CFF"), 1, 0, c("#00000000"), c("#26FFFFFF"), false, 0, c("#FFFFFFFF"))
    )

    private const val PREF = "apps_design"
    fun byId(id: String?): AppDesign = ALL.firstOrNull { it.id == id } ?: ALL[0]
    fun current(ctx: android.content.Context): AppDesign =
        byId(ctx.getSharedPreferences(com.myra.assistant.utils.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE).getString(PREF, null))
    fun apply(ctx: android.content.Context, d: AppDesign) {
        ctx.getSharedPreferences(com.myra.assistant.utils.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE).edit().putString(PREF, d.id).apply()
    }
}

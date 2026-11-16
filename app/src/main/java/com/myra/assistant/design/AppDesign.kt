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
        AppDesign("ios_aurora", "iOS Aurora", "iOS", c("#0A1B3D"), c("#1B0F3D"), 4, c("#4DFFB8"), c("#B28CFF"), 1, 0, c("#00000000"), c("#26FFFFFF"), false, 0, c("#FFFFFFFF")),
        AppDesign("gold_felt_pink", "Pink Felt Gold", "Premium", c("#2A0018"), c("#4A0030"), 3, c("#FFC83D"), c("#FF4FD8"), 2, 3, c("#FFD98A"), c("#3A001A"), true, 2, c("#FFFFFF")),
        AppDesign("pink_knit", "Pink Knit Cozy", "Premium", c("#3A1420"), c("#5C2436"), 3, c("#FFD6E8"), c("#D8588C"), 2, 1, c("#FFE6F0"), c("#44241C"), true, 0, c("#FFFFFF")),
        AppDesign("barbie_glitter", "Barbie Glitter", "Premium", c("#3A0022"), c("#6B0040"), 2, c("#FF6FC8"), c("#FFFFFF"), 1, 2, c("#FF9ADE"), c("#44FF6FC8"), true, 2, c("#FFFFFF")),
        AppDesign("swan_crystal", "Crystal Swan", "Premium", c("#2A0030"), c("#5A0A60"), 2, c("#E6B8FF"), c("#FFFFFF"), 1, 3, c("#F2D4FF"), c("#33FFFFFF"), true, 2, c("#FFFFFF")),
        AppDesign("ruby_royale", "Ruby Royale", "Premium", c("#1A0005"), c("#4A000F"), 1, c("#FF3D5E"), c("#FFC83D"), 2, 3, c("#FF8FA3"), c("#33000000"), true, 2, c("#FFFFFF")),
        AppDesign("sapphire_king", "Sapphire King", "Premium", c("#00061A"), c("#071A4A"), 1, c("#4C8CFF"), c("#C8E8FF"), 2, 3, c("#8AB4FF"), c("#33000000"), true, 2, c("#FFFFFF")),
        AppDesign("amber_queen", "Amber Queen", "Premium", c("#1A0E00"), c("#4A2800"), 1, c("#FFB13D"), c("#FFE08A"), 2, 3, c("#FFCB70"), c("#33000000"), true, 2, c("#FFFFFF")),
        AppDesign("obsidian_rose", "Obsidian Rose", "Premium", c("#0A0006"), c("#200012"), 2, c("#FF7FB8"), c("#B13CFF"), 2, 2, c("#FFA8D4"), c("#22FFFFFF"), true, 2, c("#FFFFFF")),
        AppDesign("platinum_elite", "Platinum Elite", "Premium", c("#0A0A0C"), c("#1E1E24"), 0, c("#E8E8F0"), c("#B8B8C8"), 2, 3, c("#F0F0F8"), c("#2AFFFFFF"), true, 2, c("#FFFFFF")),
        AppDesign("velvet_wine", "Velvet Wine", "Premium", c("#1A0008"), c("#420018"), 3, c("#FF8FA8"), c("#FFD36B"), 2, 2, c("#FFB3C4"), c("#33FFFFFF"), true, 0, c("#FFFFFF")),
        AppDesign("mystic_teal_gold", "Mystic Teal Gold", "Premium", c("#00151A"), c("#043A3A"), 1, c("#FFD36B"), c("#4DFFD6"), 2, 3, c("#FFE08A"), c("#22FFFFFF"), true, 2, c("#FFFFFF")),
        AppDesign("pearl_blush", "Pearl Blush", "Premium", c("#2A1018"), c("#4A1F2E"), 3, c("#FFE0EC"), c("#FFB3C8"), 1, 1, c("#FFF0F5"), c("#44FFFFFF"), true, 0, c("#FF4A1F2E")),
        AppDesign("onyx_amber", "Onyx Amber", "Premium", c("#060402"), c("#1C1208"), 0, c("#FFB84D"), c("#FF7A3D"), 2, 3, c("#FFCB7A"), c("#2A000000"), true, 2, c("#FFFFFF")),
        AppDesign("galaxy_pink", "Galaxy Pink Dream", "Premium", c("#0A0020"), c("#2A0A4A"), 2, c("#FF6FD8"), c("#8C6FFF"), 2, 2, c("#FFA0E8"), c("#2AFFFFFF"), true, 2, c("#FFFFFF")),
        AppDesign("rose_marble", "Rose Marble", "Premium", c("#2A0A14"), c("#4A1A28"), 3, c("#FFC9D8"), c("#FF8FA8"), 1, 1, c("#FFE0E8"), c("#44FFFFFF"), true, 0, c("#FF2A0A14")),
        AppDesign("sunset_gold_royal", "Sunset Gold Royal", "Premium", c("#2A0800"), c("#5A2000"), 4, c("#FFC83D"), c("#FF6B3D"), 2, 3, c("#FFD98A"), c("#2A000000"), true, 2, c("#FFFFFF")),
        AppDesign("md_coral", "Material Coral", "Android", c("#2A0E0A"), c("#5C1E12"), 3, c("#FF8A65"), c("#FFAB91"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("md_indigo", "Material Indigo", "Android", c("#0B0F3A"), c("#1A226B"), 3, c("#9FA8FF"), c("#5B6BFF"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("md_amber", "Material Amber", "Android", c("#2A1E00"), c("#5C4200"), 3, c("#FFD54F"), c("#FFB300"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FF1A1200")),
        AppDesign("md_rose", "Material Rose", "Android", c("#2A0A14"), c("#5C1A2E"), 3, c("#FF80AB"), c("#F50057"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("pixel_lavender", "Pixel Lavender", "Android", c("#1A0F2A"), c("#2E1B4A"), 3, c("#C9A8FF"), c("#9B7BFF"), 0, 1, c("#C9A8FF"), c("#26FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("pixel_sky", "Pixel Sky", "Android", c("#061A2A"), c("#0F3355"), 3, c("#6BC8FF"), c("#A8E0FF"), 0, 1, c("#6BC8FF"), c("#26FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("amoled_blue", "AMOLED Blue", "Android", c("#000000"), c("#000814"), 5, c("#3D9BFF"), c("#1F5AB8"), 3, 1, c("#3D9BFF"), c("#1A3D9BFF"), false, 1, c("#FFE8F0FF")),
        AppDesign("amoled_purple", "AMOLED Purple", "Android", c("#000000"), c("#0A0014"), 5, c("#B03DFF"), c("#6E1FB8"), 3, 1, c("#B03DFF"), c("#1AB03DFF"), false, 1, c("#FFF0E8FF")),
        AppDesign("amoled_pink", "AMOLED Pink", "Android", c("#000000"), c("#140008"), 5, c("#FF3DAE"), c("#B81F6E"), 3, 1, c("#FF3DAE"), c("#1AFF3DAE"), false, 1, c("#FFFFE8F4")),
        AppDesign("nike_glass", "Nike Court Glass", "Android", c("#1A1A1E"), c("#2E2E36"), 0, c("#FF6B00"), c("#1A1A1E"), 0, 1, c("#FFFFFF"), c("#22FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("retro_bart", "Retro Yellow Glass", "Android", c("#2A2200"), c("#4A3A00"), 0, c("#FFD900"), c("#1A1A1E"), 0, 1, c("#FFFFFF"), c("#22FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("cyber_lime", "Cyber Lime", "Android", c("#0A1400"), c("#1C3300"), 2, c("#B8FF3D"), c("#6EDB1F"), 3, 2, c("#B8FF3D"), c("#26FFFFFF"), false, 2, c("#FFFFFF")),
        AppDesign("sunrise_peach", "Sunrise Peach", "Android", c("#2A1206"), c("#5C2E12"), 4, c("#FFB088"), c("#FF8A5B"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("forest_moss", "Forest Moss", "Android", c("#0A1A0A"), c("#1C3A1C"), 0, c("#9BE89B"), c("#5BC45B"), 2, 0, c("#00000000"), c("#26FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("steel_graphite", "Steel Graphite", "Android", c("#121418"), c("#22262E"), 0, c("#AEB8C8"), c("#6E7A8C"), 2, 1, c("#AEB8C8"), c("#26FFFFFF"), false, 1, c("#FFE8ECF2")),
        AppDesign("neon_tangerine", "Neon Tangerine", "Android", c("#2A0E00"), c("#5C2200"), 2, c("#FF8A3D"), c("#FFC83D"), 3, 2, c("#FF8A3D"), c("#26FFFFFF"), false, 2, c("#FFFFFF")),
        AppDesign("ios_rose", "iOS Rose Glass", "iOS", c("#FFD6E0"), c("#FFF0F4"), 3, c("#FFFFFF"), c("#FFB8CC"), 1, 0, c("#00000000"), c("#55FFFFFF"), false, 0, c("#FF3A1420")),
        AppDesign("ios_peach", "iOS Peach", "iOS", c("#FFE0C2"), c("#FFF4E0"), 3, c("#FFFFFF"), c("#FFC98A"), 1, 0, c("#00000000"), c("#55FFFFFF"), false, 0, c("#FF4A2A10")),
        AppDesign("ios_sky", "iOS Sky Glass", "iOS", c("#CDE8FF"), c("#EAF6FF"), 3, c("#FFFFFF"), c("#A8D4FF"), 1, 0, c("#00000000"), c("#66FFFFFF"), false, 0, c("#FF0F2A4A")),
        AppDesign("ios_cream", "iOS Cream Knit", "iOS", c("#F5EBDD"), c("#FFF8EF"), 3, c("#FFFFFF"), c("#F0C9D8"), 1, 0, c("#00000000"), c("#80FFFFFF"), false, 0, c("#FF4A2E2A")),
        AppDesign("ios_slate", "iOS Slate", "iOS", c("#2A2E3A"), c("#454B5C"), 3, c("#8A94AE"), c("#C9CFE0"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_gold", "iOS Gold Glass", "iOS", c("#2A1F00"), c("#5C4A10"), 3, c("#FFD98A"), c("#FFC83D"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_teal", "iOS Teal Glass", "iOS", c("#083A3A"), c("#116060"), 3, c("#6BE8D6"), c("#A8FFF0"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_plum", "iOS Plum", "iOS", c("#2A0A3A"), c("#521A6B"), 3, c("#D8A8FF"), c("#F0C9FF"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_coral", "iOS Coral Wave", "iOS", c("#FF6B5B"), c("#FF9A7F"), 4, c("#FFFFFF"), c("#FFD9CC"), 1, 0, c("#00000000"), c("#55FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_forest", "iOS Forest", "iOS", c("#0A2A14"), c("#1C4A28"), 4, c("#9BE8B3"), c("#D4FFE0"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_steel_blue", "iOS Steel Blue", "iOS", c("#12263A"), c("#1F3F5C"), 4, c("#9FC9FF"), c("#D4E8FF"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_berry", "iOS Berry", "iOS", c("#4A0A2A"), c("#7A1A4A"), 3, c("#FFB8D4"), c("#FFE0EE"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_honey", "iOS Honey", "iOS", c("#3A2600"), c("#6B4A00"), 3, c("#FFD98A"), c("#FFECC2"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_frost", "iOS Frost", "iOS", c("#D4E8F5"), c("#F0F8FF"), 0, c("#FFFFFF"), c("#AED4EC"), 1, 0, c("#00000000"), c("#80FFFFFF"), false, 0, c("#FF14364A")),
        AppDesign("ios_sand", "iOS Desert Sand", "iOS", c("#C9A877"), c("#E8D4B0"), 0, c("#FFFFFF"), c("#8A6B3D"), 1, 0, c("#00000000"), c("#66FFFFFF"), false, 0, c("#FF3A2A10")),
        AppDesign("ios_cocoa", "iOS Cocoa", "iOS", c("#2A1810"), c("#4A2E1C"), 0, c("#D8B38A"), c("#F0D4B0"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("rose_quartz", "Rose Quartz", "Premium", c("#2A1420"), c("#4A2436"), 3, c("#FFB8D4"), c("#FFE0EC"), 2, 1, c("#FFC9DC"), c("#33FFFFFF"), true, 0, c("#FF2A1420")),
        AppDesign("midnight_gold", "Midnight Gold", "Premium", c("#04040C"), c("#14102A"), 1, c("#FFD36B"), c("#8B2BFF"), 2, 3, c("#FFD36B"), c("#2A000000"), true, 2, c("#FFFFFF")),
        AppDesign("cherry_blossom", "Cherry Blossom", "Premium", c("#3A0A18"), c("#5C1A2E"), 3, c("#FFC2D8"), c("#FF8FB3"), 1, 1, c("#FFE0EC"), c("#44FFFFFF"), true, 0, c("#FF3A0A18")),
        AppDesign("ice_queen", "Ice Queen", "Premium", c("#021A2A"), c("#063A55"), 2, c("#A8E0FF"), c("#FFFFFF"), 2, 3, c("#CDEEFF"), c("#22FFFFFF"), true, 2, c("#FFFFFF")),
        AppDesign("mocha_rose", "Mocha Rose", "Premium", c("#2A1410"), c("#4A241C"), 3, c("#E8B89C"), c("#FFC9B8"), 2, 1, c("#F0D4C2"), c("#33FFFFFF"), true, 0, c("#FFFFFF")),
        AppDesign("neon_mint_gold", "Neon Mint Gold", "Premium", c("#00140E"), c("#082E22"), 2, c("#4DFFC2"), c("#FFD36B"), 2, 2, c("#4DFFC2"), c("#22FFFFFF"), true, 2, c("#FFFFFF")),
        AppDesign("dark_amethyst", "Dark Amethyst", "Premium", c("#0A0014"), c("#1E0540"), 1, c("#C28AFF"), c("#F0D4FF"), 2, 3, c("#C28AFF"), c("#2A000000"), true, 2, c("#FFFFFF")),
        AppDesign("blush_butter", "Blush Butter", "Premium", c("#3A2214"), c("#5C3C20"), 3, c("#FFE0A8"), c("#FFC9CC"), 1, 1, c("#FFEECC"), c("#44FFFFFF"), true, 0, c("#FF3A2214")),
        AppDesign("scarlet_crown", "Scarlet Crown", "Premium", c("#1A0003"), c("#4A000A"), 1, c("#FF4D6B"), c("#FFC83D"), 2, 3, c("#FF8FA8"), c("#2A000000"), true, 2, c("#FFFFFF")),
        AppDesign("silver_frost", "Silver Frost", "Premium", c("#0C0C10"), c("#1E1E28"), 0, c("#D8DCF0"), c("#9BA0C8"), 2, 3, c("#E8EAF8"), c("#2AFFFFFF"), true, 2, c("#FFFFFF")),
        AppDesign("peony_pink", "Peony Pink", "Android", c("#3A0A1E"), c("#6B1A3A"), 3, c("#FF7FB0"), c("#FFB8D4"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("cobalt_wave", "Cobalt Wave", "Android", c("#021A3A"), c("#0A3A6B"), 4, c("#5B9CFF"), c("#A8D4FF"), 0, 1, c("#5B9CFF"), c("#26FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("citrus_burst", "Citrus Burst", "Android", c("#2A1E00"), c("#5C4A00"), 0, c("#FFE04D"), c("#FFB03D"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FF1A1200")),
        AppDesign("deep_sea", "Deep Sea Teal", "Android", c("#00141A"), c("#022E3A"), 4, c("#4DD6E8"), c("#A8F0FF"), 2, 1, c("#4DD6E8"), c("#26FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("flamingo_pop", "Flamingo Pop", "Android", c("#3A0014"), c("#6B002E"), 2, c("#FF4D9E"), c("#FFFFFF"), 3, 2, c("#FF4D9E"), c("#26FFFFFF"), false, 2, c("#FFFFFF")),
        AppDesign("graphite_lime", "Graphite Lime", "Android", c("#101410"), c("#202A20"), 0, c("#B8FF6B"), c("#6ECB3D"), 2, 1, c("#B8FF6B"), c("#26FFFFFF"), false, 1, c("#FFE8FFE0")),
        AppDesign("royal_azure", "Royal Azure", "Android", c("#06103A"), c("#12216B"), 3, c("#7F9EFF"), c("#C9D8FF"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("sunset_magenta", "Sunset Magenta", "Android", c("#2A0020"), c("#5C0044"), 4, c("#FF5BC2"), c("#FFD36B"), 0, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("lagoon_green", "Lagoon Green", "iOS", c("#0A3A2E"), c("#1C6B52"), 4, c("#FFFFFF"), c("#9BE8CC"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_blossom", "iOS Blossom Glass", "iOS", c("#FFC2D8"), c("#FFE8F0"), 3, c("#FFFFFF"), c("#FF9ABF"), 1, 0, c("#00000000"), c("#66FFFFFF"), false, 0, c("#FF4A0A20")),
        AppDesign("ios_lilac", "iOS Lilac", "iOS", c("#D8C2FF"), c("#EEE0FF"), 0, c("#FFFFFF"), c("#B89CFF"), 1, 0, c("#00000000"), c("#66FFFFFF"), false, 0, c("#FF2A1A4A")),
        AppDesign("ios_tide", "iOS Tide Pool", "iOS", c("#063A4A"), c("#0F6680"), 4, c("#9BE8FF"), c("#D4F7FF"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_vanilla", "iOS Vanilla Knit", "iOS", c("#F0E4C2"), c("#FBF6E4"), 0, c("#FFFFFF"), c("#E0C98A"), 1, 0, c("#00000000"), c("#80FFFFFF"), false, 0, c("#FF3A2E14")),
        AppDesign("ios_amethyst", "iOS Amethyst", "iOS", c("#2A0A4A"), c("#4A1A7A"), 3, c("#C28AFF"), c("#E8D4FF"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_terracotta", "iOS Terracotta", "iOS", c("#4A2414"), c("#7A4224"), 3, c("#FFB088"), c("#FFD9BF"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_arctic", "iOS Arctic", "iOS", c("#0A2A3A"), c("#124A66"), 4, c("#A8E8FF"), c("#FFFFFF"), 1, 0, c("#00000000"), c("#2AFFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_rosegold", "iOS Rose Gold", "iOS", c("#4A1A24"), c("#7A3242"), 3, c("#FFD9B3"), c("#FFB8A3"), 1, 0, c("#00000000"), c("#33FFFFFF"), false, 0, c("#FFFFFF")),
        AppDesign("ios_mono", "iOS Monochrome", "iOS", c("#1A1A1E"), c("#303038"), 0, c("#E8E8EC"), c("#A0A0AC"), 1, 0, c("#00000000"), c("#26FFFFFF"), false, 0, c("#FFFFFF")),
    )

    private const val PREF = "apps_design"
    fun byId(id: String?): AppDesign = ALL.firstOrNull { it.id == id } ?: ALL[0]
    fun current(ctx: android.content.Context): AppDesign =
        byId(ctx.getSharedPreferences(com.myra.assistant.utils.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE).getString(PREF, null))
    fun apply(ctx: android.content.Context, d: AppDesign) {
        ctx.getSharedPreferences(com.myra.assistant.utils.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE).edit().putString(PREF, d.id).apply()
    }
}

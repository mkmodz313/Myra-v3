package com.myra.assistant.keyboard

import android.content.Context
import android.graphics.Paint

/** One thing that can be tapped in the emoji / symbol / Islamic pages. */
class KbItem(val text: String, val title: String? = null)

/**
 * One swipeable page of the emoji panel.
 * kind GRID = many small cells (emoji, symbols). kind LIST = full-width rows (Kalimat, Durood, Zikr).
 */
class KbPage(
    val tab: String,
    val kind: Int,
    val cols: Int,
    val textSize: Float,
    val boxed: Boolean,
    val getter: () -> List<KbItem>
) {
    companion object {
        const val GRID = 0
        const val LIST = 1
    }
}

object KbPages {

    private fun items(s: String): List<KbItem> =
        s.trim().split(" ").filter { it.isNotEmpty() }.map { KbItem(it) }

    /** Every printable code point of the given ranges that this phone's fonts can really draw. */
    private fun range(vararg rs: IntRange): List<KbItem> {
        val p = Paint()
        val seen = LinkedHashSet<String>()
        for (r in rs) for (cp in r) {
            if (!Character.isDefined(cp)) continue
            val t = Character.getType(cp)
            if (t == Character.CONTROL.toInt() || t == Character.FORMAT.toInt() ||
                t == Character.NON_SPACING_MARK.toInt() || t == Character.SPACE_SEPARATOR.toInt() ||
                t == Character.PRIVATE_USE.toInt() || t == Character.SURROGATE.toInt()
            ) continue
            val s = String(Character.toChars(cp))
            if (p.hasGlyph(s)) seen.add(s)
        }
        return seen.map { KbItem(it) }
    }

    private fun merge(first: String, rest: List<KbItem>): List<KbItem> {
        val seen = LinkedHashSet<String>()
        for (s in first.trim().split(" ")) if (s.isNotEmpty()) seen.add(s)
        for (i in rest) seen.add(i.text)
        return seen.map { KbItem(it) }
    }

    private fun li(title: String, text: String) = KbItem(text, title)

    // ------------------------------------------------------------------ Islamic content
    private val KALIMAT = listOf(
        li("Kalima Tayyab (1)", "لَا إِلٰهَ إِلَّا اللّٰهُ مُحَمَّدٌ رَسُولُ اللّٰهِ"),
        li("Kalima Shahadat (2)", "أَشْهَدُ أَنْ لَا إِلٰهَ إِلَّا اللّٰهُ وَحْدَهُ لَا شَرِيكَ لَهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ"),
        li("Kalima Tamjeed (3)", "سُبْحَانَ اللّٰهِ وَالْحَمْدُ لِلّٰهِ وَلَا إِلٰهَ إِلَّا اللّٰهُ وَاللّٰهُ أَكْبَرُ وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰهِ الْعَلِيِّ الْعَظِيمِ"),
        li("Kalima Tawheed (4)", "لَا إِلٰهَ إِلَّا اللّٰهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ يُحْيِي وَيُمِيتُ وَهُوَ حَيٌّ لَا يَمُوتُ أَبَدًا أَبَدًا ذُو الْجَلَالِ وَالْإِكْرَامِ بِيَدِهِ الْخَيْرُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ"),
        li("Kalima Astaghfar (5)", "أَسْتَغْفِرُ اللّٰهَ رَبِّي مِنْ كُلِّ ذَنْبٍ أَذْنَبْتُهُ عَمَدًا أَوْ خَطَأً سِرًّا أَوْ عَلَانِيَةً وَأَتُوبُ إِلَيْهِ مِنَ الذَّنْبِ الَّذِي أَعْلَمُ وَمِنَ الذَّنْبِ الَّذِي لَا أَعْلَمُ إِنَّكَ أَنْتَ عَلَّامُ الْغُيُوبِ وَسَتَّارُ الْعُيُوبِ وَغَفَّارُ الذُّنُوبِ وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰهِ الْعَلِيِّ الْعَظِيمِ"),
        li("Kalima Radd-e-Kufr (6)", "اَللّٰهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ أَنْ أُشْرِكَ بِكَ شَيْئًا وَأَنَا أَعْلَمُ بِهِ وَأَسْتَغْفِرُكَ لِمَا لَا أَعْلَمُ بِهِ تُبْتُ عَنْهُ وَتَبَرَّأْتُ مِنَ الْكُفْرِ وَالشِّرْكِ وَالْكِذْبِ وَالْغِيبَةِ وَالْبِدْعَةِ وَالنَّمِيمَةِ وَالْفَوَاحِشِ وَالْبُهْتَانِ وَالْمَعَاصِي كُلِّهَا وَأَسْلَمْتُ وَأَقُولُ لَا إِلٰهَ إِلَّا اللّٰهُ مُحَمَّدٌ رَسُولُ اللّٰهِ"),
        li("Bismillah", "بِسْمِ اللّٰهِ الرَّحْمٰنِ الرَّحِيمِ"),
        li("Surah Ikhlas", "قُلْ هُوَ اللّٰهُ أَحَدٌ ۝ اللّٰهُ الصَّمَدُ ۝ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ")
    )

    private val DUROOD = listOf(
        li("Durood (short)", "صَلَّى اللّٰهُ عَلَيْهِ وَسَلَّمَ"),
        li("Durood (ﷺ)", "ﷺ"),
        li("Durood-e-Pak", "اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ وَعَلٰى آلِ مُحَمَّدٍ"),
        li("Durood-e-Ibrahim", "اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ وَعَلٰى آلِ مُحَمَّدٍ كَمَا صَلَّيْتَ عَلٰى إِبْرَاهِيمَ وَعَلٰى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ ۝ اَللّٰهُمَّ بَارِكْ عَلٰى مُحَمَّدٍ وَعَلٰى آلِ مُحَمَّدٍ كَمَا بَارَكْتَ عَلٰى إِبْرَاهِيمَ وَعَلٰى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ"),
        li("Salawat", "اَللّٰهُمَّ صَلِّ عَلٰى سَيِّدِنَا مُحَمَّدٍ وَعَلٰى آلِهِ وَصَحْبِهِ وَسَلِّمْ"),
        li("Durood on Habib", "صَلَّى اللّٰهُ عَلٰى حَبِيبِهِ سَيِّدِنَا مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَسَلَّمَ"),
        li("Salam", "اَلصَّلَاةُ وَالسَّلَامُ عَلَيْكَ يَا رَسُولَ اللّٰهِ"),
        li("Salam (Habib)", "اَلصَّلَاةُ وَالسَّلَامُ عَلَيْكَ يَا حَبِيبَ اللّٰهِ"),
        li("Radi Allahu anhu", "رَضِيَ اللّٰهُ عَنْهُ"),
        li("Alayhis Salam", "عَلَيْهِ السَّلَامُ"),
        li("Rahimahullah", "رَحِمَهُ اللّٰهُ")
    )

    private val ZIKR = listOf(
        li("Assalamu Alaikum", "اَلسَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللّٰهِ وَبَرَكَاتُهُ"),
        li("Wa Alaikum Assalam", "وَعَلَيْكُمُ السَّلَامُ وَرَحْمَةُ اللّٰهِ وَبَرَكَاتُهُ"),
        li("SubhanAllah", "سُبْحَانَ اللّٰهِ"),
        li("Alhamdulillah", "اَلْحَمْدُ لِلّٰهِ"),
        li("Allahu Akbar", "اَللّٰهُ أَكْبَرُ"),
        li("La ilaha illallah", "لَا إِلٰهَ إِلَّا اللّٰهُ"),
        li("Astaghfirullah", "أَسْتَغْفِرُ اللّٰهَ"),
        li("MashaAllah", "مَا شَاءَ اللّٰهُ"),
        li("InshaAllah", "إِنْ شَاءَ اللّٰهُ"),
        li("TabarakAllah", "تَبَارَكَ اللّٰهُ"),
        li("JazakAllah Khair", "جَزَاكَ اللّٰهُ خَيْرًا"),
        li("BarakAllahu Feek", "بَارَكَ اللّٰهُ فِيكَ"),
        li("Ameen", "آمِينَ"),
        li("Subhanallahi wa bihamdihi", "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ سُبْحَانَ اللّٰهِ الْعَظِيمِ"),
        li("La hawla wa la quwwata", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰهِ"),
        li("Hasbunallah", "حَسْبُنَا اللّٰهُ وَنِعْمَ الْوَكِيلُ"),
        li("Innalillahi", "إِنَّا لِلّٰهِ وَإِنَّا إِلَيْهِ رَاجِعُونَ"),
        li("Bismillahi tawakkaltu", "بِسْمِ اللّٰهِ تَوَكَّلْتُ عَلَى اللّٰهِ"),
        li("Rabbi zidni ilma", "رَبِّ زِدْنِي عِلْمًا"),
        li("Rabbana atina", "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ"),
        li("Jumma Mubarak", "جُمُعَةٌ مُبَارَكَةٌ"),
        li("Eid Mubarak", "عِيدٌ مُبَارَكٌ"),
        li("Ramadan Kareem", "رَمَضَانُ كَرِيمٌ")
    )

    private const val ISLAMIC_SYMBOLS =
        "ﷺ ﷲ ﷻ ﷴ ﷵ ﷶ ﷷ ﷸ ﷹ ۞ ۩ ؏ ﴾ ﴿ ☪ ☫ ☬ ☾ ☽ ✦ ✧ ❂ ۝ ؋ ٭ ؟ ؛ ، ﷽ 🕌 🕋 🌙 ⭐ 📿 🤲 ☝️ 🕊️ 🌴 🐪 🌹 💚"

    private const val STARS =
        "✦ ✧ ✩ ✪ ✫ ✬ ✭ ✮ ✯ ✰ ★ ☆ ✶ ✷ ✸ ✹ ✺ ✻ ✼ ✽ ✾ ✿ ❀ ❁ ❂ ❃ ❄ ❅ ❆ ❇ ❈ ❉ ❊ ❋ ✢ ✣ ✤ ✥ ✱ ✲ ✳ ✴ ✵ ⁂ ⁎ ⁑ ❖ ◈ ⬖ ⬗ ⬘ ⬙ ♔ ♕ ♚ ♛ ☬ ☸ ☘ ♨ ✇ ✆ ✈ ✉ ✎ ✐ ✑ ✒ ✓ ✔ ✕ ✖ ✗ ✘"

    private const val DECOR =
        "꧁꧂ ༺༻ ꧁༺ ༻꧂ ❝❞ ❛❜ 【】 『』 「」 《》 〔〕 〖〗 ⦅⦆ ❮❯ ❰❱ ⟦⟧ ⟪⟫ ⌈⌉ ⌊⌋ ‹› «» ⸨⸩ ⁅⁆ " +
        "━━━━━━ ═════ ┈┈┈┈┈ ──────── ━━⊱✿⊰━━ ✦•┈┈┈┈•✦ ★彡 彡★ ꧁༒☬ ☬༒꧂ ♡ᵕ♡ ❥❥❥ ◥◤◥◤ ▁▂▃▄▅▆▇ ▇▆▅▄▃▂▁ " +
        "┏━━━━┓ ┗━━━━┛ ╔═══╗ ╚═══╝ ⊹˚₊ ‧₊˚ ·˚ ༘ ⋆｡°✩ ✧･ﾟ: ･ﾟ✧ ❀·̩͙ ᯓ★ ⋆⭒˚｡⋆ ☆*:.｡. ✿◕‿◕✿ ➳♡゛ ⚜ ⚝ ✺◟ ⁺˳˚ ꒰ ꒱ ໒ ꒱ ˗ˏˋ ˎˊ˗"

    private const val FACES =
        "ʕ•ᴥ•ʔ (づ｡◕‿‿◕｡)づ (｡♥‿♥｡) ٩(◕‿◕｡)۶ (≧◡≦)♡ ಠ_ಠ (╯°□°）╯︵┻━┻ ( ͡° ͜ʖ ͡°) (ᵔᴥᵔ) (◕‿◕✿) ⸜(｡˃ ᵕ ˂ )⸝♡ " +
        "(✿◠‿◠) (¬‿¬) (ง •̀_•́)ง (っ◔◡◔)っ ♥ ʕ·ᴥ·ʔ (≖‿≖) (＾▽＾) (T_T) (；′⌒`) (๑>◡<๑) ᕦ(ò_óˇ)ᕤ (ノ◕ヮ◕)ノ*:･ﾟ✧ " +
        "(•‿•) (︶｡︶✽) (◍•ᴗ•◍) (⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄) (๑˃ᴗ˂)ﻭ ( ˘ ³˘)♥ (づ￣ ³￣)づ ε=ε=(ノ≧∇≦)ノ ┬┴┬┴┤(･_├┬┴┬┴"

    // ------------------------------------------------------------------ pages
    fun build(ctx: Context): List<KbPage> {
        val p = Paint()
        val out = ArrayList<KbPage>()

        // 0 : recent emoji
        out.add(KbPage("🕒", KbPage.GRID, 8, 24f, false) { KbPrefs.recents(ctx).map { KbItem(it) } })

        // emoji
        for (c in KbEmoji.cats) {
            val list = items(c.second.joinToString(" "))
            out.add(KbPage(c.first, KbPage.GRID, 8, 24f, false) { list })
        }

        // Islamic
        out.add(KbPage("☪️", KbPage.LIST, 1, 19f, true) { KALIMAT })
        out.add(KbPage(if (p.hasGlyph("ﷺ")) "ﷺ" else "SAW", KbPage.LIST, 1, 19f, true) { DUROOD })
        out.add(KbPage("📿", KbPage.LIST, 1, 19f, true) { ZIKR })
        val islamicSym = items(ISLAMIC_SYMBOLS)
        out.add(KbPage("🕌", KbPage.GRID, 6, 24f, true) { islamicSym })

        // 500+ unique symbols (only glyphs this phone can really draw are shown)
        val stars by lazy { merge(STARS, range(0x2700..0x27BF, 0x2B50..0x2B59)) }
        val arrows by lazy { range(0x2190..0x21FF, 0x27F0..0x27FF, 0x2900..0x297F, 0x2B00..0x2B11, 0x2794..0x27BF) }
        val math by lazy { range(0x2200..0x22FF, 0x2A00..0x2A1F, 0x00B1..0x00B1, 0x00D7..0x00D7, 0x00F7..0x00F7, 0x03C0..0x03C0) }
        val shapes by lazy { range(0x25A0..0x25FF, 0x2B1A..0x2B2F) }
        val misc by lazy { range(0x2600..0x26FF) }
        val money by lazy { range(0x20A0..0x20BF, 0x2100..0x214F, 0x2150..0x218F, 0x2460..0x24FF) }
        val boxes by lazy { range(0x2500..0x259F, 0x2800..0x2840) }
        val decor by lazy { items(DECOR) }
        val faces by lazy { items(FACES) }

        out.add(KbPage("✦", KbPage.GRID, 7, 22f, true) { stars })
        out.add(KbPage("➜", KbPage.GRID, 7, 22f, true) { arrows })
        out.add(KbPage("∑", KbPage.GRID, 7, 22f, true) { math })
        out.add(KbPage("◆", KbPage.GRID, 7, 22f, true) { shapes })
        out.add(KbPage("☯", KbPage.GRID, 7, 22f, true) { misc })
        out.add(KbPage("₹", KbPage.GRID, 7, 20f, true) { money })
        out.add(KbPage("▒", KbPage.GRID, 7, 22f, true) { boxes })
        out.add(KbPage("꧁", KbPage.GRID, 3, 15f, true) { decor })
        out.add(KbPage("ʕ•ᴥ•ʔ", KbPage.GRID, 2, 15f, true) { faces })
        return out
    }
}

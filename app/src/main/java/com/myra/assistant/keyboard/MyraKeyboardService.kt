package com.myra.assistant.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * MYRA Keyboard - a real system keyboard (IME). Works in WhatsApp, YouTube, Chrome, every app,
 * once it is enabled and selected from MYRA > Settings > MYRA Keyboard.
 */
class MyraKeyboardService : InputMethodService(), MyraKeyboardView.Listener {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private var root: KbRootLayout? = null
    private lateinit var kb: MyraKeyboardView
    private lateinit var toolbar: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var emojiPanel: LinearLayout
    private lateinit var clipPanel: LinearLayout
    private lateinit var themePanel: LinearLayout
    private lateinit var fontPanel: LinearLayout
    private lateinit var fontList: RecyclerView
    private lateinit var fontAdapter: KbFontAdapter
    private var fontBtn: TextView? = null
    private var curFont: KbFontStyle = KbFonts.normal
    private val composing = StringBuilder()
    private lateinit var suggest: KbSuggest
    private lateinit var strip: LinearLayout
    private val stripViews = ArrayList<TextView>()
    private var suggestOn = true
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var clipList: LinearLayout
    private lateinit var pagerAdapter: KbPagerAdapter
    private lateinit var emojiPager: RecyclerView
    private var pages: List<KbPage> = emptyList()
    private var curPage = 1
    private val tabViews = ArrayList<TextView>()
    private lateinit var emojiTabs: LinearLayout
    private lateinit var styleRow: LinearLayout
    private lateinit var themeRow: LinearLayout
    private val toolButtons = ArrayList<TextView>()
    private val panelButtons = ArrayList<TextView>()

    private var theme: KbTheme = KbThemes.byId("pinkheart")
    private var appliedStamp = -1L
    private var curLangs: List<String> = listOf("en")
    private var sound = false
    private var vib = true
    private var autoCaps = true
    private var clipboard: ClipboardManager? = null
    private var vibrator: Vibrator? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        try {
            clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard?.addPrimaryClipChangedListener(clipListener)
        } catch (e: Throwable) {
        }
        vibrator = try {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (e: Throwable) {
            null
        }
    }

    override fun onDestroy() {
        try {
            clipboard?.removePrimaryClipChangedListener(clipListener)
        } catch (e: Throwable) {
        }
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ------------------------------------------------------------------ view creation
    override fun onCreateInputView(): View {
        val r = KbRootLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.clipChildren = false
        r.clipToPadding = false

        toolbar = buildToolbar()
        r.addView(toolbar, LinearLayout.LayoutParams(MATCH, dp(44)))
        if (!::suggest.isInitialized) suggest = KbSuggest(this)
        strip = buildStrip()
        r.addView(strip, LinearLayout.LayoutParams(MATCH, dp(38)))

        content = FrameLayout(this)
        content.clipChildren = false
        content.clipToPadding = false
        kb = MyraKeyboardView(this)
        kb.listener = this
        content.addView(kb, FrameLayout.LayoutParams(MATCH, MATCH))

        emojiPanel = buildEmojiPanel()
        clipPanel = buildClipPanel()
        themePanel = buildThemePanel()
        fontPanel = buildFontPanel()
        for (p in listOf(emojiPanel, clipPanel, themePanel, fontPanel)) {
            p.visibility = View.GONE
            content.addView(p, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        r.addView(content, LinearLayout.LayoutParams(MATCH, fixedContentH()))
        root = r
        applyAll()
        return r
    }

    /** The keyboard (and every panel on top of it) always has exactly this height. */
    private fun fixedContentH(): Int =
        (dp(50) * KbPrefs.heightPct(this) / 100f * 4f + dp(10)).toInt()

    private fun applyFixedHeight() {
        val lp = content.layoutParams
        val h = fixedContentH()
        if (lp != null && lp.height != h) {
            lp.height = h
            content.layoutParams = lp
        }
        root?.requestLayout()
    }

    private fun pill(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(radiusDp).toFloat()
        g.setColor(color)
        return g
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    private fun buildToolbar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), dp(5), dp(4), dp(3))
        fun add(label: String, size: Float, onClick: () -> Unit) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = size
            tv.gravity = Gravity.CENTER
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(0, MATCH, 1f)
            lp.setMargins(dp(2), 0, dp(2), 0)
            bar.addView(tv, lp)
            toolButtons.add(tv)
        }
        add("😀", 18f) { showPanel(emojiPanel) }
        add("📋", 16f) { showPanel(clipPanel) }
        add("🎨", 16f) { showPanel(themePanel) }
        add("Aa", 13f) { showPanel(fontPanel) }
        fontBtn = toolButtons[toolButtons.size - 1]
        add("ALL", 11f) { editAction(android.R.id.selectAll) }
        add("COPY", 11f) { editAction(android.R.id.copy) }
        add("CUT", 11f) { editAction(android.R.id.cut) }
        add("PASTE", 11f) { editAction(android.R.id.paste) }
        add("⚙️", 16f) { openSettings() }
        return bar
    }

    private fun editAction(id: Int) {
        try {
            currentInputConnection?.performContextMenuAction(id)
        } catch (e: Throwable) {
        }
    }

    private fun openSettings() {
        try {
            val i = Intent(this, KeyboardSettingsActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Throwable) {
        }
    }

    private fun panelHeader(title: String, extra: String?, onExtra: (() -> Unit)?, withDelete: Boolean): LinearLayout {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        h.setPadding(dp(8), dp(4), dp(8), dp(2))
        val t = TextView(this)
        t.text = title
        t.textSize = 13f
        t.typeface = Typeface.DEFAULT_BOLD
        h.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        panelButtons.add(t)
        fun btn(label: String, onClick: () -> Unit) {
            val b = TextView(this)
            b.text = label
            b.textSize = 12f
            b.typeface = Typeface.DEFAULT_BOLD
            b.gravity = Gravity.CENTER
            b.setPadding(dp(12), dp(5), dp(12), dp(5))
            b.setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.leftMargin = dp(6)
            h.addView(b, lp)
            panelButtons.add(b)
        }
        if (extra != null && onExtra != null) btn(extra) { onExtra() }
        if (withDelete) btn("⌫") { onDelete() }
        btn("ABC") { showPanel(null) }
        return h
    }

    private fun buildEmojiPanel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        // one fixed row: category tabs (scroll) + delete + ABC
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), dp(3), dp(4), dp(3))
        val sv = HorizontalScrollView(this)
        sv.isHorizontalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        emojiTabs = LinearLayout(this)
        emojiTabs.orientation = LinearLayout.HORIZONTAL
        sv.addView(emojiTabs, FrameLayout.LayoutParams(WRAP, MATCH))
        bar.addView(sv, LinearLayout.LayoutParams(0, MATCH, 1f))
        fun btn(label: String, onClick: () -> Unit) {
            val b = TextView(this)
            b.text = label
            b.textSize = 12f
            b.typeface = Typeface.DEFAULT_BOLD
            b.gravity = Gravity.CENTER
            b.setPadding(dp(12), 0, dp(12), 0)
            b.setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(WRAP, MATCH)
            lp.leftMargin = dp(5)
            bar.addView(b, lp)
            panelButtons.add(b)
        }
        btn("⌫") { onDelete() }
        btn("ABC") { showPanel(null) }
        p.addView(bar, LinearLayout.LayoutParams(MATCH, dp(40)))

        pages = KbPages.build(this)
        pagerAdapter = KbPagerAdapter(pages, { theme }) { e -> commitEmoji(e) }
        emojiPager = RecyclerView(this)
        emojiPager.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        emojiPager.overScrollMode = View.OVER_SCROLL_NEVER
        emojiPager.setHasFixedSize(true)
        emojiPager.setItemViewCacheSize(2)
        emojiPager.adapter = pagerAdapter
        PagerSnapHelper().attachToRecyclerView(emojiPager)
        emojiPager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager as LinearLayoutManager
                    val pos = lm.findFirstCompletelyVisibleItemPosition()
                    if (pos >= 0 && pos != curPage) selectTab(pos)
                }
            }
        })
        p.addView(emojiPager, LinearLayout.LayoutParams(MATCH, 0, 1f))

        for ((i, pg) in pages.withIndex()) {
            val tv = TextView(this)
            tv.text = pg.tab
            tv.textSize = 19f
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(11), 0, dp(11), 0)
            tv.setOnClickListener {
                emojiPager.scrollToPosition(i)
                selectTab(i)
            }
            emojiTabs.addView(tv, LinearLayout.LayoutParams(WRAP, MATCH))
            tabViews.add(tv)
        }
        emojiPager.scrollToPosition(curPage)
        return p
    }

    private fun selectTab(i: Int) {
        curPage = i
        for ((idx, tv) in tabViews.withIndex()) {
            tv.background = if (idx == i) pill(withAlpha(theme.accent, 110), 12) else null
        }
        val tv = tabViews.getOrNull(i) ?: return
        (emojiTabs.parent as? HorizontalScrollView)?.post {
            (emojiTabs.parent as? HorizontalScrollView)?.smoothScrollTo(maxOf(0, tv.left - dp(60)), 0)
        }
    }

    private fun commitEmoji(e: String) {
        flushComposing()
        currentInputConnection?.commitText(e, 1)
        if (e.length <= 12) KbPrefs.addRecent(this, e)
    }

    private fun buildStrip(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        stripViews.clear()
        for (i in 0 until 3) {
            val tv = TextView(this)
            tv.textSize = 16f
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.setPadding(dp(6), 0, dp(6), 0)
            tv.setOnClickListener {
                val w = tv.text?.toString() ?: ""
                if (w.isNotEmpty()) pickSuggestion(w)
            }
            bar.addView(tv, LinearLayout.LayoutParams(0, MATCH, 1f))
            stripViews.add(tv)
        }
        return bar
    }

    private fun suggestionsAllowed(): Boolean {
        if (curFont.id != "normal") return false
        val it = currentInputEditorInfo?.inputType ?: return false
        if ((it and android.text.InputType.TYPE_MASK_CLASS) != android.text.InputType.TYPE_CLASS_TEXT) return false
        val v = it and android.text.InputType.TYPE_MASK_VARIATION
        return when (v) {
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_URI -> false
            else -> true
        }
    }

    private fun isWordChar(ch: Char): Boolean {
        if (ch == '\'') return true
        if (Character.isLetter(ch)) return true
        val ty = Character.getType(ch)
        return ty == Character.NON_SPACING_MARK.toInt() || ty == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun wordBeforeCursor(): String {
        val b = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: return ""
        var i = b.length
        while (i > 0 && isWordChar(b[i - 1])) i--
        return b.substring(i)
    }

    /** Refresh the three suggestion slots (called after every key). */
    private fun updateSuggestions() {
        ui.removeCallbacks(suggestRunnable)
        ui.postDelayed(suggestRunnable, 25)
    }

    private val suggestRunnable = Runnable {
        if (!::strip.isInitialized || !::suggest.isInitialized) return@Runnable
        val list: List<String> = if (suggestOn && suggestionsAllowed()) {
            val pre = wordBeforeCursor()
            val s = suggest.suggest(pre)
            if (pre.length >= 2 && s.none { it.equals(pre, ignoreCase = true) }) {
                (listOf(pre) + s).take(3)
            } else s
        } else emptyList()
        // best match in the middle, like other keyboards
        val order = when (list.size) {
            0 -> listOf("", "", "")
            1 -> listOf("", list[0], "")
            2 -> listOf(list[1], list[0], "")
            else -> listOf(list[1], list[0], list[2])
        }
        for (i in 0 until 3) stripViews[i].text = order[i]
    }

    private fun pickSuggestion(w: String) {
        val ic = currentInputConnection ?: return
        flushComposing()
        val pre = wordBeforeCursor()
        if (pre.isNotEmpty()) ic.deleteSurroundingText(pre.length, 0)
        ic.commitText(w + " ", 1)
        suggest.learn(w)
        updateCaps()
        updateSuggestions()
    }

    private fun learnLastWord() {
        if (!::suggest.isInitialized || !suggestionsAllowed()) return
        val w = wordBeforeCursor()
        if (w.length >= 2) suggest.learn(w)
    }

    private fun buildFontPanel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.addView(panelHeader("Font Style", "Convert", { convertAll() }, false))
        fontList = RecyclerView(this)
        fontList.layoutManager = LinearLayoutManager(this)
        fontList.overScrollMode = View.OVER_SCROLL_NEVER
        fontList.clipToPadding = false
        fontList.setPadding(dp(4), 0, dp(4), dp(6))
        fontAdapter = KbFontAdapter({ theme }, { curFont.id }) { st -> selectFont(st) }
        fontList.adapter = fontAdapter
        p.addView(fontList, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return p
    }

    /** The letters printed on the keys follow the chosen font. */
    private fun syncFontToKeys() {
        val f = curFont
        kb.labelMap = if (f.id == "normal") null else { s -> f.keycap(s) }
        kb.invalidate()
    }

    private fun selectFont(st: KbFontStyle) {
        flushComposing()
        curFont = st
        KbPrefs.setFontStyle(this, st.id)
        syncFontToKeys()
        styleChrome()
        showPanel(null)
    }

    /** Finish the word that is being typed in a stylish font (so it stays as normal text). */
    private fun flushComposing() {
        if (composing.isNotEmpty()) {
            try {
                currentInputConnection?.finishComposingText()
            } catch (e: Throwable) {
            }
            composing.setLength(0)
        }
    }

    /** Convert the selected text (or the whole text of the box) into the chosen font once. */
    private fun convertAll() {
        val f = curFont
        if (f.id == "normal") {
            Toast.makeText(this, "Pehle koi font chunein", Toast.LENGTH_SHORT).show()
            return
        }
        flushComposing()
        val ic = currentInputConnection ?: return
        try {
            val sel = ic.getSelectedText(0)
            if (!sel.isNullOrEmpty()) {
                ic.commitText(f.render(sel.toString()), 1)
            } else {
                val b = ic.getTextBeforeCursor(5000, 0)?.toString() ?: ""
                val a = ic.getTextAfterCursor(5000, 0)?.toString() ?: ""
                if (b.isEmpty() && a.isEmpty()) {
                    Toast.makeText(this, "Pehle kuch likhein ya select karein", Toast.LENGTH_SHORT).show()
                    return
                }
                ic.deleteSurroundingText(b.length, a.length)
                ic.commitText(f.render(b + a), 1)
            }
        } catch (e: Throwable) {
        }
        showPanel(null)
    }

    private fun isWordText(t: String): Boolean {
        if (t.isEmpty()) return false
        for (ch in t) {
            val ty = Character.getType(ch)
            val ok = Character.isLetterOrDigit(ch) ||
                ty == Character.NON_SPACING_MARK.toInt() ||
                ty == Character.COMBINING_SPACING_MARK.toInt()
            if (!ok) return false
        }
        return true
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (composing.isNotEmpty() && (candidatesStart < 0 || newSelStart != candidatesEnd)) {
            flushComposing()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flushComposing()
        if (::suggest.isInitialized) suggest.save()
        super.onFinishInputView(finishingInput)
    }

    private fun buildClipPanel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.addView(panelHeader("Clipboard", "Clear", {
            KbPrefs.clearClips(this)
            refreshClips()
        }, false))
        val sv = ScrollView(this)
        clipList = LinearLayout(this)
        clipList.orientation = LinearLayout.VERTICAL
        clipList.setPadding(dp(8), dp(2), dp(8), dp(8))
        sv.addView(clipList, FrameLayout.LayoutParams(MATCH, WRAP))
        p.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return p
    }

    private fun refreshClips() {
        captureClip()
        clipList.removeAllViews()
        val clips = KbPrefs.clips(this)
        if (clips.isEmpty()) {
            val t = TextView(this)
            t.text = "Clipboard khali hai. Kuch copy karein, yahan dikhega."
            t.textSize = 13f
            t.setTextColor(theme.text)
            t.setPadding(dp(8), dp(16), dp(8), dp(8))
            clipList.addView(t)
            return
        }
        for (s in clips) {
            val t = TextView(this)
            t.text = s
            t.textSize = 14f
            t.maxLines = 2
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.setTextColor(theme.text)
            t.setPadding(dp(12), dp(9), dp(12), dp(9))
            t.background = pill(withAlpha(theme.keyFill, 200), 10)
            t.setOnClickListener {
                currentInputConnection?.commitText(s, 1)
                showPanel(null)
            }
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.topMargin = dp(5)
            clipList.addView(t, lp)
        }
    }

    private fun captureClip() {
        try {
            val cb = clipboard ?: return
            val d = cb.primaryClip ?: return
            if (d.itemCount == 0) return
            val t = d.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (t.isNotBlank()) KbPrefs.addClip(this, t)
        } catch (e: Throwable) {
        }
    }

    private fun buildThemePanel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.addView(panelHeader("Theme / Design", "More", { openSettings() }, false))
        val sv1 = HorizontalScrollView(this)
        sv1.isHorizontalScrollBarEnabled = false
        styleRow = LinearLayout(this)
        styleRow.orientation = LinearLayout.HORIZONTAL
        styleRow.setPadding(dp(6), dp(2), dp(6), dp(4))
        sv1.addView(styleRow, FrameLayout.LayoutParams(WRAP, WRAP))
        p.addView(sv1, LinearLayout.LayoutParams(MATCH, WRAP))

        val sv2 = HorizontalScrollView(this)
        sv2.isHorizontalScrollBarEnabled = false
        themeRow = LinearLayout(this)
        themeRow.orientation = LinearLayout.HORIZONTAL
        themeRow.setPadding(dp(6), dp(2), dp(6), dp(2))
        sv2.addView(themeRow, FrameLayout.LayoutParams(WRAP, MATCH))
        p.addView(sv2, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return p
    }

    private fun currentStyle(): String {
        val s = KbPrefs.style(this)
        return if (s.isEmpty()) theme.defStyle else s
    }

    private fun refreshThemePanel() {
        styleRow.removeAllViews()
        val styles = listOf("jewel" to "Jewel", "neon" to "Neon", "rainbow" to "Rainbow ✨", "glossy" to "Glossy", "png" to "My PNG")
        val cur = currentStyle()
        for (st in styles) {
            val tv = TextView(this)
            tv.text = st.second
            tv.textSize = 12f
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(14), dp(5), dp(14), dp(5))
            val sel = st.first == cur
            tv.setTextColor(if (sel) Color.WHITE else theme.text)
            tv.background = pill(if (sel) theme.accent else withAlpha(theme.keyFill, 200), 14)
            tv.setOnClickListener {
                if (st.first == "png" && !KbThemes.hasCustomKey(this)) {
                    Toast.makeText(this, "Pehle MYRA Settings > Keyboard me Key PNG chunein", Toast.LENGTH_LONG).show()
                    openSettings()
                } else {
                    KbPrefs.setStyle(this, st.first)
                    applyAll()
                    refreshThemePanel()
                }
            }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.rightMargin = dp(6)
            styleRow.addView(tv, lp)
        }

        themeRow.removeAllViews()
        for (t in KbThemes.all) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(dp(3), dp(3), dp(3), dp(3))
            val selected = t.id == theme.id
            cell.background = if (selected) {
                val g = GradientDrawable()
                g.cornerRadius = dp(10).toFloat()
                g.setStroke(dp(2), theme.accent)
                g.setColor(Color.TRANSPARENT)
                g
            } else null
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            val bmp = KbThemes.loadPreview(this, t)
            if (bmp != null) {
                iv.setImageBitmap(bmp)
            } else {
                val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.gradTop, t.gradBottom))
                g.cornerRadius = dp(8).toFloat()
                iv.setImageDrawable(g)
            }
            cell.addView(iv, LinearLayout.LayoutParams(dp(58), dp(86)))
            val nm = TextView(this)
            nm.text = t.name
            nm.textSize = 9f
            nm.maxLines = 1
            nm.ellipsize = android.text.TextUtils.TruncateAt.END
            nm.gravity = Gravity.CENTER
            nm.setTextColor(theme.text)
            cell.addView(nm, LinearLayout.LayoutParams(dp(62), WRAP))
            cell.setOnClickListener {
                if (t.id == "custom" && !KbThemes.hasCustomBg(this)) {
                    Toast.makeText(this, "Pehle Settings me apni photo/PNG chunein", Toast.LENGTH_LONG).show()
                    openSettings()
                } else {
                    KbPrefs.setThemeId(this, t.id)
                    applyAll()
                    refreshThemePanel()
                }
            }
            val lp = LinearLayout.LayoutParams(WRAP, MATCH)
            lp.rightMargin = dp(4)
            themeRow.addView(cell, lp)
        }
    }

    private fun showPanel(p: View?) {
        if (root == null) return
        kb.cancelAll()
        flushComposing()
        for (v in listOf(emojiPanel, clipPanel, themePanel, fontPanel)) {
            v.visibility = if (v === p) View.VISIBLE else View.GONE
        }
        kb.visibility = if (p == null) View.VISIBLE else View.INVISIBLE
        if (p === emojiPanel) pagerAdapter.notifyItemChanged(0)
        if (p === themePanel) refreshThemePanel()
        if (p === fontPanel) {
            fontAdapter.notifyDataSetChanged()
            val idx = KbFonts.visible().indexOfFirst { it.id == curFont.id }
            if (idx >= 0) fontList.scrollToPosition(idx)
        }
        if (p === clipPanel) refreshClips()
    }

    // ------------------------------------------------------------------ applying settings
    private fun applyAll() {
        curFont = KbFonts.byId(KbPrefs.fontStyle(this))
        theme = KbThemes.byId(KbPrefs.themeId(this))
        kb.kbTheme = theme
        kb.style = currentStyle()
        kb.opacity = KbPrefs.opacity(this)
        kb.rowH = dp(50) * KbPrefs.heightPct(this) / 100f
        applyFixedHeight()
        kb.showHints = KbPrefs.hints(this)
        kb.showPreview = KbPrefs.preview(this)
        kb.customKey = if (kb.style == "png") KbThemes.loadCustomKey(this) else null
        if (kb.style == "png" && kb.customKey == null) kb.style = "3d"
        sound = KbPrefs.sound(this)
        vib = KbPrefs.vibrate(this)
        autoCaps = KbPrefs.autoCaps(this)

        val ids = KbPrefs.langs(this).filter { id -> KbLayouts.all.any { it.id == id } }
        curLangs = if (ids.isEmpty()) listOf("en") else ids
        var cur = KbPrefs.curLang(this)
        if (!curLangs.contains(cur)) cur = curLangs[0]
        kb.lang = KbLayouts.byId(cur)
        kb.reload()
        syncFontToKeys()
        root?.setTheme(theme)
        styleChrome()
        appliedStamp = KbPrefs.stamp(this)
    }

    private fun styleChrome() {
        for (tv in toolButtons) {
            tv.setTextColor(theme.text)
            tv.background = pill(withAlpha(theme.keyFill, 175), 10)
        }
        for (tv in panelButtons) {
            tv.setTextColor(theme.text)
            if (tv.paddingLeft > 0) tv.background = pill(withAlpha(theme.keyFill, 200), 12)
        }
        if (curFont.id != "normal") {
            fontBtn?.background = pill(theme.accent, 10)
            fontBtn?.setTextColor(Color.WHITE)
        }
        for (tv in tabViews) tv.setTextColor(theme.text)
        for (tv in stripViews) tv.setTextColor(theme.text)
        fontAdapter.notifyDataSetChanged()
        selectTab(curPage)
        pagerAdapter.notifyDataSetChanged()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (root == null) return
        if (KbPrefs.stamp(this) != appliedStamp) applyAll()
        composing.setLength(0)
        suggestOn = true
        updateSuggestions()
        showPanel(null)
        kb.cancelAll()
        val inType = info?.inputType ?: 0
        val cls = inType and InputType.TYPE_MASK_CLASS
        kb.mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE)
            MyraKeyboardView.M_SYM1 else MyraKeyboardView.M_LETTERS
        kb.shift = 0
        val opts = info?.imeOptions ?: 0
        val noEnter = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        kb.enterKind = if (noEnter) "enter" else when (opts and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "go"
            EditorInfo.IME_ACTION_SEARCH -> "search"
            EditorInfo.IME_ACTION_SEND -> "send"
            EditorInfo.IME_ACTION_NEXT -> "next"
            EditorInfo.IME_ACTION_DONE -> "done"
            else -> "enter"
        }
        kb.reload()
        updateCaps()
    }

    private fun updateCaps() {
        if (!autoCaps || !kb.lang.latin || kb.mode != MyraKeyboardView.M_LETTERS || kb.shift == 2) return
        val ei: EditorInfo = currentInputEditorInfo ?: return
        if (ei.inputType == 0) return
        val caps = currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
        val want = if (caps != 0) 1 else 0
        if (kb.shift != want) {
            kb.shift = want
            kb.reload()
        }
    }

    // ------------------------------------------------------------------ MyraKeyboardView.Listener
    override fun onText(t: String) {
        val f = curFont
        if (f.id != "normal" && isWordText(t)) {
            // typing in a stylish font: the word is built live, e.g. ꧁Ali꧂ ; it ends on space / enter / symbol
            composing.append(t)
            currentInputConnection?.setComposingText(f.render(composing.toString()), 1)
            updateCaps()
        updateSuggestions()
            return
        }
        if (!isWordText(t)) learnLastWord()
        flushComposing()
        currentInputConnection?.commitText(t, 1)
        updateCaps()
        updateSuggestions()
    }

    override fun onDelete() {
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            val ic = currentInputConnection
            if (composing.isEmpty()) {
                ic?.setComposingText("", 1)
                ic?.finishComposingText()
            } else {
                ic?.setComposingText(curFont.render(composing.toString()), 1)
            }
            updateCaps()
        updateSuggestions()
            return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateCaps()
        updateSuggestions()
    }

    override fun onEnter() {
        learnLastWord()
        flushComposing()
        val ic = currentInputConnection ?: return
        val ei: EditorInfo? = currentInputEditorInfo
        val opts = ei?.imeOptions ?: 0
        val noEnter = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateCaps()
        updateSuggestions()
    }

    override fun onLangNext() {
        flushComposing()
        if (curLangs.size < 2) {
            onLangPicker()
            return
        }
        val i = curLangs.indexOf(kb.lang.id)
        val n = curLangs[(i + 1) % curLangs.size]
        KbPrefs.setCurLang(this, n)
        kb.lang = KbLayouts.byId(n)
        kb.shift = 0
        kb.mode = MyraKeyboardView.M_LETTERS
        kb.reload()
        updateCaps()
    }

    override fun onLangPicker() {
        try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        } catch (e: Throwable) {
        }
    }

    override fun onKeyFeedback() {
        if (vib) {
            try {
                val v = vibrator
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(14)
                    }
                }
            } catch (e: Throwable) {
            }
        }
        if (sound) {
            try {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
            } catch (e: Throwable) {
            }
        }
    }
}

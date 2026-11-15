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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EmojiVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

class EmojiAdapter(
    private val cellH: Int,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<EmojiVH>() {
    var items: List<String> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiVH {
        val tv = TextView(parent.context)
        tv.textSize = 24f
        tv.gravity = Gravity.CENTER
        tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellH)
        return EmojiVH(tv)
    }

    override fun onBindViewHolder(holder: EmojiVH, position: Int) {
        val e = items[position]
        holder.tv.text = e
        holder.tv.setOnClickListener { onPick(e) }
    }

    override fun getItemCount(): Int = items.size
}

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
    private lateinit var clipList: LinearLayout
    private lateinit var emojiAdapter: EmojiAdapter
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

        content = FrameLayout(this)
        content.clipChildren = false
        content.clipToPadding = false
        kb = MyraKeyboardView(this)
        kb.listener = this
        content.addView(kb, FrameLayout.LayoutParams(MATCH, WRAP))

        emojiPanel = buildEmojiPanel()
        clipPanel = buildClipPanel()
        themePanel = buildThemePanel()
        for (p in listOf(emojiPanel, clipPanel, themePanel)) {
            p.visibility = View.GONE
            content.addView(p, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        r.addView(content, LinearLayout.LayoutParams(MATCH, WRAP))
        root = r
        applyAll()
        return r
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
        p.addView(panelHeader("Emoji", null, null, true))
        val sv = HorizontalScrollView(this)
        sv.isHorizontalScrollBarEnabled = false
        emojiTabs = LinearLayout(this)
        emojiTabs.orientation = LinearLayout.HORIZONTAL
        sv.addView(emojiTabs, FrameLayout.LayoutParams(WRAP, WRAP))
        p.addView(sv, LinearLayout.LayoutParams(MATCH, dp(38)))

        emojiAdapter = EmojiAdapter(dp(42)) { e -> commitEmoji(e) }
        val rv = RecyclerView(this)
        rv.layoutManager = GridLayoutManager(this, 8)
        rv.adapter = emojiAdapter
        rv.clipToPadding = false
        p.addView(rv, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val tabs = ArrayList<Pair<String, () -> List<String>>>()
        tabs.add("🕒" to { KbPrefs.recents(this) })
        for (c in KbEmoji.cats) {
            val list = c.second
            tabs.add(c.first to { list })
        }
        for ((i, tab) in tabs.withIndex()) {
            val tv = TextView(this)
            tv.text = tab.first
            tv.textSize = 20f
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(12), 0, dp(12), 0)
            val getter = tab.second
            tv.setOnClickListener {
                emojiAdapter.items = getter()
                emojiAdapter.notifyDataSetChanged()
            }
            emojiTabs.addView(tv, LinearLayout.LayoutParams(WRAP, MATCH))
            if (i == 1) {
                emojiAdapter.items = getter()
            }
        }
        return p
    }

    private fun commitEmoji(e: String) {
        currentInputConnection?.commitText(e, 1)
        KbPrefs.addRecent(this, e)
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
        val styles = listOf("3d" to "3D", "glass" to "Glass", "flat" to "Flat", "bubble" to "Bubble", "png" to "My PNG")
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
        for (v in listOf(emojiPanel, clipPanel, themePanel)) {
            v.visibility = if (v === p) View.VISIBLE else View.GONE
        }
        kb.visibility = if (p == null) View.VISIBLE else View.INVISIBLE
        if (p === themePanel) refreshThemePanel()
        if (p === clipPanel) refreshClips()
    }

    // ------------------------------------------------------------------ applying settings
    private fun applyAll() {
        theme = KbThemes.byId(KbPrefs.themeId(this))
        kb.kbTheme = theme
        kb.style = currentStyle()
        kb.opacity = KbPrefs.opacity(this)
        kb.rowH = dp(50) * KbPrefs.heightPct(this) / 100f
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
        for (i in 0 until emojiTabs.childCount) {
            (emojiTabs.getChildAt(i) as? TextView)?.setTextColor(theme.text)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (root == null) return
        if (KbPrefs.stamp(this) != appliedStamp) applyAll()
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
        currentInputConnection?.commitText(t, 1)
        updateCaps()
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateCaps()
    }

    override fun onEnter() {
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
    }

    override fun onLangNext() {
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

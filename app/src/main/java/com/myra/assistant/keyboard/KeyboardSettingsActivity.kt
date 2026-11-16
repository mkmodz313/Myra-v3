package com.myra.assistant.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.myra.assistant.R
import java.io.File
import java.io.FileOutputStream

/** MYRA > Settings > MYRA Keyboard : enable the keyboard, pick design, PNG/background, languages. */
class KeyboardSettingsActivity : AppCompatActivity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var statusTv: TextView
    private lateinit var themeGrid: GridLayout
    private lateinit var styleRow: LinearLayout

    private val bgPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (saveImage(uri, KbThemes.CUSTOM_BG, 1600)) {
                KbPrefs.setThemeId(this, "custom")
                refreshThemes()
                Toast.makeText(this, "Background set ho gaya", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val keyPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (saveImage(uri, KbThemes.CUSTOM_KEY, 400)) {
                KbPrefs.setStyle(this, "png")
                refreshStyles()
                Toast.makeText(this, "Key PNG set ho gaya", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, sp: Float, color: Long, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = sp
        t.setTextColor(color.toInt())
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun card(title: String, sub: String?): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setBackgroundResource(R.drawable.bg_card_3d)
        c.setPadding(dp(16), dp(14), dp(16), dp(16))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.bottomMargin = dp(14)
        c.layoutParams = lp
        c.addView(text(title, 16f, 0xFFFFA6EE, true))
        if (sub != null) {
            val s = text(sub, 12f, 0xFFCFA6D6)
            s.setPadding(0, dp(2), 0, dp(6))
            c.addView(s)
        }
        return c
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.textSize = 14f
        b.setBackgroundResource(R.drawable.bg_btn_3d)
        b.stateListAnimator = null
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(MATCH, dp(48))
        lp.topMargin = dp(8)
        b.layoutParams = lp
        return b
    }

    private fun pill(color: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(r).toFloat()
        g.setColor(color)
        return g
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFF08000F.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(28), dp(16), dp(32))
        sv.addView(col, FrameLayoutParams())

        col.addView(text("⌨️  MYRA Keyboard", 22f, 0xFFFFFFFF, true))
        val sub = text("Asli keyboard: WhatsApp, YouTube, Chrome - har app me type karein", 12f, 0xFFCFA6D6)
        sub.setPadding(0, dp(2), 0, dp(14))
        col.addView(sub)

        // ---------- 1. setup ----------
        val setup = card("1) Keyboard on karein", "Pehle enable, phir select - bas 2 step")
        statusTv = text("", 13f, 0xFFFFD6F8)
        statusTv.setPadding(0, dp(4), 0, dp(2))
        setup.addView(statusTv)
        setup.addView(button("Step 1: MYRA Keyboard enable karein") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        setup.addView(button("Step 2: MYRA Keyboard select karein") {
            try {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            } catch (e: Throwable) {
            }
        })
        val test = EditText(this)
        test.hint = "Yahan tap karke keyboard test karein..."
        test.setHintTextColor(0xFF9E7FA8.toInt())
        test.setTextColor(Color.WHITE)
        test.setBackgroundResource(R.drawable.bg_input_field)
        test.setPadding(dp(14), dp(12), dp(14), dp(12))
        val tlp = LinearLayout.LayoutParams(MATCH, WRAP)
        tlp.topMargin = dp(12)
        setup.addView(test, tlp)
        col.addView(setup)

        // ---------- 2. themes ----------
        val themes = card("2) Theme / Background", "Aapke 12 designs + 3D looks. Tap karke apply karein")
        themeGrid = GridLayout(this)
        themeGrid.columnCount = 3
        themes.addView(themeGrid, LinearLayout.LayoutParams(MATCH, WRAP))
        themes.addView(button("🖼️  Apni photo / PNG background chunein") { bgPicker.launch("image/*") })
        col.addView(themes)

        // ---------- 3. key style ----------
        val styles = card("3) Key design (3D / PNG)", "Keys ka look badlein, ya apna PNG key lagayein")
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        styleRow = LinearLayout(this)
        styleRow.orientation = LinearLayout.HORIZONTAL
        hs.addView(styleRow, WRAPLP())
        styles.addView(hs, LinearLayout.LayoutParams(MATCH, WRAP))
        styles.addView(button("🔑  Key PNG chunein (transparent PNG best)") { keyPicker.launch("image/*") })
        styles.addView(slider("Key opacity", 20, 100, KbPrefs.opacity(this)) { KbPrefs.setOpacity(this, it) })
        styles.addView(slider("Keyboard height %", 70, 140, KbPrefs.heightPct(this)) { KbPrefs.setHeightPct(this, it) })
        col.addView(styles)

        // ---------- 4. options ----------
        val opts = card("4) Options", null)
        opts.addView(toggle("Key vibration", KbPrefs.vibrate(this)) { KbPrefs.setVibrate(this, it) })
        opts.addView(toggle("Key sound", KbPrefs.sound(this)) { KbPrefs.setSound(this, it) })
        opts.addView(slider("Sound volume", 10, 100, KbPrefs.soundVol(this)) { KbPrefs.setSoundVol(this, it) })
        val soundTitle = TextView(this)
        soundTitle.text = "Key sound chunein (tap karke suniye, jo pasand ho wahi rakhein)"
        soundTitle.setTextColor(Color.WHITE)
        soundTitle.textSize = 13f
        soundTitle.setPadding(0, dp(10), 0, dp(4))
        opts.addView(soundTitle)
        val soundChips = ArrayList<TextView>()
        fun paintSoundChips() {
            val cur = KbPrefs.soundId(this)
            for ((i, tv) in soundChips.withIndex()) {
                val g = GradientDrawable()
                g.cornerRadius = dp(12).toFloat()
                g.setColor(if (i == cur) 0xFFFF4DA6.toInt() else 0x33FFFFFF)
                g.setStroke(dp(1), if (i == cur) Color.WHITE else 0x66FF4DA6)
                tv.background = g
            }
        }
        for ((i, nm) in KbSound.names.withIndex()) {
            val tv = TextView(this)
            tv.text = "🔊  " + "%02d".format(i + 1) + "  " + nm
            tv.setTextColor(Color.WHITE)
            tv.textSize = 14f
            tv.setPadding(dp(14), dp(11), dp(14), dp(11))
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.topMargin = dp(5)
            tv.layoutParams = lp
            tv.setOnClickListener {
                KbPrefs.setSoundId(this, i)
                paintSoundChips()
                KbSound.play(this, i, KbPrefs.soundVol(this))
            }
            soundChips.add(tv)
            opts.addView(tv)
        }
        paintSoundChips()
        opts.addView(toggle("Auto capital (English etc.)", KbPrefs.autoCaps(this)) { KbPrefs.setAutoCaps(this, it) })
        opts.addView(toggle("Number hints (1-0) on top row", KbPrefs.hints(this)) { KbPrefs.setHints(this, it) })
        opts.addView(toggle("Key preview popup", KbPrefs.preview(this)) { KbPrefs.setPreview(this, it) })
        col.addView(opts)

        // ---------- 5. languages ----------
        val langs = card("5) Languages", "Jo chahiye tick karein. Keyboard par 🌐 dabakar badlein")
        val g = GridLayout(this)
        g.columnCount = 2
        val enabled = KbPrefs.langs(this).toMutableList()
        for (l in KbLayouts.all) {
            val cb = CheckBox(this)
            cb.text = l.name
            cb.setTextColor(Color.WHITE)
            cb.textSize = 13f
            cb.buttonTintList = ColorStateList.valueOf(0xFFFF4DA6.toInt())
            cb.isChecked = enabled.contains(l.id)
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    if (!enabled.contains(l.id)) enabled.add(l.id)
                } else {
                    enabled.remove(l.id)
                }
                if (enabled.isEmpty()) {
                    enabled.add("en")
                    Toast.makeText(this, "Kam se kam ek language zaroori hai (English on)", Toast.LENGTH_SHORT).show()
                }
                KbPrefs.setLangs(this, enabled)
            }
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = WRAP
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            g.addView(cb, lp)
        }
        langs.addView(g, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(langs)

        // ---------- reset ----------
        col.addView(button("♻️  Keyboard settings reset") {
            KbPrefs.clearAll(this)
            File(filesDir, KbThemes.CUSTOM_BG).delete()
            File(filesDir, KbThemes.CUSTOM_KEY).delete()
            recreate()
        })

        setContentView(sv)
        refreshThemes()
        refreshStyles()
    }

    @Suppress("FunctionName")
    private fun FrameLayoutParams(): android.widget.FrameLayout.LayoutParams =
        android.widget.FrameLayout.LayoutParams(MATCH, WRAP)

    @Suppress("FunctionName")
    private fun WRAPLP(): android.widget.FrameLayout.LayoutParams =
        android.widget.FrameLayout.LayoutParams(WRAP, WRAP)

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = cur.startsWith("$packageName/")
        statusTv.text = when {
            selected -> "✅ MYRA Keyboard active hai - ab har app me chalega"
            enabled -> "🟡 Enable ho gaya. Ab Step 2 se MYRA Keyboard select karein"
            else -> "🔴 Abhi enable nahi hai. Step 1 se shuru karein"
        }
    }

    // ------------------------------------------------------------------ themes grid
    private fun refreshThemes() {
        themeGrid.removeAllViews()
        val cur = KbPrefs.themeId(this)
        val cellW = (resources.displayMetrics.widthPixels - dp(32 + 32 + 24)) / 3
        for (t in KbThemes.all) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(dp(3), dp(3), dp(3), dp(3))
            if (t.id == cur) {
                val sel = GradientDrawable()
                sel.cornerRadius = dp(12).toFloat()
                sel.setStroke(dp(2), 0xFFFF4DA6.toInt())
                sel.setColor(Color.TRANSPARENT)
                cell.background = sel
            }
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            val bmp = KbThemes.loadPreview(this, t)
            if (bmp != null) {
                iv.setImageBitmap(bmp)
            } else {
                val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.gradTop, t.gradBottom))
                gd.cornerRadius = dp(8).toFloat()
                iv.setImageDrawable(gd)
            }
            cell.addView(iv, LinearLayout.LayoutParams(cellW - dp(6), (cellW * 1.5f).toInt()))
            val nm = text(t.name, 10f, 0xFFFFFFFF)
            nm.gravity = Gravity.CENTER
            nm.maxLines = 2
            cell.addView(nm, LinearLayout.LayoutParams(cellW - dp(6), WRAP))
            cell.setOnClickListener {
                if (t.id == "custom" && !KbThemes.hasCustomBg(this)) {
                    bgPicker.launch("image/*")
                } else {
                    KbPrefs.setThemeId(this, t.id)
                    refreshThemes()
                }
            }
            val lp = GridLayout.LayoutParams()
            lp.width = cellW
            lp.height = WRAP
            lp.setMargins(dp(2), dp(4), dp(2), dp(4))
            themeGrid.addView(cell, lp)
        }
    }

    private fun refreshStyles() {
        styleRow.removeAllViews()
        val st = KbPrefs.style(this)
        val cur = if (st.isEmpty()) KbThemes.byId(KbPrefs.themeId(this)).defStyle else st
        val list = listOf("jewel" to "Jewel", "neon" to "Neon", "rainbow" to "Rainbow ✨", "glossy" to "Glossy", "png" to "My PNG")
        for (s in list) {
            val tv = text(s.second, 13f, 0xFFFFFFFF, true)
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(16), dp(8), dp(16), dp(8))
            tv.background = pill(if (s.first == cur) 0xFFFF2DAA.toInt() else 0x33FFFFFF, 16)
            tv.setOnClickListener {
                if (s.first == "png" && !KbThemes.hasCustomKey(this)) {
                    keyPicker.launch("image/*")
                } else {
                    KbPrefs.setStyle(this, s.first)
                    refreshStyles()
                }
            }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.rightMargin = dp(8)
            lp.topMargin = dp(4)
            styleRow.addView(tv, lp)
        }
    }

    // ------------------------------------------------------------------ small widgets
    private fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = SwitchCompat(this)
        sw.text = label
        sw.setTextColor(Color.WHITE)
        sw.textSize = 14f
        sw.isChecked = initial
        sw.setPadding(0, dp(8), 0, dp(8))
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        return sw
    }

    private fun slider(label: String, min: Int, max: Int, initial: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(10), 0, 0)
        val tv = text("$label: $initial", 13f, 0xFFFFD6F8)
        box.addView(tv)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = (initial - min).coerceIn(0, max - min)
        sb.progressTintList = ColorStateList.valueOf(0xFFFF4DA6.toInt())
        sb.thumbTintList = ColorStateList.valueOf(0xFFFF4DA6.toInt())
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tv.text = "$label: ${p + min}"
                if (fromUser) onChange(p + min)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(sb, LinearLayout.LayoutParams(MATCH, WRAP))
        return box
    }

    // ------------------------------------------------------------------ image import
    private fun saveImage(uri: Uri, fileName: String, maxSide: Int): Boolean {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
            val o = BitmapFactory.Options()
            o.inSampleSize = sample
            val bmp: Bitmap? = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            if (bmp == null) {
                Toast.makeText(this, "Image read nahi ho payi", Toast.LENGTH_SHORT).show()
                false
            } else {
                var out: Bitmap = bmp
                val big = maxOf(bmp.width, bmp.height)
                if (big > maxSide) {
                    val f = maxSide.toFloat() / big
                    out = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
                }
                FileOutputStream(File(filesDir, fileName)).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "Image save nahi ho payi", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

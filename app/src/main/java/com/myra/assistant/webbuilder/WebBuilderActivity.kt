package com.myra.assistant.webbuilder

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.myra.assistant.R
import com.myra.assistant.utils.ApiKeys
import com.myra.assistant.utils.Keys
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * MYRA Web Builder: paste a prompt -> AI builds a complete modern website (index.html + style.css + script.js [+ more pages]),
 * live preview inside the app, change requests, and a ZIP download.
 * The API key comes from assets/keys.txt (WEB_BUILDER_KEY=❤️ placeholder, filled in with MT Manager) or from the key field below.
 */
class WebBuilderActivity : AppCompatActivity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ui = Handler(Looper.getMainLooper())

    private val files = LinkedHashMap<String, String>()
    private var busy = false
    private var startedAt = 0L

    private lateinit var promptField: EditText
    private lateinit var keyField: EditText
    private lateinit var modelField: EditText
    private lateinit var changeField: EditText
    private lateinit var status: TextView
    private lateinit var web: WebView
    private lateinit var codeView: TextView
    private lateinit var codeScroll: ScrollView
    private lateinit var tabPreview: TextView
    private lateinit var tabCode: TextView
    private lateinit var genBtn: Button

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(330, TimeUnit.SECONDS)
        .build()

    private val saveZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) writeZip(uri)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences("webbuilder", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ UI
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this)
        root.setBackgroundColor(0xFF120A20.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(36), dp(16), dp(30))
        root.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        setContentView(root)

        col.addView(text("🌐  MYRA Web Builder", 22f, 0xFFFFA6EE.toInt(), true))
        col.addView(text("Prompt paste karein → AI poori modern website banayegi → yahin preview → ZIP download.", 12f, 0xFFCFA6D6.toInt(), false))

        col.addView(label("1) Website ka prompt"))
        promptField = field("Jaise: Ek premium restaurant website, dark theme, menu, gallery, booking form, Urdu + English...", 6, true)
        promptField.setText(prefs().getString("prompt", ""))
        col.addView(promptField)

        col.addView(label("2) AI model"))
        modelField = field("gemini-2.5-flash", 1, false)
        modelField.setText(prefs().getString("model", "gemini-2.5-flash"))
        col.addView(modelField)
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.addView(chip("⚡ Flash (fast)") { modelField.setText("gemini-2.5-flash") })
        chips.addView(chip("💎 Pro (best quality)") { modelField.setText("gemini-2.5-pro") })
        col.addView(chips)

        col.addView(label("3) API key  (yahan paste karein - sabse aasan)"))
        keyField = field("API key yahan paste karein", 1, false)
        keyField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyField.setText(prefs().getString("key", ""))
        col.addView(keyField)

        genBtn = button("⚡  Website banao") { startBuild(false) }
        col.addView(genBtn)
        status = text("", 12f, 0xFFFFE08A.toInt(), false)
        status.setPadding(0, dp(8), 0, dp(4))
        col.addView(status)

        // preview / code tabs
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabPreview = tab("👁  Preview") { showTab(true) }
        tabCode = tab("</>  Code") { showTab(false) }
        tabs.addView(tabPreview, LinearLayout.LayoutParams(0, dp(42), 1f))
        tabs.addView(tabCode, LinearLayout.LayoutParams(0, dp(42), 1f))
        col.addView(tabs)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.setBackgroundColor(Color.WHITE)
        web.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                if (u.startsWith(BASE)) {
                    val path = u.removePrefix(BASE).substringBefore('#').substringBefore('?')
                    if (path.isEmpty()) return false
                    val key = files.keys.firstOrNull { it.equals(path, true) }
                    if (key != null) {
                        view.loadDataWithBaseURL(BASE, inlined(key), "text/html", "utf-8", null)
                        return true
                    }
                    return false
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                } catch (e: Throwable) {
                }
                return true
            }
        }
        col.addView(web, LinearLayout.LayoutParams(MATCH, dp(520)))

        codeView = TextView(this)
        codeView.typeface = Typeface.MONOSPACE
        codeView.textSize = 11f
        codeView.setTextColor(0xFFE8E0F0.toInt())
        codeView.setTextIsSelectable(true)
        codeView.setPadding(dp(10), dp(10), dp(10), dp(10))
        codeScroll = ScrollView(this)
        codeScroll.setBackgroundColor(0xFF1C1230.toInt())
        codeScroll.addView(codeView)
        codeScroll.visibility = View.GONE
        col.addView(codeScroll, LinearLayout.LayoutParams(MATCH, dp(520)))

        col.addView(label("✏️  Badlav (change) likhein aur Update dabayein"))
        changeField = field("Jaise: hero section me video-style gradient lagao, colors gold karo, pricing section add karo", 3, true)
        col.addView(changeField)
        col.addView(button("🔄  Update karo") { startBuild(true) })
        col.addView(button("💾  ZIP save karo") {
            if (files.isEmpty()) toast("Pehle website banayein") else saveZip.launch("website_${System.currentTimeMillis() / 1000}.zip")
        })
        col.addView(button("📋  Code copy karo") {
            if (files.isEmpty()) toast("Pehle website banayein") else {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("website", allCode()))
                toast("Code copy ho gaya")
            }
        })
        showTab(true)
        web.loadDataWithBaseURL(BASE, placeholderPage(), "text/html", "utf-8", null)
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (web.visibility == View.VISIBLE && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    // ------------------------------------------------------------------ small view helpers
    private fun text(t: String, sp: Float, color: Int, bold: Boolean): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = sp
        v.setTextColor(color)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun label(t: String): TextView {
        val v = text(t, 13f, 0xFFFFFFFF.toInt(), true)
        v.setPadding(0, dp(14), 0, dp(4))
        return v
    }

    private fun field(hint: String, lines: Int, multi: Boolean): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(0x88FFFFFF.toInt())
        e.setTextColor(Color.WHITE)
        e.textSize = 14f
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        if (multi) {
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            e.minLines = lines
            e.gravity = Gravity.TOP
        } else {
            e.setSingleLine(true)
        }
        val g = GradientDrawable()
        g.cornerRadius = dp(12).toFloat()
        g.setColor(0x33FFFFFF)
        g.setStroke(dp(1), 0x66FF4DA6)
        e.background = g
        return e
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
        val lp = LinearLayout.LayoutParams(MATCH, dp(50))
        lp.topMargin = dp(10)
        b.layoutParams = lp
        return b
    }

    private fun chip(label: String, onClick: () -> Unit): TextView {
        val v = text(label, 12f, Color.WHITE, true)
        v.setPadding(dp(12), dp(8), dp(12), dp(8))
        val g = GradientDrawable()
        g.cornerRadius = dp(14).toFloat()
        g.setColor(0x44FF4DA6)
        g.setStroke(dp(1), 0x88FF4DA6.toInt())
        v.background = g
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.setMargins(0, dp(6), dp(8), 0)
        v.layoutParams = lp
        v.setOnClickListener { onClick() }
        return v
    }

    private fun tab(label: String, onClick: () -> Unit): TextView {
        val v = text(label, 13f, Color.WHITE, true)
        v.gravity = Gravity.CENTER
        v.setOnClickListener { onClick() }
        return v
    }

    private fun showTab(preview: Boolean) {
        web.visibility = if (preview) View.VISIBLE else View.GONE
        codeScroll.visibility = if (preview) View.GONE else View.VISIBLE
        fun paint(t: TextView, on: Boolean) {
            val g = GradientDrawable()
            g.cornerRadius = dp(10).toFloat()
            g.setColor(if (on) 0xFFFF4DA6.toInt() else 0x33FFFFFF)
            t.background = g
        }
        paint(tabPreview, preview)
        paint(tabCode, !preview)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun setStatus(s: String) {
        status.text = s
    }

    // ------------------------------------------------------------------ build
    private fun currentKey(): String {
        val typed = keyField.text.toString().trim()
        if (typed.isNotEmpty() && !typed.contains('❤')) return typed
        val k = Keys.get(this, "WEB_BUILDER_KEY")
        if (k.isNotEmpty()) return k
        return ApiKeys.gemini(this)
    }

    private fun startBuild(isChange: Boolean) {
        if (busy) return
        val p = promptField.text.toString().trim()
        val change = changeField.text.toString().trim()
        if (!isChange && p.isEmpty()) {
            toast("Pehle prompt likhein")
            return
        }
        if (isChange && (files.isEmpty() || change.isEmpty())) {
            toast("Pehle website banayein aur badlav likhein")
            return
        }
        val key = currentKey()
        if (key.isEmpty()) {
            setStatus("🔑 API key nahi mili. Sabse aasan: upar \"3) API key\" wali field me apni key paste karein aur phir \"Website banao\" dabayein (key yaad rahegi).")
            return
        }
        val model = modelField.text.toString().trim().ifEmpty { "gemini-2.5-flash" }
        prefs().edit().putString("prompt", p).putString("model", model).putString("key", keyField.text.toString().trim()).apply()

        val userText = if (isChange) {
            "CURRENT WEBSITE FILES:\n" + allCode() + "\n\nORIGINAL REQUEST:\n" + p +
                "\n\nAPPLY THIS CHANGE and return ALL files again in full (every file, complete, no shortcuts):\n" + change
        } else {
            "Build this website:\n" + p
        }
        busy = true
        genBtn.isEnabled = false
        startedAt = System.currentTimeMillis()
        tick()
        Thread {
            try {
                val out = callGemini(key, model, userText)
                val parsed = parseFiles(out)
                runOnUiThread {
                    files.clear()
                    files.putAll(parsed)
                    showResult()
                }
            } catch (e: Throwable) {
                val msg = e.message ?: e.toString()
                runOnUiThread { setStatus("❌ $msg") }
            } finally {
                runOnUiThread {
                    busy = false
                    genBtn.isEnabled = true
                }
            }
        }.start()
    }

    private fun tick() {
        if (!busy) return
        val s = (System.currentTimeMillis() - startedAt) / 1000
        setStatus("⏳ Website ban rahi hai... ${s}s  (bari website me 1-3 minute lag sakte hain)")
        ui.postDelayed({ tick() }, 1000)
    }

    private fun callGemini(key: String, model: String, userText: String): String {
        val body = JSONObject()
        body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
        body.put(
            "contents",
            JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText))))
        )
        body.put("generationConfig", JSONObject().put("temperature", 0.85).put("maxOutputTokens", 60000))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                var m = raw.take(300)
                try {
                    m = JSONObject(raw).getJSONObject("error").optString("message", m)
                } catch (e: Throwable) {
                }
                throw RuntimeException("API error ${resp.code}: $m")
            }
            val j = JSONObject(raw)
            val cands = j.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                val fb = j.optJSONObject("promptFeedback")?.optString("blockReason", "") ?: ""
                throw RuntimeException("AI ne jawab nahi diya" + if (fb.isNotEmpty()) " ($fb)" else "")
            }
            val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: throw RuntimeException("AI ka jawab khali tha, dobara try karein")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
            if (sb.isBlank()) throw RuntimeException("AI ka jawab khali tha, dobara try karein")
            return sb.toString()
        }
    }

    private fun parseFiles(out: String): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        val re = Regex("===FILE:\\s*(.+?)\\s*===\\s*\\n(.*?)\\n?===END===", RegexOption.DOT_MATCHES_ALL)
        for (m in re.findAll(out)) {
            val name = m.groupValues[1].trim().trimStart('/').replace("\\", "/")
            var body = m.groupValues[2]
            body = body.removePrefix("```html").removePrefix("```css").removePrefix("```javascript").removePrefix("```js").removePrefix("```")
            body = body.trimEnd().removeSuffix("```").trim('\n')
            if (name.isNotEmpty() && body.isNotBlank()) map[name] = body
        }
        if (map.isEmpty()) {
            val fence = Regex("```html\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(out)
            val html = fence?.groupValues?.get(1) ?: out
            if (html.contains("<html", true) || html.contains("<!doctype", true)) map["index.html"] = html.trim()
        }
        if (map.isEmpty()) throw RuntimeException("Website ka code samajh nahi aaya, dobara try karein")
        return map
    }

    private fun showResult() {
        setStatus("✅ Tayyar! ${files.size} file(s): " + files.keys.joinToString(", "))
        val first = files.keys.firstOrNull { it.equals("index.html", true) } ?: files.keys.firstOrNull { it.endsWith(".html", true) }
        if (first != null) web.loadDataWithBaseURL(BASE, inlined(first), "text/html", "utf-8", null)
        codeView.text = allCode()
        showTab(true)
    }

    private fun allCode(): String {
        val sb = StringBuilder()
        for ((n, c) in files) sb.append("===FILE: ").append(n).append("===\n").append(c).append("\n===END===\n\n")
        return sb.toString()
    }

    /** Puts style.css / script.js into the page so the preview works from one string. */
    private fun inlined(page: String): String {
        var html = files[page] ?: return placeholderPage()
        for ((name, content) in files) {
            val q = Regex.escape(name)
            if (name.endsWith(".css", true)) {
                html = Regex("<link[^>]*href=[\"']?(\\./)?$q[\"']?[^>]*>", RegexOption.IGNORE_CASE)
                    .replace(html) { "<style>\n$content\n</style>" }
            } else if (name.endsWith(".js", true)) {
                html = Regex("<script[^>]*src=[\"']?(\\./)?$q[\"']?[^>]*>\\s*</script>", RegexOption.IGNORE_CASE)
                    .replace(html) { "<script>\n$content\n</script>" }
            }
        }
        return html
    }

    private fun placeholderPage(): String =
        "<html><body style='margin:0;background:#120A20;color:#FFA6EE;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'>" +
            "<div><div style='font-size:46px'>🌐</div><div style='font-size:18px;margin-top:8px'>Yahan website ka preview aayega</div>" +
            "<div style='font-size:12px;color:#CFA6D6;margin-top:6px'>Upar prompt likh kar \"Website banao\" dabayein</div></div></body></html>"

    private fun writeZip(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                ZipOutputStream(os).use { z ->
                    for ((n, c) in files) {
                        z.putNextEntry(ZipEntry(n))
                        z.write(c.toByteArray(Charsets.UTF_8))
                        z.closeEntry()
                    }
                }
            }
            toast("ZIP save ho gayi ✅")
        } catch (e: Throwable) {
            toast("ZIP save nahi hui: ${e.message}")
        }
    }

    companion object {
        private const val BASE = "https://preview.local/"

        private val SYSTEM_PROMPT = """
You are an elite web designer and senior front-end engineer. You turn a short request into a COMPLETE, production-quality, ultra-modern website.

DESIGN RULES
- Look like a premium 2025/2026 agency site: bold typography, strong visual hierarchy, generous spacing, consistent 8px grid, rounded cards, subtle glassmorphism or soft shadows, gradients, smooth micro-animations (CSS transitions, scroll-reveal with IntersectionObserver), hover effects, sticky navbar, tasteful dark/light balance that fits the topic.
- Mobile-first and fully responsive (phone, tablet, desktop). Use CSS variables for colours, clamp() for fluid type, CSS grid/flexbox. No horizontal scrolling.
- Choose a distinctive colour palette and 1-2 Google Fonts (via <link>) that match the brand. Never use plain default browser styling.
- Real, convincing content written for the topic (no lorem ipsum). Write the content in the same language as the user's request (Urdu/Hindi/Arabic => correct RTL with dir="rtl" and a matching font).
- Typical sections when relevant: navbar, hero with strong call-to-action, features/services, about, gallery or portfolio, testimonials, pricing, FAQ (accordion), contact form, footer. Adapt to the request; do not pad with irrelevant sections.
- Icons: inline SVG or emoji. Images: use CSS gradients/SVG illustrations, or https://picsum.photos/seed/<word>/800/600 placeholders. Never leave broken image links.
- Working interactivity in vanilla JavaScript: mobile menu, smooth scroll, accordion, tabs, counters, form validation with a friendly success message, light/dark toggle when it fits.
- Accessibility: semantic HTML5, alt text, aria labels, good contrast, focus styles, prefers-reduced-motion respected.
- No frameworks or build steps. No external JS libraries unless truly needed (then a CDN link). Code must run by simply opening index.html.

OUTPUT FORMAT (STRICT)
Return ONLY file blocks, nothing before, between or after them (no explanations, no markdown fences):
===FILE: index.html===
(full file)
===END===
===FILE: style.css===
(full file)
===END===
===FILE: script.js===
(full file)
===END===
- index.html must link style.css and script.js with relative paths.
- If the user asks for several pages, add more .html files (about.html, contact.html ...) that share style.css/script.js and link to each other with relative links.
- When asked to change an existing site, return EVERY file again, complete and updated.
""".trimIndent()
    }
}

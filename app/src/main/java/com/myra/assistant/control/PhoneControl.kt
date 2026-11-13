package com.myra.assistant.control

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.myra.assistant.apps.AppLauncher
import com.myra.assistant.apps.InstalledAppsManager
import com.myra.assistant.ai.DynamicDecisionEngine
import com.myra.assistant.ai.IntentAnalyzer
import com.myra.assistant.websocket.GeminiWebSocketClient
import org.json.JSONObject
import com.myra.assistant.data.ChatRepository
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Natural-language phone control for typed AND spoken commands.
 *
 * Everything goes through official Android Intents / wa.me deep links. WhatsApp messages are NEVER sent
 * without an explicit "yes" from the user, and the Accessibility Service is only used (after that "yes")
 * to tap WhatsApp's own Send button. Nothing here bypasses permissions or app security.
 *
 * Returns handled=false for anything it does not recognise so the existing command pipeline keeps working.
 */
object PhoneControl {

    private const val TAG = "PHONE_CTRL"
    private const val PENDING_TTL_MS = 120_000L

    data class Outcome(val handled: Boolean, val reply: String = "", val success: Boolean = true)

    private val NOT_HANDLED = Outcome(false)

    private data class Pending(val number: String, val text: String, val createdAt: Long, val inChat: Boolean = false)

    private enum class Target(val label: String) {
        WHATSAPP("WhatsApp"), YOUTUBE("YouTube"), GOOGLE("Google"), CHROME("Chrome"),
        FACEBOOK("Facebook"), INSTAGRAM("Instagram"), GALLERY("Gallery"), CAMERA("Camera"),
        SETTINGS("Settings"), CALCULATOR("Calculator")
    }

    private val ALIASES = mapOf(
        "whatsapp" to Target.WHATSAPP, "whats app" to Target.WHATSAPP, "watsapp" to Target.WHATSAPP,
        "youtube" to Target.YOUTUBE, "you tube" to Target.YOUTUBE,
        "google" to Target.GOOGLE,
        "chrome" to Target.CHROME, "google chrome" to Target.CHROME,
        "facebook" to Target.FACEBOOK, "fb" to Target.FACEBOOK,
        "instagram" to Target.INSTAGRAM, "insta" to Target.INSTAGRAM,
        "gallery" to Target.GALLERY, "photos" to Target.GALLERY,
        "camera" to Target.CAMERA,
        "settings" to Target.SETTINGS, "setting" to Target.SETTINGS,
        "calculator" to Target.CALCULATOR, "calc" to Target.CALCULATOR
    )

    private val PACKAGES = mapOf(
        Target.WHATSAPP to listOf("com.whatsapp", "com.whatsapp.w4b"),
        Target.YOUTUBE to listOf("com.google.android.youtube"),
        Target.GOOGLE to listOf("com.google.android.googlequicksearchbox"),
        Target.CHROME to listOf("com.android.chrome"),
        Target.FACEBOOK to listOf("com.facebook.katana"),
        Target.INSTAGRAM to listOf("com.instagram.android")
    )

    private val YES_WORDS = setOf("yes", "yeah", "yep", "ok", "okay", "confirm", "confirmed", "send", "haan", "han", "ji", "bhej", "bhejo", "kardo", "sure")
    private val NO_WORDS = setOf("no", "nope", "cancel", "nahi", "nahin", "stop", "ruko", "dont", "don", "mat")

    private val COMPOSE_A = Regex(
        "^\\s*(?:please\\s+)?(?:send|write|type|compose)\\s+(?:this|a|the|my)?\\s*(?:message|msg|text)\\s*(?:saying|that says|is)?\\s*[:,\\-]?\\s*(.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val COMPOSE_B = Regex(
        "^\\s*(?:message|msg)\\s+(?:bhejo|bhej\\s*do|send\\s*karo|likho)\\s*[:,\\-]?\\s*(.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val PHONE_RX = Regex("(\\+?\\d[\\d\\s\\-().]{6,}\\d)")
    private val OPEN_RX1 = Regex(
        "^(?:please |myra |hey myra )*(?:open|launch|start|kholo|khol|chalao)\\s+(?:the )?(.+?)(?:\\s+(?:app|application|karo|kardo|kar do|do|please))*$"
    )
    private val OPEN_RX2 = Regex(
        "^(.+?)\\s+(?:app )?(?:kholo|khol do|khol|open karo|open kar do|open|chalao)(?:\\s+(?:karo|kardo|please|do))*$"
    )
    private val SEARCH_ON = Regex("^(?:please )?search\\s+(?:for )?(.+?)\\s+(?:on|in)\\s+(google|youtube)$")
    private val SEARCH_PLAIN = Regex("^(?:please )?(?:search|google)\\s+(?:for )?(.+)$")
    private val SEARCH_HINGLISH = Regex("^(.+?)\\s+(?:ko )?(google|youtube)\\s+(?:par|pe)\\s+search(?:\\s+karo)?$")
    private val YT_ON = Regex("^(?:please )?(?:play|search)\\s+(.+?)\\s+(?:on|in)\\s+youtube$")
    private val YT_HINGLISH1 = Regex("^youtube\\s+(?:par|pe|me|mein|on)\\s+(.+?)\\s+(?:play|chalao|chala do|laga do|lagao|sunao|dikhao)(?:\\s+(?:karo|kardo|kar do))?$")
    private val YT_HINGLISH2 = Regex("^(.+?)\\s+youtube\\s+(?:par|pe|me|mein)\\s+(?:play|chalao|chala do|laga do|lagao|sunao|dikhao)(?:\\s+(?:karo|kardo|kar do))?$")
    private val ANSWER_RX = Regex("(call|phone|कॉल|फोन)\\s*(utha|uthao|utha do|uthalo|utha lo)|(pick up|answer|receive|utha)\\s*(the |my )?(call|phone)|call\\s*(pick|answer|receive)")
    private val TALK_RX = Regex("(phone|call)\\s*(pe|par|per)\\s*(tum |aap )?baat|tum baat karo|aap baat karo|talk to (him|her|them|the caller)|you talk")
    private val DOMAIN_RX = Regex(
        "((?:https?://)?(?:www\\.)?[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.(?:com|org|net|pk|in|io|co|edu|gov|app|dev|ai|info|tv|me|uk)(?:/\\S*)?)"
    )

    /** What the user is saying right now (live voice transcript) - used to double-check confirmations. */
    @Volatile var lastHeard: String = ""

    private var pending: Pending? = null
    private var activeNumber: String? = null
    private var lastActionKey = ""
    private var lastActionAt = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> Logger.e(TAG, "async error: ${e.message}") })
    private val main = Handler(Looper.getMainLooper())

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    @Synchronized
    fun handle(context: Context, raw: String): Outcome {
        val ctx = context.applicationContext
        val text = raw.trim()
        if (text.isEmpty()) return NOT_HANDLED
        val lower = normalize(text)

        // 1) Compose a WhatsApp message (needs a chat opened by number first; a new compose replaces an older pending one)
        val composed = COMPOSE_A.find(text)?.groupValues?.get(1) ?: COMPOSE_B.find(text)?.groupValues?.get(1)
        if (!composed.isNullOrBlank()) return prepareMessage(composed.trim())

        // 2) Confirmation / cancellation of a prepared WhatsApp message
        pending?.let { p ->
            if (System.currentTimeMillis() - p.createdAt > PENDING_TTL_MS) {
                pending = null
            } else {
                val words = lower.split(" ").filter { it.isNotEmpty() }
                if (words.size <= 4) {
                    if (words.any { it in NO_WORDS }) {
                        pending = null
                        return Outcome(true, "Okay dear, message cancel kar diya. Maine kuch bhi send nahi kiya.")
                    }
                    if (words.any { it in YES_WORDS }) return confirmSend(ctx, p)
                }
            }
        }

        // 3) Open a WhatsApp chat by phone number
        val phoneMatch = PHONE_RX.find(text)
        if (phoneMatch != null && !lower.startsWith("call") &&
            listOf("chat", "whatsapp", "message", "msg", "open", "kholo", "baat").any { lower.contains(it) }
        ) {
            return openChatByNumber(ctx, phoneMatch.value)
        }

        // 3a) Answer a ringing call by voice / text
        if (ANSWER_RX.containsMatchIn(lower) && CallControl.isRinging(ctx)) {
            val talk = TALK_RX.containsMatchIn(lower)
            val res = CallControl.answer(ctx)
            if (res.startsWith("success") && talk) CallControl.beginCallConversation(ctx, "")
            return Outcome(true, if (res.startsWith("success")) (if (talk) "Ji, call utha li, ab main baat karti hoon." else "Ji, call utha li.") else "Call nahi uth payi: " + res.removePrefix("failed: "), res.startsWith("success"))
        }

        // 3b) Play something on YouTube
        (YT_ON.find(lower) ?: YT_HINGLISH1.find(lower) ?: YT_HINGLISH2.find(lower))?.let {
            return playOnYouTube(ctx, it.groupValues[1])
        }

        // 4) Search
        SEARCH_ON.find(lower)?.let { return search(ctx, it.groupValues[1], it.groupValues[2]) }
        SEARCH_HINGLISH.find(lower)?.let { return search(ctx, it.groupValues[1], it.groupValues[2]) }
        SEARCH_PLAIN.find(lower)?.let { return search(ctx, it.groupValues[1], "google") }

        // 5) Open a website
        val opensSomething = listOf("open", "launch", "kholo", "khol", "website", "site", "visit", "go to").any { lower.contains(it) }
        if (opensSomething) {
            val spoken = lower.replace(" dot ", ".")
            DOMAIN_RX.find(spoken)?.let { return openWebsite(ctx, it.value) }
        }

        // 6) Open an app ("open X" / "X kholo"). Unknown app names are only accepted in the verb-first form.
        val verbFirst = OPEN_RX1.find(lower)?.groupValues?.get(1)?.trim()
        if (!verbFirst.isNullOrEmpty() && verbFirst.split(" ").size <= 3) return openApp(ctx, verbFirst, allowGeneric = true)
        val verbLast = OPEN_RX2.find(lower)?.groupValues?.get(1)?.trim()
        if (!verbLast.isNullOrEmpty() && ALIASES.containsKey(verbLast)) return openApp(ctx, verbLast, allowGeneric = false)

        return NOT_HANDLED
    }

    fun hasPendingMessage(): Boolean = pending != null

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // WhatsApp
    // ─────────────────────────────────────────────────────────────────────────────────────────────
    private fun openChatByNumber(ctx: Context, candidate: String): Outcome {
        val hasPlus = candidate.trim().startsWith("+")
        var digits = candidate.filter { it.isDigit() }
        if (digits.startsWith("00")) digits = digits.drop(2)
        else if (!hasPlus && digits.startsWith("0")) {
            return Outcome(true, "Dear, number ke saath country code bhi batayein, jaise +92 300 1234567. Isse main sahi chat khol sakungi.", false)
        }
        if (digits.length !in 8..15) {
            return Outcome(true, "Ye number sahi nahi lag raha. Please country code ke saath dobara batayein.", false)
        }
        val pkg = whatsAppPackage(ctx)
            ?: return Outcome(true, "WhatsApp is phone par installed nahi hai dear.", false)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).setPackage(pkg)
        if (!start(ctx, intent)) return Outcome(true, "WhatsApp chat khul nahi paayi. Please dobara try karein.", false)
        activeNumber = digits
        pending = null
        return Outcome(true, "Sure dear! Opening WhatsApp chat with +$digits.")
    }

    private fun prepareMessage(message: String): Outcome {
        val number = activeNumber
            ?: return Outcome(true, "Pehle batayein kis number par message bhejna hai, jaise: \"Open a chat with +92 300 1234567\".", false)
        pending = Pending(number, message, System.currentTimeMillis())
        return Outcome(
            true,
            "Message ready for +$number:\n\"$message\"\nShall I send it? Say \"yes\" to confirm or \"no\" to cancel."
        )
    }

    private fun confirmSend(ctx: Context, p: Pending): Outcome {
        pending = null
        if (p.inChat) {
            // reply already typed into the open WhatsApp chat: just press WhatsApp's own Send button
            if (!AccessibilityHelperService.isEnabled(ctx)) {
                return Outcome(true, "Reply WhatsApp mein typed hai. Accessibility band hai, Send aap khud dabayein.", false)
            }
            scope.launch {
                val ok = tapWhatsAppSend()
                ChatRepository.addAssistant(if (ok) "✅ Send dabaa diya." else "⚠️ Send button nahi dabaa saki, aap dabayein.")
            }
            return Outcome(true, "Okay dear! Send dabane ki koshish kar rahi hoon…")
        }
        val pkg = whatsAppPackage(ctx)
            ?: return Outcome(true, "WhatsApp installed nahi hai dear.", false)
        val uri = Uri.parse("https://wa.me/${p.number}?text=${Uri.encode(p.text)}")
        if (!start(ctx, Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))) {
            return Outcome(true, "WhatsApp khul nahi paaya, message send nahi hua.", false)
        }
        if (!AccessibilityHelperService.isEnabled(ctx)) {
            return Outcome(
                true,
                "Message WhatsApp mein ready hai. Accessibility service band hai, isliye Send button aap khud dabayein.",
                false
            )
        }
        // Accessibility is used ONLY now, after explicit confirmation, to tap WhatsApp's own Send button.
        scope.launch {
            val tapped = tapWhatsAppSend()
            ChatRepository.addAssistant(
                if (tapped) "✅ Send button dabaa diya (+${p.number}). Delivery WhatsApp mein check kar lein."
                else "⚠️ Message WhatsApp mein ready hai, lekin main Send button nahi dabaa saki. Please aap Send dabayein."
            )
        }
        return Outcome(true, "Okay dear! WhatsApp mein message khol rahi hoon, Send dabane ki koshish karti hoon…")
    }

    private suspend fun tapWhatsAppSend(): Boolean {
        repeat(25) {
            delay(400)
            val root = AccessibilityHelperService.getFreshRoot() ?: return@repeat
            if (root.packageName?.toString()?.startsWith("com.whatsapp") != true) return@repeat
            val node = findSendButton(root) ?: return@repeat
            return clickNodeOrParent(node)
        }
        return false
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")) {
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let { return it }
        }
        return root.findAccessibilityNodeInfosByText("Send")?.firstOrNull {
            it.contentDescription?.toString().equals("Send", true) || it.text?.toString().equals("Send", true)
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 4) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
            hops++
        }
        return false
    }

    private fun whatsAppPackage(ctx: Context): String? =
        PACKAGES[Target.WHATSAPP]?.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Apps / websites / search
    // ─────────────────────────────────────────────────────────────────────────────────────────────
    private fun openApp(ctx: Context, name: String, allowGeneric: Boolean): Outcome {
        val target = ALIASES[name]
        if (target == null) {
            if (!allowGeneric) return NOT_HANDLED
            // Any other installed app: reuse the existing app finder/launcher (strict name match only).
            val found = InstalledAppsManager.findApp(ctx, name) ?: return NOT_HANDLED
            if (!(found.name.contains(name) || name.contains(found.name))) return NOT_HANDLED
            return if (AppLauncher.launch(ctx, found.label)) Outcome(true, "Sure dear! Opening ${found.label}.")
            else Outcome(true, "Sorry dear, ${found.label} open nahi ho paaya.", false)
        }
        if (isDuplicate("open:${target.name}")) return Outcome(true, "Sure dear! Opening ${target.label}.")

        val ok = when (target) {
            Target.GALLERY -> start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
            Target.CAMERA -> start(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            Target.SETTINGS -> start(ctx, Intent(Settings.ACTION_SETTINGS))
            Target.CALCULATOR -> start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            else -> {
                val pkg = PACKAGES[target]?.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
                pkg != null && start(ctx, ctx.packageManager.getLaunchIntentForPackage(pkg)!!)
            }
        }
        return if (ok) Outcome(true, "Sure dear! Opening ${target.label}.")
        else Outcome(true, "Sorry dear, ${target.label} open nahi ho paaya. Shayad ye app is phone par installed nahi hai.", false)
    }

    private fun openWebsite(ctx: Context, domain: String): Outcome {
        val url = if (domain.startsWith("http")) domain else "https://$domain"
        if (isDuplicate("web:$url")) return Outcome(true, "Sure dear! Opening $domain.")
        return if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) Outcome(true, "Sure dear! Opening $domain.")
        else Outcome(true, "Website open nahi ho saki.", false)
    }

    private fun search(ctx: Context, query: String, site: String): Outcome {
        val q = query.trim()
        if (q.isEmpty()) return NOT_HANDLED
        val url = if (site == "youtube") "https://www.youtube.com/results?search_query=${Uri.encode(q)}"
        else "https://www.google.com/search?q=${Uri.encode(q)}"
        if (isDuplicate("search:$site:$q")) return Outcome(true, "Searching $q.")
        return if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) Outcome(true, "Sure dear! Searching \"$q\" on ${site.replaceFirstChar { it.uppercase() }}.")
        else Outcome(true, "Search open nahi ho saki.", false)
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    internal fun start(ctx: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Launch failed: ${e.message}")
            false
        }
        // Android 10+ blocks starting apps from the background unless MYRA has the "Display over other apps"
        // permission. If that is missing, also leave a one-tap notification so the action is never silently lost.
        if (isBackground(ctx) && !Settings.canDrawOverlays(ctx)) postTapToOpen(ctx, intent)
        return ok
    }

    private fun isBackground(ctx: Context): Boolean = try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (_: Exception) { false }

    private fun postTapToOpen(ctx: Context, intent: Intent) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("myra_open_actions", "MYRA actions", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(ctx, 7001, Intent(intent), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = android.app.Notification.Builder(ctx, "myra_open_actions")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("MYRA")
                .setContentText("Tap to open (enable 'Display over other apps' for MYRA to open apps automatically)")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(7001, n)
        } catch (_: Exception) { }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // YouTube
    // ─────────────────────────────────────────────────────────────────────────────────────────────
    private fun playOnYouTube(ctx: Context, query: String): Outcome {
        val q = query.trim()
        if (q.isEmpty()) return Outcome(true, "Kya play karna hai dear?", false)
        if (isDuplicate("yt:$q")) return Outcome(true, "Sure dear! YouTube par \"$q\" khol rahi hoon.")
        val yt = "com.google.android.youtube"
        val installed = ctx.packageManager.getLaunchIntentForPackage(yt) != null
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))
        val opened = if (installed) {
            start(ctx, Intent(Intent.ACTION_SEARCH).setPackage(yt).putExtra("query", q)) || start(ctx, web)
        } else start(ctx, web)
        if (!opened) return Outcome(true, "YouTube open nahi ho paaya.", false)
        val canTap = installed && AccessibilityHelperService.isEnabled(ctx)
        if (canTap) {
            scope.launch {
                if (!tapFirstYouTubeResult()) ChatRepository.addAssistant("YouTube search khul gaya hai, video aap tap kar dein.")
            }
        }
        return Outcome(
            true,
            if (canTap) "Sure dear! YouTube par \"$q\" search kiya, pehla video chalane ki koshish kar rahi hoon."
            else "Sure dear! YouTube par \"$q\" ka search khol diya. (Video khud chalane ke liye Accessibility on karein.)"
        )
    }

    private suspend fun tapFirstYouTubeResult(): Boolean {
        repeat(20) {
            delay(500)
            val root = AccessibilityHelperService.getFreshRoot() ?: return@repeat
            if (root.packageName?.toString() != "com.google.android.youtube") return@repeat
            val node = findFirstVideoResult(root) ?: return@repeat
            return clickNodeOrParent(node)
        }
        return false
    }

    private fun findFirstVideoResult(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val n = queue.removeFirst()
            visited++
            val d = n.contentDescription?.toString()
            if (d != null && d.length > 25 && !d.contains("Shorts", true) &&
                (d.contains("minute", true) || d.contains("second", true))
            ) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Tool calls coming from the live voice model (Gemini function calling)
    // ─────────────────────────────────────────────────────────────────────────────────────────────
    @Synchronized
    fun setInChatPending(text: String) {
        pending = Pending("", text, System.currentTimeMillis(), true)
    }

    private fun hasInChatPending(): Boolean = pending?.inChat == true

    private fun userSaidYes(): Boolean {
        if (!GeminiWebSocketClient.extrasEnabled) return true   // no transcript available: the model heard the user
        val words = normalize(lastHeard).split(" ").filter { it.isNotEmpty() }
        return words.size <= 4 && words.any { it in YES_WORDS } && words.none { it in NO_WORDS }
    }

    /** Sends the reply that was typed into the open WhatsApp chat (only after the user's explicit yes). */
    private suspend fun confirmInChatSend(): String {
        val p = synchronized(this) { pending?.takeIf { it.inChat } } ?: return "failed: there is no prepared reply (it may already have been sent or cancelled)"
        if (!userSaidYes()) return "failed: the user has not clearly confirmed yet. Ask them to say yes."
        synchronized(this) { pending = null }
        return if (tapWhatsAppSend()) "success: Send button pressed (reply: ${p.text.take(60)})"
        else "failed: could not press WhatsApp's Send button. Tell the user to press Send themselves."
    }

    suspend fun executeTool(context: Context, name: String, args: JSONObject): String {
        val ctx = context.applicationContext
        fun res(o: Outcome) = (if (o.success) "success: " else "failed: ") + o.reply
        return try {
            when (name) {
                "open_app" -> synchronized(this) {
                    val app = normalize(args.optString("app_name"))
                    if (app.isEmpty()) return@synchronized "failed: no app name"
                    val o = openApp(ctx, app, allowGeneric = true)
                    when {
                        o.handled -> res(o)
                        AppLauncher.launch(ctx, app) -> "success: Opened $app"
                        else -> "failed: '$app' is not installed on this phone"
                    }
                }
                "play_on_youtube" -> synchronized(this) { res(playOnYouTube(ctx, args.optString("query"))) }
                "open_website" -> synchronized(this) {
                    val u = args.optString("url").trim()
                    if (u.isEmpty()) "failed: no url" else res(openWebsite(ctx, u))
                }
                "search_web" -> synchronized(this) { res(search(ctx, args.optString("query"), "google")) }
                "whatsapp_open_chat" -> synchronized(this) { res(openChatByNumber(ctx, args.optString("phone_number"))) }
                "whatsapp_prepare_message" -> synchronized(this) {
                    val o = prepareMessage(args.optString("message").trim())
                    if (o.success) "prepared: The message is NOT sent yet. Ask the user to confirm (yes/no) and only then call whatsapp_send_confirmed. Never say it was sent before that."
                    else res(o)
                }
                "whatsapp_open_chat_by_name" -> WhatsAppAutomation.openChatByName(ctx, args.optString("contact_name"))
                "whatsapp_read_messages" -> WhatsAppAutomation.readMessages(args.optString("count").toIntOrNull() ?: 5)
                "whatsapp_play_voice_message" -> WhatsAppAutomation.playVoiceMessage()
                "whatsapp_prepare_reply" -> WhatsAppAutomation.prepareReply(args.optString("message").trim())
                "answer_call" -> {
                    val talk = args.optString("mode").contains("talk", true)
                    val res = CallControl.answer(ctx)
                    if (res.startsWith("success") && talk) {
                        CallControl.beginCallConversation(ctx, "")
                        "$res. Speakerphone is being switched on; you will now talk to the caller yourself."
                    } else res
                }
                "end_call" -> CallControl.end(ctx)
                "ui_read_screen" -> UiAutomation.readScreen()
                "ui_click" -> UiAutomation.click(args.optString("text"), false)
                "ui_click_confirmed" ->
                    if (!userSaidYes()) "failed: the user has not clearly confirmed yet. Ask them to say yes."
                    else UiAutomation.click(args.optString("text"), true)
                "ui_type" -> UiAutomation.type(args.optString("text"))
                "ui_scroll" -> UiAutomation.scroll(args.optString("direction"))
                "ui_press" -> UiAutomation.press(args.optString("key"))
                "whatsapp_send_confirmed" -> if (hasInChatPending()) confirmInChatSend() else synchronized(this) {
                    val p = pending ?: return@synchronized "failed: there is no prepared message (or it was already sent/cancelled)"
                    if (GeminiWebSocketClient.extrasEnabled) {
                        val words = normalize(lastHeard).split(" ").filter { it.isNotEmpty() }
                        val said = words.size <= 4 && words.any { it in YES_WORDS } && words.none { it in NO_WORDS }
                        if (!said) return@synchronized "failed: the user has not clearly confirmed yet. Ask them to say yes."
                    }
                    res(confirmSend(ctx, p))
                }
                "character_action" -> {
                    val act = com.myra.assistant.ui.avatar.CharacterAction.fromKey(args.optString("action"))
                    if (act == null) "failed: unknown action '${args.optString("action")}'. Use dance, spin, jump, wave, bow, sway or stop."
                    else "success: " + com.myra.assistant.ui.avatar.CharacterActionBus.perform(act)
                }
                "run_command" -> {
                    val cmd = args.optString("command").trim()
                    if (cmd.isEmpty()) "failed: no command"
                    else {
                        val r = DynamicDecisionEngine.execute(ctx, IntentAnalyzer.analyze(cmd))
                        if (r.isBlank()) "failed: command not understood" else "success: $r"
                    }
                }
                else -> "failed: unknown function $name"
            }
        } catch (e: Exception) {
            "failed: ${e.message}"
        }
    }

    /** The existing command pipeline may also react to the same sentence; never fire the same action twice in 4 s. */
    private fun isDuplicate(key: String): Boolean {
        val now = System.currentTimeMillis()
        val dup = key == lastActionKey && now - lastActionAt < 4000
        lastActionKey = key
        lastActionAt = now
        return dup
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}+\\s.]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.')
}

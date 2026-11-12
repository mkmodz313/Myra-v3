package com.myra.assistant.control

import android.content.Context
import com.myra.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.delay

/**
 * WhatsApp by contact NAME (the name you saved): open the chat, read the visible messages, play a voice message,
 * type a reply. Sending a reply always needs the user's explicit "yes" (handled in PhoneControl).
 * Uses the user's Accessibility Service; WhatsApp updates can move buttons, so every step falls back to labels.
 */
object WhatsAppAutomation {

    private val PKGS = listOf("com.whatsapp", "com.whatsapp.w4b")
    private val TABS = setOf("chats", "updates", "calls", "communities", "status")

    private fun inWhatsApp(pkg: CharSequence?) = pkg?.toString()?.startsWith("com.whatsapp") == true

    suspend fun openChatByName(ctx: Context, name: String): String {
        if (!UiAutomation.available()) return UiAutomation.NEED_A11Y
        val who = name.trim()
        if (who.isEmpty()) return "failed: no contact name"
        val pkg = PKGS.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
            ?: return "failed: WhatsApp is not installed"
        if (!PhoneControl.start(ctx, ctx.packageManager.getLaunchIntentForPackage(pkg)!!)) return "failed: WhatsApp could not be opened"

        if (!UiAutomation.waitUntil(5000) { inWhatsApp(UiAutomation.root()?.packageName) })
            return "failed: WhatsApp did not come to the front. Enable 'Display over other apps' for MYRA."

        // get back to the main chat list (never press Back there, it would leave WhatsApp)
        for (i in 0 until 5) {
            val r = UiAutomation.root() ?: break
            if (!inWhatsApp(r.packageName)) return "failed: lost the WhatsApp window"
            val onList = UiAutomation.nodes(r).any { it.isVisibleToUser && UiAutomation.label(it).lowercase() in TABS }
            if (onList) break
            UiAutomation.press("back")
            delay(350)
        }

        val search = UiAutomation.root()?.let { UiAutomation.find(it, "Search") }
            ?: return "failed: WhatsApp search button not found"
        UiAutomation.clickNode(search)
        if (!UiAutomation.waitUntil(2500) { UiAutomation.root()?.let { r -> UiAutomation.nodes(r).any { it.isEditable && it.isVisibleToUser } } == true })
            return "failed: WhatsApp search box did not open"
        val typed = UiAutomation.type(who)
        if (!typed.startsWith("success")) return typed
        delay(900)

        val r = UiAutomation.root() ?: return "failed: screen not readable"
        val box = UiAutomation.nodes(r).firstOrNull { it.isEditable && it.isVisibleToUser }
        val boxBottom = box?.let { UiAutomation.bounds(it).bottom } ?: 0
        val lower = who.lowercase()
        val candidates = UiAutomation.nodes(r).filter {
            it.isVisibleToUser && !it.isEditable && UiAutomation.label(it).lowercase().contains(lower) &&
                UiAutomation.bounds(it).top >= boxBottom
        }.sortedBy { UiAutomation.bounds(it).top }
        val pick = candidates.firstOrNull { UiAutomation.idEnds(it, "conversations_row_contact_name") || UiAutomation.idEnds(it, "contact_row_container") }
            ?: candidates.firstOrNull()
            ?: return "failed: no chat or contact named '$who' found in WhatsApp"
        UiAutomation.clickNode(pick)

        val opened = UiAutomation.waitUntil(3500) { UiAutomation.root()?.let { UiAutomation.findById(it, "entry").isNotEmpty() } == true }
        return if (opened) "success: opened the WhatsApp chat with ${UiAutomation.label(pick)}"
        else "failed: tapped '${UiAutomation.label(pick)}' but the chat screen did not open"
    }

    fun readMessages(count: Int): String {
        if (!UiAutomation.available()) return UiAutomation.NEED_A11Y
        val r = UiAutomation.root() ?: return "failed: screen not readable"
        if (!inWhatsApp(r.packageName)) return "failed: WhatsApp is not on screen. Open the chat first."
        val width = UiAutomation.bounds(r).right.coerceAtLeast(1)
        val header = UiAutomation.findById(r, "conversation_contact_name").firstOrNull()?.let { UiAutomation.label(it) } ?: "unknown"

        data class Line(val top: Int, val text: String)
        val lines = ArrayList<Line>()
        for (n in UiAutomation.findById(r, "message_text")) {
            if (!n.isVisibleToUser) continue
            val t = n.text?.toString()?.trim().orEmpty()
            if (t.isEmpty()) continue
            val b = UiAutomation.bounds(n)
            val mine = b.left > (width - b.right)          // closer to the right edge = sent by the user
            lines.add(Line(b.top, (if (mine) "ME: " else "THEM: ") + t))
        }
        for (n in UiAutomation.nodes(r)) {
            if (n.isVisibleToUser && UiAutomation.label(n).contains("voice message", true)) {
                val b = UiAutomation.bounds(n)
                lines.add(Line(b.top, (if (b.left > (width - b.right)) "ME: " else "THEM: ") + "[voice message]"))
            }
        }
        if (lines.isEmpty()) return "failed: no messages visible. Is a chat open? (header: $header)"
        val last = lines.sortedBy { it.top }.takeLast(count.coerceIn(1, 15))
        return "chat with $header, visible messages (oldest first):\n" + last.joinToString("\n") { it.text }
    }

    fun playVoiceMessage(): String {
        if (!UiAutomation.available()) return UiAutomation.NEED_A11Y
        val r = UiAutomation.root() ?: return "failed: screen not readable"
        if (!inWhatsApp(r.packageName)) return "failed: WhatsApp is not on screen"
        val btn = UiAutomation.nodes(r).filter { it.isVisibleToUser && UiAutomation.label(it).contains("play voice message", true) }
            .maxByOrNull { UiAutomation.bounds(it).top }
            ?: return "failed: no voice message visible in this chat"
        return if (UiAutomation.clickNode(btn)) "success: playing the latest visible voice message" else "failed: could not press play"
    }

    /** Types the reply into the chat box but does NOT send it. */
    fun prepareReply(text: String): String {
        if (!UiAutomation.available()) return UiAutomation.NEED_A11Y
        val r = UiAutomation.root() ?: return "failed: screen not readable"
        if (!inWhatsApp(r.packageName)) return "failed: WhatsApp is not on screen"
        val entry = UiAutomation.findById(r, "entry").firstOrNull()
            ?: UiAutomation.nodes(r).firstOrNull { it.isEditable && it.isVisibleToUser }
            ?: return "failed: no message box found. Open a chat first."
        if (!UiAutomation.setText(entry, text)) return "failed: could not type into the message box"
        PhoneControl.setInChatPending(text)
        return "prepared: the reply is typed but NOT sent. Ask the user to confirm (yes/no); only then call whatsapp_send_confirmed."
    }

    fun a11yOn(ctx: Context) = AccessibilityHelperService.isEnabled(ctx)
}

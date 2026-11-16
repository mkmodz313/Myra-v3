package com.myra.assistant.control

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.myra.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.delay

/**
 * Generic "hands" for any app, built on the user's own Accessibility Service (only works while the user has it
 * switched on). Read the screen, tap a labelled option, type, scroll, go back / home.
 *
 * Safety rules built in:
 *  - password fields are never read or typed into
 *  - clicking risky labels (Send, Pay, Buy, Delete, Confirm, Transfer ...) needs an explicit user "yes"
 *  - apps that block accessibility (banking apps, secure screens) simply show nothing - nothing is bypassed
 */
object UiAutomation {

    const val NEED_A11Y =
        "failed: MYRA's Accessibility service is OFF. Tell the user: Settings -> Accessibility -> MYRA -> turn ON (on Android 13+ also App info -> menu -> Allow restricted settings)."

    private val RISKY = Regex(
        "(?i)\\b(send|pay|buy|purchase|order|checkout|confirm|transfer|delete|remove|uninstall|submit|subscribe|book|donate|erase|reset|post|publish|place order)\\b"
    )

    private fun svc() = AccessibilityHelperService.instance
    fun available(): Boolean = svc() != null
    fun root(): AccessibilityNodeInfo? = svc()?.rootInActiveWindow

    fun label(n: AccessibilityNodeInfo): String =
        (n.text?.toString()?.takeIf { it.isNotBlank() } ?: n.contentDescription?.toString() ?: "").trim()

    fun nodes(root: AccessibilityNodeInfo, limit: Int = 1500): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty() && out.size < limit) {
            val n = q.removeFirst()
            out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return out
    }

    fun bounds(n: AccessibilityNodeInfo): Rect = Rect().also { n.getBoundsInScreen(it) }

    fun idEnds(n: AccessibilityNodeInfo, suffix: String) = n.viewIdResourceName?.endsWith(":id/$suffix") == true

    fun findById(root: AccessibilityNodeInfo, suffix: String): List<AccessibilityNodeInfo> =
        nodes(root).filter { idEnds(it, suffix) }

    /** Best visible node whose text / description matches [query] (exact > starts with > contains). */
    fun find(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        var bestLen = Int.MAX_VALUE
        for (n in nodes(root)) {
            if (!n.isVisibleToUser || n.isPassword) continue
            val l = label(n).lowercase()
            if (l.isEmpty()) continue
            val score = when { l == q -> 3; l.startsWith(q) -> 2; l.contains(q) -> 1; else -> 0 }
            if (score == 0) continue
            if (score > bestScore || (score == bestScore && l.length < bestLen)) {
                best = n; bestScore = score; bestLen = l.length
            }
        }
        return best
    }

    fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        var hops = 0
        while (c != null && hops < 7) {
            if (c.isClickable && c.isEnabled) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            c = c.parent
            hops++
        }
        return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun setText(target: AccessibilityNodeInfo, text: String): Boolean {
        if (target.isPassword) return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    suspend fun waitUntil(timeoutMs: Long, stepMs: Long = 150, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (try { cond() } catch (_: Exception) { false }) return true
            delay(stepMs)
        }
        return try { cond() } catch (_: Exception) { false }
    }

    // ── tool implementations (return plain text for the voice model) ───────────────────────────
    fun readScreen(max: Int = 80): String {
        if (!available()) return NEED_A11Y
        val r = root() ?: return "failed: the screen cannot be read (secure screen or app blocks accessibility)"
        val pkg = r.packageName?.toString() ?: "unknown"
        data class Item(val top: Int, val left: Int, val text: String)
        val items = nodes(r).filter { it.isVisibleToUser && !it.isPassword }.mapNotNull { n ->
            val l = label(n)
            if (l.isEmpty()) null else {
                val b = bounds(n)
                val tag = when { n.isEditable -> "[input] "; n.isClickable || n.isCheckable -> "[tap] "; else -> "" }
                Item(b.top, b.left, tag + l.take(120))
            }
        }.sortedWith(compareBy<Item>({ it.top / 12 }, { it.left })).distinctBy { it.text }.take(max)
        if (items.isEmpty()) return "app: $pkg\n(nothing readable on screen)"
        return "app: $pkg\n" + items.joinToString("\n") { it.text }
    }

    fun click(query: String, userConfirmed: Boolean): String {
        if (!available()) return NEED_A11Y
        val r = root() ?: return "failed: screen not readable"
        val n = find(r, query) ?: return "failed: no visible option named '$query'. Use ui_read_screen to see what is on screen."
        val l = label(n)
        if (!userConfirmed && (RISKY.containsMatchIn(l) || RISKY.containsMatchIn(query))) {
            return "needs_confirmation: '$l' is a sensitive action. Ask the user to say yes, then call ui_click_confirmed."
        }
        return if (clickNode(n)) "success: tapped '$l'" else "failed: could not tap '$l'"
    }

    fun type(text: String): String {
        if (!available()) return NEED_A11Y
        val r = root() ?: return "failed: screen not readable"
        val target = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: nodes(r).firstOrNull { it.isEditable && it.isVisibleToUser }
            ?: return "failed: no text box on screen. Tap the search/input field first."
        if (target.isPassword) return "refused: password fields are never filled by MYRA."
        return if (setText(target, text)) "success: typed" else "failed: could not type here"
    }

    fun scroll(direction: String): String {
        if (!available()) return NEED_A11Y
        val r = root() ?: return "failed: screen not readable"
        val sc = nodes(r).filter { it.isScrollable && it.isVisibleToUser }
            .maxByOrNull { bounds(it).let { b -> b.width() * b.height() } }
            ?: return "failed: nothing scrollable here"
        val up = direction.trim().lowercase().startsWith("up")
        val ok = sc.performAction(if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        return if (ok) "success: scrolled ${if (up) "up" else "down"}" else "failed: cannot scroll further"
    }

    fun press(key: String): String {
        val s = svc() ?: return NEED_A11Y
        val action = when (key.trim().lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents", "recent" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications", "notification" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> return "failed: unknown key '$key' (use back, home, recents, notifications)"
        }
        return if (s.performGlobalAction(action)) "success: pressed $key" else "failed: could not press $key"
    }
}

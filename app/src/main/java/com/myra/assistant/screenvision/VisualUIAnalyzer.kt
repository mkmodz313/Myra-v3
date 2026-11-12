package com.myra.assistant.screenvision

import android.graphics.Bitmap
import com.myra.assistant.models.ScreenNodeModel

object VisualUIAnalyzer {

    fun analyze(bitmap: Bitmap?, nodes: List<ScreenNodeModel>): String {
        val nodesSummary = nodes
            .filter { (it.text != null || it.contentDesc != null) }
            .take(20)
            .joinToString(", ") { it.text ?: it.contentDesc ?: "" }
        return "Screen context: $nodesSummary"
    }

    fun findBestActionTarget(nodes: List<ScreenNodeModel>, intent: String): ScreenNodeModel? {
        val lower = intent.lowercase()
        return nodes.find { node ->
            val t = (node.text ?: node.contentDesc ?: "").lowercase()
            t.isNotBlank() && (t.contains(lower) || lower.contains(t))
        }
    }
}

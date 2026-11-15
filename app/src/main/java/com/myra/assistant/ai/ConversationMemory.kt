package com.myra.assistant.ai

data class Turn(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

object ConversationMemory {
    private val history = mutableListOf<Turn>()
    private const val MAX_TURNS = 20

    fun addUser(text: String) {
        history.add(Turn("user", text))
        trim()
    }

    fun addAssistant(text: String) {
        history.add(Turn("assistant", text))
        trim()
    }

    fun getRecent(n: Int = 6): List<Turn> = history.takeLast(n)

    fun clear() = history.clear()

    private fun trim() {
        if (history.size > MAX_TURNS) {
            history.removeAt(0)
        }
    }

    fun getContextSummary(): String = getRecent().joinToString("\n") {
        "${it.role}: ${it.text}"
    }
}

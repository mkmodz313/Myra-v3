package com.myra.assistant.data

import android.content.Context
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Single shared conversation (voice + typed). Voice transcripts, typed messages and MYRA's replies all
 * land here, in arrival order, and are persisted to disk so history survives app restarts.
 */
object ChatRepository {

    data class Msg(
        val id: Long,
        val fromUser: Boolean,
        val text: String,
        val time: Long,
        val streaming: Boolean = false
    )

    private const val FILE_NAME = "chat_history.json"
    private const val MAX_MESSAGES = 500

    private val items = ArrayList<Msg>()
    val messages = MutableLiveData<List<Msg>>(emptyList())

    private var storeFile: File? = null
    private var nextId = 1L
    private var initialized = false
    private val io = Executors.newSingleThreadExecutor()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val f = File(context.applicationContext.filesDir, FILE_NAME)
        storeFile = f
        try {
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optLong("id", nextId)
                    items.add(Msg(id, o.optBoolean("u"), o.optString("t"), o.optLong("ts")))
                    if (id >= nextId) nextId = id + 1
                }
            }
        } catch (_: Exception) {
            items.clear()
        }
        publish()
    }

    @Synchronized
    fun addUser(text: String) = add(true, text)

    @Synchronized
    fun addAssistant(text: String) = add(false, text)

    private fun add(fromUser: Boolean, raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        finishStreamingLocked()
        val last = items.lastOrNull()
        val now = System.currentTimeMillis()
        // Duplicate guard: same role + same text arriving again within a few seconds.
        if (last != null && last.fromUser == fromUser && now - last.time < 4000 &&
            (last.text.equals(text, true) || last.text.contains(text, true))
        ) return
        items.add(Msg(nextId++, fromUser, text, now))
        trimLocked()
        publish()
        persist()
    }

    /** Appends a live transcript chunk to the message that is currently streaming for that role. */
    @Synchronized
    fun appendStreaming(fromUser: Boolean, chunk: String) {
        if (chunk.isEmpty()) return
        val idx = items.lastIndex
        val last = items.lastOrNull()
        if (last != null && last.streaming && last.fromUser == fromUser) {
            items[idx] = last.copy(text = last.text + chunk)
        } else {
            finishStreamingLocked()
            if (chunk.isBlank()) return
            items.add(Msg(nextId++, fromUser, chunk.trimStart(), System.currentTimeMillis(), true))
            trimLocked()
        }
        publish()
    }

    @Synchronized
    fun finishStreaming() {
        if (finishStreamingLocked()) {
            publish()
            persist()
        }
    }

    private fun finishStreamingLocked(): Boolean {
        var changed = false
        for (i in items.indices) {
            val m = items[i]
            if (m.streaming) {
                val t = m.text.trim()
                if (t.isEmpty()) {
                    items.removeAt(i)
                } else {
                    items[i] = m.copy(text = t, streaming = false)
                }
                changed = true
                break
            }
        }
        return changed
    }

    @Synchronized
    fun clear() {
        items.clear()
        publish()
        persist()
    }

    @Synchronized
    fun hasStreamingAssistant(): Boolean = items.lastOrNull()?.let { it.streaming && !it.fromUser } == true

    private fun trimLocked() {
        while (items.size > MAX_MESSAGES) items.removeAt(0)
    }

    private fun publish() {
        messages.postValue(ArrayList(items))
    }

    private fun persist() {
        val f = storeFile ?: return
        val snapshot = ArrayList(items)
        io.execute {
            try {
                val arr = JSONArray()
                snapshot.filter { !it.streaming }.forEach {
                    arr.put(JSONObject().put("id", it.id).put("u", it.fromUser).put("t", it.text).put("ts", it.time))
                }
                val tmp = File(f.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(f)
            } catch (_: Exception) {
            }
        }
    }
}

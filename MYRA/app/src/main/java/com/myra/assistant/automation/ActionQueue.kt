package com.myra.assistant.automation

import com.myra.assistant.models.ActionModel
import java.util.concurrent.ConcurrentLinkedQueue

object ActionQueue {
    private val queue = ConcurrentLinkedQueue<ActionModel>()

    fun enqueue(action: ActionModel) = queue.offer(action)
    fun dequeue(): ActionModel? = queue.poll()
    fun isEmpty() = queue.isEmpty()
    fun clear() = queue.clear()
    fun size() = queue.size
}

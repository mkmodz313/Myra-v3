package com.myra.assistant.websocket

/**
 * WebSocket-level audio stream receiver callback holder
 */
class AudioStreamReceiver(val onData: (ByteArray) -> Unit) {
    fun receive(data: ByteArray) = onData(data)
}

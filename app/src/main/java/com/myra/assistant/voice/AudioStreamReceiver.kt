package com.myra.assistant.voice

/**
 * Receives audio chunks from Gemini and routes to AudioPlayer
 */
class AudioStreamReceiver(private val player: AudioPlayer) {

    fun receive(data: ByteArray) {
        if (data.isNotEmpty()) player.playChunk(data)
    }

    fun clear() = player.clearAndStop()
}

package com.myra.assistant.voice

import com.myra.assistant.utils.AudioUtils
import com.myra.assistant.websocket.GeminiWebSocketClient

/**
 * Bridges AudioRecorder chunks → GeminiWebSocketClient
 */
class AudioStreamSender(private val client: GeminiWebSocketClient) {

    fun send(pcm: ByteArray) {
        if (VoiceStateManager.isAiSpeaking()) return
        client.sendAudioChunk(pcm)
    }
}

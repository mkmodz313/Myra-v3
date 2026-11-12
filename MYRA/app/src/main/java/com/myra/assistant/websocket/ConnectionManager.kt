package com.myra.assistant.websocket

import com.myra.assistant.utils.Logger

/**
 * Manages WebSocket connection lifecycle with auto-reconnect.
 */
class ConnectionManager(
    private val apiKey: String,
    private val systemPrompt: String,
    private val onConnected: () -> Unit,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onTextReceived: (String) -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val TAG = "CONN_MGR"
    private var client: GeminiWebSocketClient? = null
    private var reconnectAttempts = 0
    private val MAX_RECONNECT = 5

    fun connect() {
        reconnectAttempts = 0
        createClient()
    }

    private fun createClient() {
        client = GeminiWebSocketClient(
            apiKey, systemPrompt,
            onConnected = {
                reconnectAttempts = 0
                onConnected()
            },
            onAudioReceived = onAudioReceived,
            onTextReceived = onTextReceived,
            onTurnComplete = onTurnComplete,
            onError = { msg ->
                Logger.e(TAG, "Error: $msg | attempts=$reconnectAttempts")
                onError(msg)
                if (reconnectAttempts < MAX_RECONNECT) {
                    reconnectAttempts++
                    Thread.sleep(3000)
                    createClient()
                }
            }
        )
        client?.connect()
    }

    fun getClient() = client

    fun disconnect() {
        client?.disconnect()
        client = null
    }

    fun isConnected() = client?.isConnected() == true
}

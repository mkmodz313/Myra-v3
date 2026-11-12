package com.myra.assistant.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.myra.assistant.utils.Logger

class NetworkMonitor(context: Context) {
    private val TAG = "NET"
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var isConnected = false
        private set
    var onConnectionChange: ((Boolean) -> Unit)? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isConnected = true
            Logger.d(TAG, "Network available")
            onConnectionChange?.invoke(true)
        }
        override fun onLost(network: Network) {
            isConnected = false
            Logger.d(TAG, "Network lost")
            onConnectionChange?.invoke(false)
        }
    }

    fun start() {
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, callback)
        isConnected = cm.activeNetwork != null
    }

    fun stop() {
        try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
    }
}

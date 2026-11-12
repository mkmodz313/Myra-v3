package com.myra.assistant.utils

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/** Live internet state for the whole app: ONLINE, POOR (weak / unstable) or OFFLINE (no internet). */
object NetworkState {
    const val ONLINE = 0
    const val POOR = 1
    const val OFFLINE = 2

    private val _state = MutableLiveData(ONLINE)
    val state: LiveData<Int> = _state

    private val main = Handler(Looper.getMainLooper())
    private var cm: ConnectivityManager? = null
    private var troubleUntil = 0L
    private var pending = ONLINE
    private val apply = Runnable { if (_state.value != pending) _state.value = pending }

    fun register(app: Application) {
        try {
            val c = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm = c
            c.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = evaluate()
                override fun onLost(network: Network) = evaluate()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = evaluate()
            })
            evaluate()
        } catch (_: Throwable) { }
    }

    /** Called when the voice connection keeps failing although the phone says it is online. */
    fun reportTrouble() {
        troubleUntil = System.currentTimeMillis() + 15_000
        evaluate()
        main.postDelayed({ evaluate() }, 15_500)
    }

    private fun compute(): Int {
        val c = cm ?: return ONLINE
        val caps = c.getNetworkCapabilities(c.activeNetwork) ?: return OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return OFFLINE
        if (System.currentTimeMillis() < troubleUntil) return POOR
        val down = caps.linkDownstreamBandwidthKbps
        return if (down in 1..400) POOR else ONLINE
    }

    private fun evaluate() {
        pending = try { compute() } catch (_: Throwable) { ONLINE }
        main.removeCallbacks(apply)
        // coming back online is shown at once; going offline / poor waits a moment so brief blips do not flicker
        main.postDelayed(apply, if (pending == ONLINE) 0L else 1200L)
    }
}

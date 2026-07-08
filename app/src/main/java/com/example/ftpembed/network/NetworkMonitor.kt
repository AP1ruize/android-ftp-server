package com.example.ftpembed.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkKind {
    Wifi,
    Hotspot,
    None,
}

class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkKind = MutableStateFlow(NetworkKind.None)
    val networkKind: StateFlow<NetworkKind> = _networkKind.asStateFlow()

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()

        override fun onLost(network: Network) = refresh()

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            refresh()
    }

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        registered = true
        refresh()
    }

    fun stop() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(callback)
        registered = false
        _networkKind.value = NetworkKind.None
    }

    fun isUsableNetwork(): Boolean = _networkKind.value != NetworkKind.None

    private fun refresh() {
        _networkKind.value = classifyCurrentNetwork()
    }

    private fun classifyCurrentNetwork(): NetworkKind {
        if (hasNetworkWithTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return NetworkKind.Wifi
        }

        if (hasHotspotLanIp()) {
            return NetworkKind.Hotspot
        }

        return NetworkKind.None
    }

    private fun hasNetworkWithTransport(transport: Int): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val activeCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (activeCapabilities?.hasTransport(transport) == true) {
            return true
        }
        return connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)?.hasTransport(transport) == true
        }
    }

    private fun hasHotspotLanIp(): Boolean {
        val ip = LocalIpProvider.getLanIpv4() ?: return false
        return ip.startsWith("192.168.")
    }
}

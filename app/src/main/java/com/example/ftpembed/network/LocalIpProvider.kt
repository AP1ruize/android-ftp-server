package com.example.ftpembed.network

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object LocalIpProvider {
    fun getLanIpv4(): String? {
        return NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull(::isUsableLanIpv4)
            ?.hostAddress
    }

    fun chooseLanIpv4(addresses: Iterable<InetAddress>): String? {
        return addresses.firstOrNull(::isUsableLanIpv4)?.hostAddress
    }

    private fun isUsableLanIpv4(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress) {
            return false
        }
        val host = address.hostAddress ?: return false
        return host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.").containsMatchIn(host)
    }
}

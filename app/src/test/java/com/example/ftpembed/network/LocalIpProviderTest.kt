package com.example.ftpembed.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalIpProviderTest {
    @Test
    fun chooseLanIpv4_prefersPrivateIpv4() {
        val ip = LocalIpProvider.chooseLanIpv4(
            listOf(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("169.254.1.2"),
                InetAddress.getByName("192.168.1.23"),
            ),
        )

        assertEquals("192.168.1.23", ip)
    }

    @Test
    fun chooseLanIpv4_accepts172PrivateRange() {
        val ip = LocalIpProvider.chooseLanIpv4(listOf(InetAddress.getByName("172.20.0.5")))

        assertEquals("172.20.0.5", ip)
    }

    @Test
    fun chooseLanIpv4_rejectsPublicAddress() {
        val ip = LocalIpProvider.chooseLanIpv4(listOf(InetAddress.getByName("8.8.8.8")))

        assertNull(ip)
    }
}

package com.vaydelux.bousygram.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConnectivityObserverTest {

    @Test
    fun testAppNetworkStateProperties() {
        val offline = AppNetworkState.Offline
        assertFalse(offline.isConnected)
        assertFalse(offline.isCellular)

        val wifi = AppNetworkState.WiFi(isMetered = false)
        assertTrue(wifi.isConnected)
        assertFalse(wifi.isCellular)
        assertFalse(wifi.isMetered)

        val cellular = AppNetworkState.Cellular(isRoaming = true)
        assertTrue(cellular.isConnected)
        assertTrue(cellular.isCellular)
        assertTrue(cellular.isRoaming)

        val other = AppNetworkState.Other
        assertTrue(other.isConnected)
        assertFalse(other.isCellular)
    }

    @Test
    fun testNetworkStateFlow() {
        assertEquals(AppNetworkState.Offline, NetworkConnectivityObserver.networkState.value)
        assertFalse(NetworkConnectivityObserver.isCellular)
    }
}

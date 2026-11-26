package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RollingLanDirectMediaPolicyTest {
    @Test fun boundsAndOverflow() {
        val cap = RollingLanDirectMediaPolicy.MAX_SOURCE_BYTES
        assertTrue(RollingLanDirectMediaPolicy.validRange(cap, cap - 1, 1))
        assertFalse(RollingLanDirectMediaPolicy.validRange(cap, cap - 1, 2))
        assertFalse(RollingLanDirectMediaPolicy.validRange(cap, Long.MAX_VALUE, 1))
        assertFalse(RollingLanDirectMediaPolicy.validRange(cap, -1, 1))
        assertFalse(RollingLanDirectMediaPolicy.validRange(cap, 0, 65537))
        assertFalse(RollingLanDirectMediaPolicy.validRange(cap + 1, 0, 1))
    }
    @Test fun recognizesOnlySupportedContainerHeaders() {
        assertTrue(RollingLanDirectMediaPolicy.supportedHeader(byteArrayOf(0, 0, 0, 24) + "ftyp".toByteArray()))
        assertTrue(RollingLanDirectMediaPolicy.supportedHeader(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())))
        val ts = ByteArray(377).apply { this[0] = 0x47; this[188] = 0x47; this[376] = 0x47 }
        assertTrue(RollingLanDirectMediaPolicy.supportedHeader(ts))
        assertFalse(RollingLanDirectMediaPolicy.supportedHeader(ts.copyOf(376)))
        assertFalse(RollingLanDirectMediaPolicy.supportedHeader("http://example.test".toByteArray()))
        assertFalse(RollingLanDirectMediaPolicy.supportedHeader(byteArrayOf()))
    }
}

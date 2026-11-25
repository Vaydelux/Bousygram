package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RemuxContainerPolicyTest {
    @Test fun admitsKnownHeadersNotTransportStreamOrShortInput() {
        assertTrue(RemuxContainerPolicy.permits(byteArrayOf(0,0,0,24,102,116,121,112)))
        assertTrue(RemuxContainerPolicy.permits(byteArrayOf(0x1a,0x45,0xdf.toByte(),0xa3.toByte(),0,0,0,0)))
        assertFalse(RemuxContainerPolicy.permits(byteArrayOf(0x47,0,0,0,0,0,0,0)))
        assertFalse(RemuxContainerPolicy.permits(ByteArray(7)))
        assertFalse(RemuxContainerPolicy.permits(ByteArray(8)))
    }
}

package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RollingLanMediaModeTest {
    @Test fun normalMediaAndDiagnosticsRemainSeparate() {
        val direct = RollingLanMediaMode.DIRECT_VIDEO
        val telegram = RollingLanMediaMode.TELEGRAM
        assertFalse(direct.diagnostic)
        assertFalse(telegram.diagnostic)
        assertTrue(direct.live)
        assertFalse(telegram.live)
        assertEquals(3, RollingLanMediaMode.entries.count { it.diagnostic })
    }
    @Test fun sendRequiresAppropriateSourceAndInvitation() {
        for (mode in RollingLanMediaMode.entries) {
            assertFalse(mode.canSend(false, true, true))
            assertTrue(mode.canSend(true, true, true))
            assertEquals(!mode.needsFile && mode != RollingLanMediaMode.TELEGRAM,
                mode.canSend(true, false, false))
            assertFalse(mode.canReceive(true))
            assertTrue(mode.canReceive(false))
        }
    }
}

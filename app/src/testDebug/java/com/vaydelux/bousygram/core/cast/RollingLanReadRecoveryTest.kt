package com.vaydelux.bousygram.core.cast

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class RollingLanReadRecoveryTest {
    @Test fun immutableRangesHaveTwoRetriesTotal() {
        val recovery = RollingLanReadRecovery { true }
        assertTrue(recovery.retry(EOFException(), "/segment/1.m4s", 32))
        assertTrue(recovery.retry(SocketTimeoutException(), "/segment/1.m4s", 64))
        assertFalse(recovery.retry(SocketException(), "/segment/1.m4s", 128))
    }
    @Test fun playlistCannotBeSplicedAndSecurityErrorsNeverRetry() {
        val recovery = RollingLanReadRecovery { true }
        assertFalse(recovery.retry(EOFException(), "/index.m3u8", 1))
        assertFalse(recovery.retry(SSLHandshakeException("pin"), "/segment/1.m4s", 0))
        assertFalse(recovery.retry(IOException("refused"), "/segment/1.m4s", 0))
        assertTrue(recovery.retry(EOFException(), "/index.m3u8", 0))
    }
    @Test fun revocationBlocksRetryWithoutConsumingBudget() {
        var active = false
        val recovery = RollingLanReadRecovery { active }
        assertFalse(recovery.retry(SocketException(), "/init.mp4", 0))
        active = true
        assertTrue(recovery.retry(SocketException(), "/init.mp4", 0))
        active = false
        assertFalse(recovery.retry(SocketException(), "/init.mp4", 1))
    }
}

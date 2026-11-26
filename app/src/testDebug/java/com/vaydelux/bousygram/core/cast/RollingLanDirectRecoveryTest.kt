package com.vaydelux.bousygram.core.cast

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class RollingLanDirectRecoveryTest {
    @Test fun retriesOnlyTransportInterruptions() {
        assertTrue(RollingLanDirectRecovery.retryable(EOFException()))
        assertTrue(RollingLanDirectRecovery.retryable(SocketException()))
        assertTrue(RollingLanDirectRecovery.retryable(SocketTimeoutException()))
        assertFalse(RollingLanDirectRecovery.retryable(SSLHandshakeException("peer")))
        assertFalse(RollingLanDirectRecovery.retryable(IOException("range refused")))
    }
}

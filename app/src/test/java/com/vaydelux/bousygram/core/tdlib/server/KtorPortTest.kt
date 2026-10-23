package com.vaydelux.bousygram.core.tdlib.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorPortTest {
    @Test
    fun testKtorEphemeralPort() = runBlocking {
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {}.start(wait = false)
        val port = server.resolvedConnectors().first().port
        assertTrue(port > 0)
        server.stop(100, 500)
    }
}

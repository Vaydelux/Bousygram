package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAccessRegistryTest {
    @Test
    fun tokensAreDifferentForTheSameFile() {
        val first = StreamAccessRegistry.issueFile(17)
        val second = StreamAccessRegistry.issueFile(17)
        assertNotEquals(first, second)
        assertTrue(StreamAccessRegistry.authorizesFile(first, 17))
        assertTrue(StreamAccessRegistry.authorizesFile(second, 17))
    }

    @Test
    fun tokenIsScopedToItsFile() {
        val token = StreamAccessRegistry.issueFile(23)
        assertTrue(StreamAccessRegistry.authorizesFile(token, 23))
        assertFalse(StreamAccessRegistry.authorizesFile(token, 24))
    }

    @Test
    fun playlistTokenCannotAuthorizeAFile() {
        val token = StreamAccessRegistry.issuePlaylist()
        assertNotNull(token)
        assertTrue(StreamAccessRegistry.authorizesPlaylist(token))
        assertFalse(StreamAccessRegistry.authorizesFile(token, 23))
    }

    @Test
    fun missingOrUnknownTokensAreRejected() {
        assertFalse(StreamAccessRegistry.authorizesFile(null, 23))
        assertFalse(StreamAccessRegistry.authorizesFile("invalid-token", 23))
        assertFalse(StreamAccessRegistry.authorizesPlaylist("invalid-token"))
    }

    @Test
    fun revokedTokenCannotBeReused() {
        val token = StreamAccessRegistry.issueFile(99)
        assertTrue(StreamAccessRegistry.authorizesFile(token, 99))
        assertTrue(StreamAccessRegistry.revoke(token))
        assertFalse(StreamAccessRegistry.authorizesFile(token, 99))
        assertFalse(StreamAccessRegistry.revoke(token))
    }

    @Test
    fun localUrlCapabilityCanBeRevokedWithoutInvalidatingAnotherToken() {
        val token = StreamAccessRegistry.issueFile(321)
        val other = StreamAccessRegistry.issueFile(322)
        assertTrue(StreamAccessRegistry.revokeFromUrl("http://127.0.0.1:8080/stream?fileId=321&token=$token"))
        assertFalse(StreamAccessRegistry.authorizesFile(token, 321))
        assertTrue(StreamAccessRegistry.authorizesFile(other, 322))
        assertFalse(StreamAccessRegistry.revokeFromUrl("https://example.invalid/stream?fileId=321&token=$other"))
        assertTrue(StreamAccessRegistry.authorizesFile(other, 322))
    }
}

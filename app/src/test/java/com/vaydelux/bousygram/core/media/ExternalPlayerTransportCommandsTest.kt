package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.shouldRestorePlayIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlayerTransportCommandsTest {
    @Test fun remotePauseUpdatesUserIntentRatherThanPausingDecoderOnly() {
        var explicitlyPaused = false
        var decoderPlaying = true
        val commands = ExternalPlayerTransportCommands(
            requestPlay = { explicitlyPaused = false; decoderPlaying = true },
            requestPause = { explicitlyPaused = true; decoderPlaying = false },
            requestSeek = {}
        )
        commands.onPause()
        assertTrue(explicitlyPaused)
        assertFalse(decoderPlaying)
        assertFalse(shouldRestorePlayIntent(
            isReady = true, explicitlyPaused = explicitlyPaused,
            inForeground = true, castConnected = false, isPlaying = false
        ))
        commands.onPlay()
        assertFalse(explicitlyPaused)
        assertTrue(decoderPlaying)
    }

    @Test fun remoteAbsoluteSeekUsesGuardedRouteWithoutNegativeNativePosition() {
        val requests = mutableListOf<Long>()
        val commands = ExternalPlayerTransportCommands({}, {}, { requests += it })
        commands.onSeekTo(-23L)
        commands.onSeekTo(120_000L)
        assertEquals(listOf(0L, 120_000L), requests)
    }

    @Test fun remotePauseDuringBufferingDoesNotAccidentallyRequestPlay() {
        var paused = false
        var playRequests = 0
        val commands = ExternalPlayerTransportCommands(
            requestPlay = { playRequests++ },
            requestPause = { paused = true },
            requestSeek = {}
        )
        commands.onPause()
        assertTrue(paused)
        assertEquals(0, playRequests)
    }

}

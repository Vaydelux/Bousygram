package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LibVlcPlaybackPolicyTest {
    @Test fun ongoingPlaybackAndManualPauseStayReadyDuringBufferNotifications() {
        assertEquals(EnginePlaybackState.Ready, libVlcBufferState(true, true))
        assertEquals(EnginePlaybackState.Ready, libVlcBufferState(false, false))
        assertEquals(EnginePlaybackState.Ready, libVlcBufferState(false, true))
        assertEquals(EnginePlaybackState.Buffering, libVlcBufferState(true, false))
    }
}

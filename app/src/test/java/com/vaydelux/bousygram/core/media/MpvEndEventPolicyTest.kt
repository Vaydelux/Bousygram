package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvEndEventPolicyTest {
    @Test fun stopAndFileReplacementAreNotNaturalVideoCompletion() {
        assertFalse(shouldReportNativeMpvEnded(false))
    }

    @Test fun prematureNativeEndRequestsRecoveryInsteadOfSilentPausedReadyState() {
        assertTrue(shouldRetryUnexpectedMpvEnd(false, EnginePlaybackState.Ready))
        assertFalse(shouldRetryUnexpectedMpvEnd(true, EnginePlaybackState.Ready))
        assertFalse(shouldRetryUnexpectedMpvEnd(false, EnginePlaybackState.Buffering))
        assertFalse(shouldRetryUnexpectedMpvEnd(false, EnginePlaybackState.Idle))
    }

    @Test fun nativeEndRequiresObservedEof() {
        assertTrue(shouldReportNativeMpvEnded(true))
    }

    @Test fun onlyEventsFromAnActiveLoadedSourceCanMarkEofOrRecovery() {
        assertFalse(shouldProcessNativeMpvTerminalEvent(false, false))
        assertFalse(shouldProcessNativeMpvTerminalEvent(true, false))
        assertFalse(shouldProcessNativeMpvTerminalEvent(false, true))
        assertTrue(shouldProcessNativeMpvTerminalEvent(true, true))
    }
}

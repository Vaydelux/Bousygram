package com.vaydelux.bousygram.presentation.media

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekCompletionPolicyTest {
    @Test fun observingTargetDuringBufferingIsNotConfirmation() {
        assertEquals(SeekCompletion.Waiting, seekCompletion(true, true, 5_000L))
        assertEquals(SeekCompletion.TimedOut, seekCompletion(true, true, 30_000L))
    }

    @Test fun onlyReadyNearTargetPositionConfirmsSeek() {
        assertEquals(SeekCompletion.Confirmed, seekCompletion(false, true, 200L))
        assertEquals(SeekCompletion.Waiting, seekCompletion(false, false, 200L))
        assertEquals(SeekCompletion.TimedOut, seekCompletion(false, false, 30_000L))
    }

    @Test fun delayedDecoderRetryRunsOnlyWithCurrentForegroundPlaybackIntent() {
        fun eligible(
            sameQueue: Boolean = true,
            sameReload: Boolean = true,
            active: Boolean = true,
            foreground: Boolean = true,
            paused: Boolean = false,
            casting: Boolean = false,
            suppressed: Boolean = false
        ) = shouldRunDelayedPlaybackRetry(
            sameQueue, sameReload, active, foreground, paused, casting, suppressed
        )
        assertEquals(true, eligible())
        assertEquals(false, eligible(sameQueue = false))
        assertEquals(false, eligible(sameReload = false))
        assertEquals(false, eligible(active = false))
        assertEquals(false, eligible(foreground = false))
        assertEquals(false, eligible(paused = true))
        assertEquals(false, eligible(casting = true))
        assertEquals(false, eligible(suppressed = true))
    }

    @Test fun seekDeadlineIsRespectedAndNeverSignalsFalseSuccess() {
        assertEquals(SeekCompletion.Waiting, seekCompletion(true, false, 999L, 1_000L))
        assertEquals(SeekCompletion.TimedOut, seekCompletion(true, false, 1_000L, 1_000L))
    }
}

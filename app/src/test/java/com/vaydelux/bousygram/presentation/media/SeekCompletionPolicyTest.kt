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

    @Test fun seekDeadlineIsRespectedAndNeverSignalsFalseSuccess() {
        assertEquals(SeekCompletion.Waiting, seekCompletion(true, false, 999L, 1_000L))
        assertEquals(SeekCompletion.TimedOut, seekCompletion(true, false, 1_000L, 1_000L))
    }
}

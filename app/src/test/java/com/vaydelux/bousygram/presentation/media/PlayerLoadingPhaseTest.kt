package com.vaydelux.bousygram.presentation.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLoadingPhaseTest {
    @Test fun initialOpenShowsSkeleton() {
        assertEquals(PlayerLoadingPhase.OPENING, playerLoadingPhase(true, false, true, null))
    }
    @Test fun reconnectTakesPriorityOverInitialOpenAndBuffering() {
        assertEquals(PlayerLoadingPhase.RECONNECTING, playerLoadingPhase(true, true, true, null))
        assertEquals(PlayerLoadingPhase.RECONNECTING, playerLoadingPhase(false, true, true, null))
    }
    @Test fun normalRebufferOnlyShowsSmallStatusBanner() {
        assertEquals(PlayerLoadingPhase.BUFFERING, playerLoadingPhase(false, false, true, null))
    }
    @Test fun errorRemovesAllLoadingOverlaysSoRetryRemainsAccessible() {
        assertEquals(PlayerLoadingPhase.NONE, playerLoadingPhase(true, true, true, "Playback failed"))
    }
    @Test fun loadingCopyNeverInventsDownloadProgress() {
        val opening = playerLoadingCopy(PlayerLoadingPhase.OPENING, 0)
        assertEquals("Preparing playback", opening.title)
        assertEquals("Connecting to your Telegram video", opening.detail)
        assertEquals("Buffering video", playerLoadingCopy(PlayerLoadingPhase.BUFFERING, 0).title)
    }
    @Test fun reconnectCopyReflectsActualAttemptCount() {
        assertEquals("Restoring your connection", playerLoadingCopy(PlayerLoadingPhase.RECONNECTING, 0).detail)
        assertEquals("Restoring playback · attempt 2", playerLoadingCopy(PlayerLoadingPhase.RECONNECTING, 2).detail)
    }
    @Test fun idleCopyIsEmpty() {
        assertEquals(PlayerLoadingCopy("", ""), playerLoadingCopy(PlayerLoadingPhase.NONE, 0))
    }
    @Test fun readyAndPausedStatesShowNoLoadingOverlay() {
        assertEquals(PlayerLoadingPhase.NONE, playerLoadingPhase(false, false, false, null))
    }
}

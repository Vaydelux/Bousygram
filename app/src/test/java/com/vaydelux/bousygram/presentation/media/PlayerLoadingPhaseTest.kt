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
    @Test fun readyAndPausedStatesShowNoLoadingOverlay() {
        assertEquals(PlayerLoadingPhase.NONE, playerLoadingPhase(false, false, false, null))
    }
}

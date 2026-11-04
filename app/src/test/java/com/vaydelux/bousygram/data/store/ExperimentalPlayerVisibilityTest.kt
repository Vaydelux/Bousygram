package com.vaydelux.bousygram.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalPlayerVisibilityTest {
    @Test fun normalApkCannotOfferExperimentalVlc() {
        val choices = availablePlaybackEngines()
        assertEquals(4, choices.size)
        assertTrue(SelectedPlayerEngine.LIBVLC in choices)
        assertTrue(SelectedPlayerEngine.MPV in choices)
    }

    @Test fun optInLabListsVlcWithoutChangingAutoPreference() {
        val choices = availablePlaybackEngines()
        assertEquals(4, choices.size)
        assertEquals(SelectedPlayerEngine.LIBVLC, parseSavedPlayerEngine("LIBVLC_EXPERIMENTAL"))
        assertEquals(SelectedPlayerEngine.AUTO, parseSavedPlayerEngine("invalid"))
        assertEquals(SelectedPlayerEngine.MPV, nextManualPlayerEngine(SelectedPlayerEngine.ExoPlayer))
        assertEquals(SelectedPlayerEngine.LIBVLC, nextManualPlayerEngine(SelectedPlayerEngine.MPV))
        assertEquals(SelectedPlayerEngine.ExoPlayer, nextManualPlayerEngine(SelectedPlayerEngine.LIBVLC))
        assertEquals(SelectedPlayerEngine.AUTO, choices.first())
    }
}

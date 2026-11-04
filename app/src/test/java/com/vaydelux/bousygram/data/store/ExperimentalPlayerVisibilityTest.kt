package com.vaydelux.bousygram.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalPlayerVisibilityTest {
    @Test fun normalApkCannotOfferExperimentalVlc() {
        val choices = availablePlaybackEngines(false)
        assertEquals(3, choices.size)
        assertFalse(SelectedPlayerEngine.LIBVLC_EXPERIMENTAL in choices)
        assertTrue(SelectedPlayerEngine.MPV in choices)
    }

    @Test fun optInLabListsVlcWithoutChangingAutoPreference() {
        val choices = availablePlaybackEngines(true)
        assertEquals(4, choices.size)
        assertTrue(SelectedPlayerEngine.LIBVLC_EXPERIMENTAL in choices)
        assertEquals(SelectedPlayerEngine.AUTO, choices.first())
    }
}

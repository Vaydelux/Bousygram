package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class TelegramCastCompatibilityTest {
    @Test fun onlyAdmitsMp4CandidatesAndRejectsKnownConflictingContainers() {
        assertNull(TelegramCastCompatibility.rejection("video/mp4", "movie"))
        assertNull(TelegramCastCompatibility.rejection("application/octet-stream", "movie.M4V"))
        assertTrue(TelegramCastCompatibility.rejection("video/mp4", "movie.MKV")!!.contains("MKV"))
        assertTrue(TelegramCastCompatibility.rejection("video/mp4", "movie.TS")!!.contains("TS"))
        assertTrue(TelegramCastCompatibility.rejection("video/mp2t", null)!!.contains("TS"))
        assertNotNull(TelegramCastCompatibility.rejection("video/webm", "movie.webm"))
        assertNotNull(TelegramCastCompatibility.rejection(null, null))
    }
}

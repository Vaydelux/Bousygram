package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class TelegramCastCompatibilityTest {
    @Test fun mkvRequiresRemuxButTsAlwaysRejects() {
        assertTrue(TelegramCastCompatibility.requiresRemux("video/mp4", "clip.MKV"))
        assertTrue(TelegramCastCompatibility.requiresRemux("video/x-matroska", null))
        assertFalse(TelegramCastCompatibility.requiresRemux("video/x-matroska", "clip.ts"))
        assertFalse(TelegramCastCompatibility.requiresRemux("video/mp2t", "clip.mkv"))
        assertFalse(TelegramCastCompatibility.requiresRemux("video/mp4", "clip.mp4"))
    }

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

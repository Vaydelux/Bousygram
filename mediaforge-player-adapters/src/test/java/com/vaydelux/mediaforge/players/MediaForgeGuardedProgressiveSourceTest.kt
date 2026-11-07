package com.vaydelux.mediaforge.players

import com.vaydelux.mediaforge.core.StreamCandidate
import com.vaydelux.mediaforge.core.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM contract tests: no remote provider or FUSE device is accessed.
 * Android filesystem/range playback needs emulator and physical qualification.
 */
class MediaForgeGuardedProgressiveSourceTest {
    @Test fun parsesOnlyExplicitStableByteRanges() {
        assertEquals(Triple(0L, 0L, 12345L), parseContentRange("bytes 0-0/12345"))
        assertEquals(Triple(200L, 399L, 500L), parseContentRange("bytes 200-399/500"))
        assertNull(parseContentRange("bytes */500"))
        assertNull(parseContentRange("bytes 0-0/*"))
        assertNull(parseContentRange("unexpected"))
        assertNull(parseContentRange(null))
        assertNull(parseContentRange("bytes 1-1/99999999999999999999999"))
    }

    @Test fun guardedLibVlcSourceOnlyAcceptsProgressiveFormats() {
        val url = "https://video.example.org/fixture"
        fun candidate(format: StreamFormat) = StreamCandidate(url, format, "video.example.org")
        assertTrue(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.MP4)))
        assertTrue(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.WEBM)))
        assertFalse(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.HLS)))
        assertFalse(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.DASH)))
        assertFalse(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.MPEG_TS)))
    }
}

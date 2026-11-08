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

    @Test fun alignedWindowsAvoidOverlappingRequestsAcrossSmallSeeks() {
        val size = MEDIAFORGE_RANGE_WINDOW_BYTES
        assertEquals(0L, alignedRangeWindowStart(0L))
        assertEquals(0L, alignedRangeWindowStart(4096L))
        assertEquals(0L, alignedRangeWindowStart(size - 1L))
        assertEquals(size, alignedRangeWindowStart(size))
        assertEquals(size, alignedRangeWindowStart(size + 128L))
        assertEquals(size * 2L, alignedRangeWindowStart(size * 2L + 10L))
        // Very large offsets must not overflow when computing an aligned start.
        assertTrue(alignedRangeWindowStart(Long.MAX_VALUE) <= Long.MAX_VALUE)
    }

    @Test fun strongEtagsOnlyAndRangeIdentity() {
        assertEquals("\"media-v1\"", parseStrongEtag("\"media-v1\""))
        assertNull(parseStrongEtag("\"with spaces\""))
        assertNull(parseStrongEtag("W/\"media-v1\""))
        assertNull(parseStrongEtag("\"bad\"quoted\""))
        assertEquals("\"\"", parseStrongEtag("\"\""))
        assertNull(parseStrongEtag("media-v1"))
        assertNull(parseStrongEtag("\"bad\r\nheader\""))
        assertNull(parseStrongEtag(null))
        val identity = RemoteRangeIdentity(12_345L, parseStrongEtag("\"media-v1\""))
        assertEquals(12_345L, identity.length)
        assertEquals("\"media-v1\"", identity.strongEtag)
    }

    @Test fun byteRangeResponsesNeverMixMediaVersions() {
        val identity = RemoteRangeIdentity(1024L, "\"stable-v1\"")
        assertTrue(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/1024", "\"stable-v1\""))
        // A 206 may omit its ETag while still respecting the If-Range request.
        assertTrue(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/1024", null))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/1024", "\"changed-v2\""))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, "bytes 1-256/1024", "\"stable-v1\""))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/2048", "\"stable-v1\""))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, null, "\"stable-v1\""))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/1024", "W/\"stable-v1\""))
        val noTag = RemoteRangeIdentity(1024L, null)
        assertTrue(isConsistentRangeResponse(noTag, 0L, 255L, "bytes 0-255/1024", null))
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

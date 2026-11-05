package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportStreamThumbnailPolicyTest {
    @Test fun transportStreamDocumentsAreRecognizedRegardlessOfCase() {
        assertTrue(VideoFormatSupport.isTransportStream("video.TS", "application/octet-stream"))
        assertTrue(VideoFormatSupport.isTransportStream("movie.M2TS", null))
        assertTrue(VideoFormatSupport.isTransportStream("movie.mts", null))
        assertTrue(VideoFormatSupport.isTransportStream(null, "video/mp2t; charset=binary"))
        assertTrue(VideoFormatSupport.isTransportStream(null, "video/mpegts"))
        assertTrue(VideoFormatSupport.isTransportStream(null, "video/x-mpegts; codecs=avc1"))
        assertTrue(VideoFormatSupport.isTransportStream(null, "VIDEO/VND.DLNA.MPEG-TTS; charset=binary"))
    }
    @Test fun completedTsFileMayBeProbedAndSparseFileIsNeverRead() {
        val can = VideoFormatSupport::canProbeLocalTsFrame
        assertTrue(can(true, 0L, 0L, 188L * 100L))
        assertFalse(can(false, 0L, 1_000L, 10_000_000L))
        assertFalse(can(false, 1_000_000L, 4_000_000L, 10_000_000L))
        assertFalse(can(false, 0L, 4_000_000L, 1_000_000L))
        assertTrue(can(false, 0L, 4_000_000L, 4_000_000L))
    }

    @Test fun mp4AndOtherFormatsDoNotTriggerTsThumbnailExtraction() {
        assertFalse(VideoFormatSupport.isTransportStream("video.mp4", "video/mp4"))
        assertFalse(VideoFormatSupport.isTransportStream(null, null))
    }
}

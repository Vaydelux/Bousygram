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
    }
    @Test fun mp4AndOtherFormatsDoNotTriggerTsThumbnailExtraction() {
        assertFalse(VideoFormatSupport.isTransportStream("video.mp4", "video/mp4"))
        assertFalse(VideoFormatSupport.isTransportStream(null, null))
    }
}

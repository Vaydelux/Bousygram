package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExoTrackIdentityTest {
    @Test fun sameLocalIndexInDifferentGroupsMustNotSelectTheWrongTrack() {
        val spanish = exoTrackUiId(4, 0)
        val english = exoTrackUiId(3, 0)
        assertNotEquals(spanish, english)
        assertEquals(4 to 0, decodeExoTrackUiId(spanish))
        assertEquals(3 to 0, decodeExoTrackUiId(english))
    }

    @Test fun multipleTracksWithinSameMediaGroupRemainDistinct() {
        assertEquals(2 to 13, decodeExoTrackUiId(exoTrackUiId(2, 13)))
        assertNotEquals(exoTrackUiId(2, 13), exoTrackUiId(2, 14))
        assertNull(decodeExoTrackUiId(-1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun impossibleLargeTrackIndexIsRejected() {
        exoTrackUiId(0, 65536)
    }
}

package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcVideoLayoutPolicyTest {
    @Test fun fitIsNativeAutoscaleEvenBeforeVideoTrackIsKnown() {
        assertEquals(
            LibVlcVideoLayout(null, 0f),
            libVlcVideoLayout(LibVlcResizeMode.FIT, 0, 0, 0, 0)
        )
    }

    @Test fun zoomCenterCropsWideVideoOnTallViewport() {
        val layout = libVlcVideoLayout(LibVlcResizeMode.ZOOM,
            windowWidth = 1080, windowHeight = 1920,
            videoWidth = 1920, videoHeight = 1080)
        assertEquals(null, layout?.aspectRatio)
        assertEquals(1920f / 1080f, layout!!.scale, 0.001f)
    }

    @Test fun zoomCenterCropsTallVideoOnWideViewport() {
        val layout = libVlcVideoLayout(LibVlcResizeMode.ZOOM,
            windowWidth = 1920, windowHeight = 1080,
            videoWidth = 1080, videoHeight = 1920)
        assertEquals(1920f / 1080f, layout!!.scale, 0.001f)
    }

    @Test fun zoomRespectsNonSquarePixels() {
        val layout = libVlcVideoLayout(LibVlcResizeMode.ZOOM,
            windowWidth = 1920, windowHeight = 1080,
            videoWidth = 720, videoHeight = 576,
            sarNumerator = 16, sarDenominator = 15)
        assertEquals(2.5f, layout!!.scale, 0.001f)
    }

    @Test fun stretchMatchesViewportWithoutInventingVideoDimensions() {
        assertEquals(
            LibVlcVideoLayout("1920:1080", 0f),
            libVlcVideoLayout(LibVlcResizeMode.STRETCH, 1920, 1080, 0, 0)
        )
    }

    @Test fun invalidSurfaceOrTrackDefersNonFitMode() {
        assertNull(libVlcVideoLayout(LibVlcResizeMode.ZOOM, 0, 1080, 1920, 1080))
        assertNull(libVlcVideoLayout(LibVlcResizeMode.ZOOM, 1920, 1080, 0, 0))
        assertNull(libVlcVideoLayout(LibVlcResizeMode.ZOOM, 1920, 1080, 1920, 1080, 0, 1))
        assertNull(libVlcVideoLayout(LibVlcResizeMode.STRETCH, 1920, 0, 0, 0))
    }

    @Test fun sameOutputIsValueEqualAndCanSkipRedundantNativeCalls() {
        val one = libVlcVideoLayout(LibVlcResizeMode.ZOOM, 1920, 1080, 1280, 720)
        val two = libVlcVideoLayout(LibVlcResizeMode.ZOOM, 1920, 1080, 1280, 720)
        val resized = libVlcVideoLayout(LibVlcResizeMode.ZOOM, 1280, 720, 1280, 720)
        assertEquals(one, two)
        assertTrue(one != resized)
    }
}

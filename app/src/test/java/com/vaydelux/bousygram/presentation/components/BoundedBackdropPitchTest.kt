package com.vaydelux.bousygram.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedBackdropPitchTest {
    @Test fun keepsOriginalSpacingOnSmallPreviews() {
        assertEquals(14f, boundedBackdropPitch(180f, 60f, 14f, 100), 0.001f)
    }

    @Test fun capsCellCountOnLargeTvAndPhoneSurfaces() {
        for ((width, height) in listOf(3840f to 2160f, 2160f to 3840f, 1920f to 1080f)) {
            val spacing = boundedBackdropPitch(width, height, 28f, 700)
            assertTrue(spacing >= 28f)
            assertTrue((width / spacing) * (height / spacing) <= 700.001f)
        }
    }

    @Test fun degenerateViewportsStaySafe() {
        assertEquals(1f, boundedBackdropPitch(0f, 0f, 0f, 100), 0f)
        assertThrows(IllegalArgumentException::class.java) {
            boundedBackdropPitch(100f, 100f, 10f, 0)
        }
    }
}

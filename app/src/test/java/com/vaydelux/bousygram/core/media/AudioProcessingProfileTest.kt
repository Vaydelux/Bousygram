package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProcessingProfileTest {
    @Test fun originalAndSmartVolumeAreOffByDefault() {
        val profile = AudioProcessingProfile()
        assertEquals(AudioEqualizerPreset.ORIGINAL, profile.preset)
        assertEquals(SmartVolumeMode.OFF, profile.smartVolume)
        assertTrue(profile.sanitizedGains.all { it == 0f })
        assertEquals(0f, profile.preampDb, 0.001f)
    }

    @Test fun editedCustomGainClampsAndCanBeReset() {
        val p = AudioProcessingProfile().editedBand(4, 999f)
        assertEquals(AudioEqualizerPreset.CUSTOM, p.preset)
        assertEquals(6f, p.sanitizedGains[4], 0.001f)
        assertEquals(-6f, p.preampDb, 0.001f)
        val ignored = p.editedBand(-1, 2f).editedBand(10, 2f).editedBand(4, Float.NaN)
        assertEquals(p.sanitizedGains, ignored.sanitizedGains)
        assertEquals(List(10) { 0f }, p.resetCustom().sanitizedGains)
    }

    @Test fun customFrequencyInterpolationAndClippingBoundsAreDeterministic() {
        val profile = AudioProcessingProfile(preset = AudioEqualizerPreset.CUSTOM)
            .editedBand(0, 6f)
            .editedBand(1, -6f)
        assertEquals(6f, profile.gainDbAt(60f), 0.001f)
        assertEquals(-6f, profile.gainDbAt(125f), 0.001f)
        assertEquals(0f, profile.gainDbAt(Float.POSITIVE_INFINITY), 0.001f)
        assertTrue(profile.gainDbAt(80f) in -6f..6f)
        for (freq in AudioProcessingProfile.CUSTOM_FREQUENCIES_HZ) {
            assertTrue(safeAudioFxBandMillibels(profile, freq, -1_500, 1_500) <= 0)
        }
    }

    @Test fun compactLayoutExposesFiveBandsButKeepsTenUnderlyingValues() {
        val five = CustomEqualizerLayout.FIVE.bandIndices
        val ten = CustomEqualizerLayout.TEN.bandIndices
        assertEquals(5, five.size)
        assertEquals(10, ten.size)
        assertEquals(10, five.toSet().plus(ten).size)
        assertEquals(0, five.first())
        assertEquals(9, five.last())
    }

    @Test fun arm32KeepsHeavyDynaudnormOffWhileRetainingLightCompression() {
        assertNull(smartVolumeMpvGraph(SmartVolumeMode.OFF, false))
        assertNull(smartVolumeMpvGraph(SmartVolumeMode.AUTO_LEVEL, true))
        assertTrue(smartVolumeMpvGraph(SmartVolumeMode.NIGHT, true)!!.contains("alimiter"))
        assertTrue(smartVolumeMpvGraph(SmartVolumeMode.DIALOGUE_BOOST, true)!!.contains("acompressor"))
        assertFalse(smartVolumeMpvGraph(SmartVolumeMode.NIGHT, true)!!.contains("dynaudnorm"))
        assertTrue(smartVolumeMpvGraph(SmartVolumeMode.AUTO_LEVEL, false)!!.contains("dynaudnorm"))
        assertTrue(SmartVolumeMode.entries.size >= 4)
    }
}

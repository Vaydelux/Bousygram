package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEffectStatusTest {
    @Test fun filterVerificationRefusesToClaimSuccessWithoutNativeReadBack() {
        assertEquals(FilterVerification.UNVERIFIABLE, verifyNamedMpvAudioFilter(null, "bousy_eq"))
        assertEquals(FilterVerification.ABSENT, verifyNamedMpvAudioFilter("", "bousy_eq"))
        assertEquals(FilterVerification.ABSENT, verifyNamedMpvAudioFilter("@bousy_eq_extra:lavfi=[x]", "bousy_eq"))
        assertEquals(FilterVerification.PRESENT, verifyNamedMpvAudioFilter("@bousy_eq:lavfi=[x]", "bousy_eq"))
        assertEquals(FilterVerification.PRESENT, verifyNamedMpvAudioFilter("lavfi=[x],@bousy_eq:lavfi=[y]", "bousy_eq"))
    }

    @Test fun audioFxHeadroomDoesNotAddPositiveBandGain() {
        for (preset in AudioEqualizerPreset.entries) {
            for (frequency in listOf(60f, 170f, 400f, 1_000f, 2_400f, 6_000f, 12_000f)) {
                val gain = safeAudioFxBandMillibels(preset, frequency, -1_500, 1_500).toInt()
                assertTrue("Positive AudioFX band gain on ${preset.name}: $gain", gain <= 0)
                assertTrue(gain >= -1_500)
            }
        }
        assertEquals(0, safeAudioFxBandMillibels(AudioEqualizerPreset.ORIGINAL, 1_000f, -1_200, 1_200).toInt())
        assertEquals(-150, safeAudioFxBandMillibels(AudioEqualizerPreset.CINEMA, 1_000f, -1_200, 1_200).toInt())
    }
}

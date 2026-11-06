package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputLifecyclePolicyTest {
    private val requested = AudioProcessingProfile(
        preset = AudioEqualizerPreset.DIALOGUE,
        smartVolume = SmartVolumeMode.NIGHT
    )

    @Test fun outputStopLeavesEnabledEffectsPendingNotApplied() {
        assertEquals(AudioEffectStatus.PENDING, audioEffectStatusWithoutOutput(true))
        assertEquals(AudioEffectStatus.BYPASSED, audioEffectStatusWithoutOutput(false))
    }

    @Test fun onlyPendingEffectsRetryWhenSessionIsUsable() {
        assertTrue(shouldRetryPendingAudioEffectsOnReady(
            requested, AudioEffectStatus.PENDING, AudioEffectStatus.APPLIED, 24
        ))
        assertTrue(shouldRetryPendingAudioEffectsOnReady(
            requested, AudioEffectStatus.APPLIED, AudioEffectStatus.PENDING, 24
        ))
        assertFalse(shouldRetryPendingAudioEffectsOnReady(
            requested, AudioEffectStatus.PENDING, AudioEffectStatus.PENDING, 0
        ))
        assertFalse(shouldRetryPendingAudioEffectsOnReady(
            requested, AudioEffectStatus.PENDING, AudioEffectStatus.PENDING, -1
        ))
    }

    @Test fun readyDoesNotRecreateAppliedOrUnsupportedEffects() {
        assertFalse(shouldRetryPendingAudioEffectsOnReady(
            requested, AudioEffectStatus.APPLIED, AudioEffectStatus.APPLIED, 24
        ))
        assertFalse(shouldRetryPendingAudioEffectsOnReady(
            requested, AudioEffectStatus.UNSUPPORTED, AudioEffectStatus.UNSUPPORTED, 24
        ))
    }

    @Test fun offProfileDoesNotRetryEvenIfOldStatusesArePending() {
        assertFalse(shouldRetryPendingAudioEffectsOnReady(
            AudioProcessingProfile(), AudioEffectStatus.PENDING,
            AudioEffectStatus.PENDING, 24
        ))
    }

    @Test fun singleEffectRequestsRetryOnlyTheirOwnPendingState() {
        val eqOnly = requested.copy(smartVolume = SmartVolumeMode.OFF)
        assertFalse(shouldRetryPendingAudioEffectsOnReady(
            eqOnly, AudioEffectStatus.APPLIED, AudioEffectStatus.PENDING, 30
        ))
        val volumeOnly = requested.copy(preset = AudioEqualizerPreset.ORIGINAL)
        assertFalse(shouldRetryPendingAudioEffectsOnReady(
            volumeOnly, AudioEffectStatus.PENDING, AudioEffectStatus.APPLIED, 30
        ))
    }
}

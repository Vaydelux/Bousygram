package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputTopologyPolicyTest {
    private val enabled = AudioProcessingProfile(
        preset = AudioEqualizerPreset.DIALOGUE,
        smartVolume = SmartVolumeMode.NIGHT
    )

    @Test fun deviceOrderDoesNotTriggerUnnecessaryReconfiguration() {
        val a = AudioOutputDeviceKey(3, 2)
        val b = AudioOutputDeviceKey(5, 8)
        assertFalse(didAudioOutputTopologyChange(setOf(a, b), setOf(b, a)))
        assertFalse(didAudioOutputTopologyChange(emptySet(), emptySet()))
        assertTrue(didAudioOutputTopologyChange(setOf(a), setOf(a, b)))
        assertTrue(didAudioOutputTopologyChange(setOf(a, b), setOf(b)))
        assertTrue(didAudioOutputTopologyChange(setOf(a), setOf(AudioOutputDeviceKey(9, 2))))
    }

    @Test fun idleOrOriginalProfilesNeverRefreshNativeEffects() {
        assertEquals(AudioTopologyRecoveryAction.IGNORE,
            audioTopologyRecoveryAction(enabled, false, true, 16))
        assertEquals(AudioTopologyRecoveryAction.IGNORE,
            audioTopologyRecoveryAction(AudioProcessingProfile(), true, true, 16))
    }

    @Test fun activeReadySessionMayRefreshWithoutRestartingPlayback() {
        assertEquals(AudioTopologyRecoveryAction.REAPPLY,
            audioTopologyRecoveryAction(enabled, true, true, 16))
        assertEquals(AudioTopologyRecoveryAction.REAPPLY,
            audioTopologyRecoveryAction(enabled.copy(smartVolume = SmartVolumeMode.OFF),
                true, true, 16))
    }

    @Test fun missingAudioSessionOrBufferingDefersReconfiguration() {
        assertEquals(AudioTopologyRecoveryAction.WAIT_FOR_OUTPUT,
            audioTopologyRecoveryAction(enabled, true, false, 16))
        assertEquals(AudioTopologyRecoveryAction.WAIT_FOR_OUTPUT,
            audioTopologyRecoveryAction(enabled, true, true, 0))
        assertEquals(AudioTopologyRecoveryAction.WAIT_FOR_OUTPUT,
            audioTopologyRecoveryAction(enabled, true, true, -1))
    }
}

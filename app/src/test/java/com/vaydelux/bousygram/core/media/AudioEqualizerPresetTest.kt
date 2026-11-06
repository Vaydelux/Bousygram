package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEqualizerPresetTest {
    @Test fun originalPresetLeavesSoundUntouchedByDefault() {
        assertFalse(AudioEqualizerPreset.ORIGINAL.isEnabled)
        assertEquals(AudioEqualizerPreset.ORIGINAL, AudioEqualizerPreset.fromStored(null))
        assertEquals(AudioEqualizerPreset.ORIGINAL, AudioEqualizerPreset.fromStored("INVALID"))
        assertEquals(AudioEqualizerPreset.ORIGINAL, AudioEqualizerPreset.fromStored("cinema"))
        assertEquals(0f, AudioEqualizerPreset.ORIGINAL.gainDbAt(60f), 0.001f)
        assertEquals(0f, AudioEqualizerPreset.ORIGINAL.gainDbAt(6_000f), 0.001f)
        assertEquals(0f, AudioEqualizerPreset.ORIGINAL.preampDb, 0.001f)
    }

    @Test fun premiumPresetsHaveConservativeFiniteGainsAndHeadroom() {
        assertTrue(AudioEqualizerPreset.entries.size >= 12)
        AudioEqualizerPreset.entries.forEach { preset ->
            for (hz in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 20f, 60f, 500f, 1_000f, 10_000f, 20_000f)) {
                val db = preset.gainDbAt(hz)
                assertTrue("Non-finite gain in ${preset.name}", db.isFinite())
                assertTrue(db in -4f..3f)
            }
            assertTrue(preset.preampDb in -3f..0f)
            assertEquals(preset, AudioEqualizerPreset.fromStored(preset.name))
        }
    }

    @Test fun dialoguePresetReducesRumbleAndEmphasizesVoice() {
        val dialogue = AudioEqualizerPreset.DIALOGUE
        assertTrue(dialogue.gainDbAt(60f) < 0f)
        assertTrue(dialogue.gainDbAt(2_400f) > 0f)
        assertTrue(dialogue.gainDbAt(800f) < dialogue.gainDbAt(2_400f))
    }

    @Test fun frequencyInterpolationRemainsBetweenNeighboringBands() {
        val left = AudioEqualizerPreset.CINEMA.gainDbAt(170f)
        val right = AudioEqualizerPreset.CINEMA.gainDbAt(400f)
        val middle = AudioEqualizerPreset.CINEMA.gainDbAt(260f)
        assertTrue(middle in minOf(left, right)..maxOf(left, right))
    }

    @Test fun androidAudioFxConvertsDbToClampedMillibels() {
        assertEquals(150, audioEqMillibels(AudioEqualizerPreset.CINEMA, 60f, -1_200, 1_200).toInt())
        assertEquals(80, audioEqMillibels(AudioEqualizerPreset.CINEMA, 60f, -80, 80).toInt())
        assertEquals(-100, audioEqMillibels(AudioEqualizerPreset.DIALOGUE, 60f, -100, 100).toInt())
        assertEquals(0, audioEqMillibels(AudioEqualizerPreset.ORIGINAL, 60f, -200, 200).toInt())
    }
}

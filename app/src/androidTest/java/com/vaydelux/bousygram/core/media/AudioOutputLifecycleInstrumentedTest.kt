package com.vaydelux.bousygram.core.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-safe lifecycle checks; no Telegram account, media or audio output needed. */
@RunWith(AndroidJUnit4::class)
class AudioOutputLifecycleInstrumentedTest {
    @Test
    fun stoppingExoPlayerPreservesSmartVolumeButNeverRestoresRetiredEq() {
        // Media3 enforces access from its application Looper (main by default).
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val engine = ExoPlayerEngine.create(context)
            // The deliberately stale DIALOGUE preset must never enable a
            // native equalizer. Smart Volume is a separate supported feature.
            val profile = AudioProcessingProfile(
                preset = AudioEqualizerPreset.DIALOGUE,
                smartVolume = SmartVolumeMode.NIGHT
            )
            try {
                engine.applyAudioProcessing(profile)
                assertEquals(AudioEffectStatus.BYPASSED, engine.audioEqualizerStatus.value)
                engine.stop()
                assertEquals(AudioEffectStatus.BYPASSED, engine.audioEqualizerStatus.value)
                assertEquals(AudioEffectStatus.PENDING, engine.smartVolumeStatus.value)
                // Repeated stop/release must not create or retain a stale effect.
                engine.stop()
                assertEquals(AudioEffectStatus.BYPASSED, engine.audioEqualizerStatus.value)
            } finally {
                engine.release()
            }
            assertEquals(AudioEffectStatus.BYPASSED, engine.audioEqualizerStatus.value)
            assertEquals(AudioEffectStatus.BYPASSED, engine.smartVolumeStatus.value)
            assertFalse(engine.applyAudioProcessing(profile))
        }
    }

    @Test
    fun defaultOffProcessingRemainsBypassedAfterStop() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val engine = ExoPlayerEngine.create(context)
            try {
                engine.applyAudioProcessing(AudioProcessingProfile())
                engine.stop()
                assertEquals(AudioEffectStatus.BYPASSED, engine.audioEqualizerStatus.value)
                assertEquals(AudioEffectStatus.BYPASSED, engine.smartVolumeStatus.value)
            } finally {
                engine.release()
            }
        }
    }
}

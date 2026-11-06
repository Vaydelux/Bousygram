package com.vaydelux.bousygram.data.store

import com.vaydelux.bousygram.core.media.AudioEqualizerPreset
import com.vaydelux.bousygram.core.media.CustomEqualizerLayout
import com.vaydelux.bousygram.core.media.SmartVolumeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioSettingsImportPolicyTest {
    @Test fun validatesCustomCurveAndSmartMode() {
        val settings = SettingsImportPolicy.parse(
            """{"version":1,"audioEqPreset":"CUSTOM",
              "audioCustomBands":[0,1,2,3,4,5,6,-6,-4,-2],
              "audioCustomLayout":"TEN","audioSmartVolume":"NIGHT"}""",
            null
        )
        assertEquals(AudioEqualizerPreset.CUSTOM, settings.audioEqualizer)
        assertEquals(CustomEqualizerLayout.TEN, settings.audioCustomLayout)
        assertEquals(SmartVolumeMode.NIGHT, settings.audioSmartVolume)
        assertEquals(10, settings.audioCustomGains?.size)
    }

    @Test fun absentAudioFieldsPreservePreviousPreferences() {
        val settings = SettingsImportPolicy.parse("""{"version":1,"safeMode":false}""", null)
        assertNull(settings.audioEqualizer)
        assertNull(settings.audioCustomGains)
        assertNull(settings.audioCustomLayout)
        assertNull(settings.audioSmartVolume)
    }

    @Test fun rejectsOutOfRangeAndIncompleteCustomCurves() {
        for (bands in listOf("[0,0]", "[0,0,0,0,0,0,0,0,0,99]", "null")) {
            assertThrows(IllegalArgumentException::class.java) {
                SettingsImportPolicy.parse("""{"audioCustomBands":$bands}""", null)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SettingsImportPolicy.parse("""{"audioSmartVolume":"EXPERIMENTAL"}""", null)
        }
    }
}

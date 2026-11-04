package com.vaydelux.bousygram.presentation.media

import android.content.Context
import androidx.media3.ui.SubtitleView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.core.media.SubtitleAppearance
import com.vaydelux.bousygram.core.media.SubtitleColorPreset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual Media3 caption APIs without requiring private Telegram data. */
@RunWith(AndroidJUnit4::class)
class SubtitleAppearanceInstrumentedTest {
    @Test fun allCaptionPresetsCanBeAppliedOnAndroidMainThread() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context: Context = inst.targetContext
        inst.runOnMainSync {
            val subtitles = SubtitleView(context)
            SubtitleColorPreset.entries.forEach { preset ->
                applyTextSubtitleStyle(subtitles, SubtitleAppearance(125, preset))
            }
            assertEquals(3, SubtitleColorPreset.entries.size)
        }
    }
}

package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.BousygramPlaybackPalette
import com.vaydelux.bousygram.presentation.theme.rememberBousygramPlaybackPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline, account-free test: changing Appearance propagates to both players. */
@RunWith(AndroidJUnit4::class)
class PlaybackThemeParityInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allSixteenThemesUpdateSharedPlaybackPalette() {
        val selected = mutableStateOf(BousygramThemePreset.DARK)
        var current: BousygramPlaybackPalette? = null

        compose.setContent {
            BousygramTheme(preset = selected.value) {
                val playback = rememberBousygramPlaybackPalette()
                SideEffect { current = playback }
                Column { Text("Theme: ${selected.value.displayName}") }
            }
        }

        val activeColors = mutableSetOf<Color>()
        for (preset in BousygramThemePreset.entries) {
            compose.runOnIdle { selected.value = preset }
            compose.waitForIdle()
            compose.runOnIdle {
                val playback = requireNotNull(current)
                activeColors.add(playback.played)
                assertTrue(playback.played.alpha > 0.95f)
                assertTrue(playback.thumb.alpha > 0.95f)
                if (preset.isLightPreset) {
                    // Distinguishable dark ink rather than invisible white-on-white.
                    assertTrue(playback.unplayed.red < 0.75f)
                } else {
                    assertTrue(playback.unplayed.red > 0.85f)
                }
            }
        }
        assertEquals(BousygramThemePreset.entries.size, activeColors.size)
    }
}

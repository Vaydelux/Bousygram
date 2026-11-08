package com.vaydelux.bousygram.presentation.extras

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.mediaforge.core.CaptionCandidate
import com.vaydelux.mediaforge.core.CaptionFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** TV subtitle picker input and selection semantics without any network access. */
@RunWith(AndroidJUnit4::class)
class MediaForgeSubtitlePickerInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val english = CaptionCandidate(
        url = "https://media.example.invalid/en.vtt",
        format = CaptionFormat.VTT,
        host = "media.example.invalid",
        language = "en",
        label = "English"
    )
    private val filipino = CaptionCandidate(
        url = "https://media.example.invalid/fil.srt",
        format = CaptionFormat.SRT,
        host = "media.example.invalid",
        language = "fil",
        label = "Filipino"
    )

    @Test fun tvPickerExposesReadableTracksAndAccessibleSelection() {
        var chosen: CaptionCandidate? = null
        compose.setContent {
            BousygramTheme {
                var selected by remember { mutableStateOf<CaptionCandidate?>(null) }
                ExtrasSubtitlePicker(
                    captions = listOf(english, filipino),
                    selected = selected,
                    isTv = true,
                    onSelected = { selected = it; chosen = it },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Subtitle tracks").assertExists()
        compose.onNodeWithText("Off · No subtitles").assertIsSelected()
        compose.onNodeWithText("English  ·  VTT").assertIsNotSelected()
        compose.onNodeWithText("English  ·  VTT").performClick()
        compose.runOnIdle { assertEquals(english, chosen) }
        compose.onNodeWithText("English  ·  VTT").assertIsSelected()
    }

    @Test fun tvPickerAllowsTurningCaptionsOff() {
        var chosen: CaptionCandidate? = english
        compose.setContent {
            BousygramTheme {
                var selected by remember { mutableStateOf<CaptionCandidate?>(english) }
                ExtrasSubtitlePicker(
                    captions = listOf(english),
                    selected = selected,
                    isTv = true,
                    onSelected = { selected = it; chosen = it },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("English  ·  VTT").assertIsSelected()
        compose.onNodeWithText("Off · No subtitles").performClick()
        compose.runOnIdle { assertNull(chosen) }
        compose.onNodeWithText("Off · No subtitles").assertIsSelected()
    }
}

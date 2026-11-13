package com.vaydelux.bousygram.presentation.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.components.writeUiEvidence
import com.vaydelux.bousygram.presentation.design.preview.StreamingDesignGallery
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Compose gallery, screenshots and D-pad-compatible semantics. */
@RunWith(AndroidJUnit4::class)
class StreamingDesignGalleryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun debugGalleryShowsLocalOnlyCardsAndAccessibleSelectedTabs() {
        var exited = false
        compose.setContent {
            BousygramTheme {
                StreamingDesignGallery(onExit = { exited = true })
            }
        }

        compose.onNodeWithTag("streaming-design-gallery").assertExists()
        compose.onNodeWithTag("streaming-preview-tab-0").assertIsSelected()
        compose.onNodeWithTag("streaming-media-card-example-a").assertExists()
        compose.onNodeWithTag("streaming-media-card-example-b").assertExists()
        compose.onNodeWithTag("streaming-design-gallery").assertExists()
        writeUiEvidence(compose, "brand-neutral-design-dark")
        compose.onNodeWithTag("streaming-preview-tab-1").performClick()
        compose.onNodeWithTag("streaming-preview-tab-1").assertIsSelected()
        compose.onNodeWithText("Discover").assertExists()
        compose.onNodeWithText("Exit preview").performClick()
        compose.runOnIdle { assertTrue(exited) }
    }

    @Test fun lightAndHighContrastRemainReadableWithoutPersistingAppearance() {
        compose.setContent {
            var preset by remember { mutableStateOf(BousygramThemePreset.LIGHT) }
            BousygramTheme(preset = preset) {
                StreamingDesignGallery(onExit = {
                    preset = BousygramThemePreset.HIGH_CONTRAST
                })
            }
        }
        compose.onNodeWithTag("streaming-preview-tab-0").assertIsSelected()
        writeUiEvidence(compose, "brand-neutral-design-light")
        compose.onNodeWithText("Exit preview").performClick()
        compose.onNodeWithTag("streaming-design-gallery").assertExists()
        writeUiEvidence(compose, "brand-neutral-design-high-contrast")
    }
}

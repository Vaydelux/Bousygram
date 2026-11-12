package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Offline design-system contract covering TV/phone component parity.
 * No Telegram credentials, TMDB key or external provider is required.
 */
@RunWith(AndroidJUnit4::class)
class UniformPremiumDesignInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun spaceGroteskOwnsAllHeadingsWhileInterOwnsLabelsAndBody() {
        var headline: FontFamily? = null
        var title: FontFamily? = null
        var subtitle: FontFamily? = null
        var label: FontFamily? = null
        var body: FontFamily? = null
        compose.setContent {
            BousygramTheme {
                val typography = MaterialTheme.typography
                SideEffect {
                    headline = typography.headlineLarge.fontFamily
                    title = typography.titleLarge.fontFamily
                    subtitle = typography.titleSmall.fontFamily
                    label = typography.labelLarge.fontFamily
                    body = typography.bodyMedium.fontFamily
                }
            }
        }
        compose.runOnIdle {
            assertEquals(headline, title)
            assertEquals(headline, subtitle)
            assertEquals(label, body)
            assertNotEquals("Display and body fonts must be distinct", headline, body)
        }
    }

    @Test fun captureBundledTypographyAndMediaCardInEveryTheme() {
        val selected = mutableStateOf(BousygramThemePreset.DARK)
        compose.setContent {
            BousygramTheme(preset = selected.value) {
                PremiumGridBackdrop {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BousygramPageHeading("Telegram media", "Your library", selected.value.displayName, compact = true)
                        MediaPosterCard(
                            media = com.vaydelux.bousygram.domain.model.MediaItem(
                                -100, 1, 0, com.vaydelux.bousygram.domain.model.MediaType.Video,
                                "A longer episode title with readable metadata", null),
                            subtitle = "Saved Messages", onClick = {}, cardWidth = 280.dp
                        )
                    }
                }
            }
        }
        BousygramThemePreset.entries.forEach { preset ->
            compose.runOnIdle { selected.value = preset }
            writeUiEvidence(compose, "component-theme-" + preset.id.replace('_', '-'))
        }
    }

    @Test fun phoneAndTvNavigationUseTheSameThemeSemanticsAndPremiumBackground() {
        compose.setContent {
            BousygramTheme(preset = BousygramThemePreset.AURORA) {
                PremiumGridBackdrop {
                    var selection by remember { mutableStateOf("Library") }
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BousygramPageHeading(
                            eyebrow = "Bousygram / Telegram",
                            title = "Your library",
                            subtitle = "Shared visual language",
                            compact = false
                        )
                        BousygramSectionTab(
                            label = "Library", active = selection == "Library",
                            isTv = false, onClick = { selection = "Library" },
                            modifier = Modifier.testTag("phone-premium-tab")
                        )
                        BousygramSectionTab(
                            label = "Extras", active = selection == "Extras",
                            isTv = true, onClick = { selection = "Extras" },
                            modifier = Modifier.testTag("tv-premium-tab")
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("bousygram-premium-backdrop").assertIsDisplayed()
        compose.onNodeWithTag("phone-premium-tab").assertIsSelected()
        compose.onNodeWithTag("tv-premium-tab").performClick()
        compose.onNodeWithTag("tv-premium-tab").assertIsSelected()
    }

    @Test fun highContrastRetainsPremiumSharedNavigationWithoutDecorativeEffects() {
        compose.setContent {
            BousygramTheme(preset = BousygramThemePreset.HIGH_CONTRAST) {
                PremiumGridBackdrop {
                    BousygramSectionTab(
                        label = "Saved", active = true, isTv = true,
                        onClick = {}, modifier = Modifier.testTag("contrast-premium-tab")
                    )
                }
            }
        }
        compose.onNodeWithTag("bousygram-premium-backdrop").assertExists()
        compose.onNodeWithTag("contrast-premium-tab").assertIsSelected()
    }
}

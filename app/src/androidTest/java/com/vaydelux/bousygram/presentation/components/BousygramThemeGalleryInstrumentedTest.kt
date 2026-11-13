package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CI interaction checks for the actual Settings appearance picker. */
@RunWith(AndroidJUnit4::class)
class BousygramThemeGalleryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyPremiumThemeCanBeSelectedWithTheSharedFocusSemantics() {
        compose.setContent {
            var active by remember { mutableStateOf(BousygramThemePreset.DARK) }
            BousygramTheme(preset = active) {
                PremiumGridBackdrop {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        BousygramBrandMark(size = 40.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Appearance",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(12.dp))
                        BousygramThemeGallery(selected = active, onSelect = { active = it })
                    }
                }
            }
        }

        compose.onNodeWithTag("bousygram-canonical-brand-mark").assertExists()
        compose.onNodeWithTag("theme-preset-dark").assertIsSelected()
        writeUiEvidence(compose, "themes-midnight")
        // Iterate every installed choice so new palettes cannot be added
        // without exercising their native Settings focus/selection behavior.
        for (preset in BousygramThemePreset.entries.filterNot { it == BousygramThemePreset.DARK }) {
            val tag = "theme-preset-" + preset.id
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithTag(tag).assertIsSelected()
        }
    }
}

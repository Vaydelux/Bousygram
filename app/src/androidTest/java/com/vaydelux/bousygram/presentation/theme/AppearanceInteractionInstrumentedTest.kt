package com.vaydelux.bousygram.presentation.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.components.BousygramDynamicIsland
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramIosAppearanceControls
import com.vaydelux.bousygram.presentation.components.DynamicIslandAction
import com.vaydelux.bousygram.presentation.components.DynamicIslandActivityState
import org.junit.Assert.assertEquals
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceInteractionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun persistedAppearanceSnapshotTracksAtomicAndIndividualChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val flow = EnterprisePreferences.visualAppearanceFlow
            val previous = flow.value
            val newTheme = if (previous.themeId == "cupertino") "midnight_glass" else "cupertino"
            val newStyle = if (previous.designStyleId == "iphone_glass") "cinema_halo" else "iphone_glass"
            val newPattern = if (previous.backdropPatternId == "ios_mesh") "ambient_halo" else "ios_mesh"
            try {
                EnterprisePreferences.setVisualAppearance(newTheme, newStyle, newPattern, !previous.reduceTransparency)
                assertEquals(EnterprisePreferences.VisualAppearance(newTheme, newStyle, newPattern, !previous.reduceTransparency), flow.value)
                EnterprisePreferences.setReduceTransparency(previous.reduceTransparency)
                assertEquals(previous.reduceTransparency, flow.value.reduceTransparency)
                EnterprisePreferences.setVisualThemeId(previous.themeId)
                assertEquals(previous.themeId, flow.value.themeId)
                assertEquals(newStyle, flow.value.designStyleId)
                assertEquals(newPattern, flow.value.backdropPatternId)
            } finally {
                EnterprisePreferences.setVisualAppearance(previous.themeId, previous.designStyleId,
                    previous.backdropPatternId, previous.reduceTransparency)
            }
        }
    }

    @Test fun appearanceControlsExposeSelectionAndToggleState() {
        compose.setContent {
            var preset by androidx.compose.runtime.remember { mutableStateOf(BousygramThemePreset.MIDNIGHT_GLASS) }
            var reduced by androidx.compose.runtime.remember { mutableStateOf(false) }
            BousygramTheme(preset = preset, reduceTransparency = reduced) {
                BousygramIosAppearanceControls(
                    activePreset = preset,
                    reduceTransparency = reduced,
                    onSelectPreset = { preset = it },
                    onReduceTransparencyChange = { reduced = it }
                )
            }
        }
        compose.onNodeWithTag("ios-appearance-dark").assertIsSelected()
        compose.onNodeWithTag("ios-appearance-light").performClick().assertIsSelected()
        compose.onNodeWithTag("ios-appearance-contrast").performClick().assertIsSelected()
        compose.onNodeWithTag("ios-reduce-transparency-toggle").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("ios-reduce-transparency-toggle").performClick().assertIsOff()
    }

    @Test fun dynamicIslandActionDismissesSheetAndFiresOnce() {
        var calls = 0
        compose.setContent {
            BousygramTheme {
                BousygramDynamicIsland(
                    state = DynamicIslandActivityState(
                        title = "Online",
                        showQuickAppearance = false,
                        actions = listOf(DynamicIslandAction("resume", "Continue", BousygramIcons.Continue) {
                            calls++
                        })
                    )
                )
            }
        }
        compose.onNodeWithTag("bousygram-dynamic-island").performClick()
        compose.onNodeWithTag("bousygram-dynamic-island-sheet").assertExists()
        compose.onNodeWithTag("dynamic-island-action-resume").performClick()
        compose.onNodeWithTag("bousygram-dynamic-island-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun dynamicIslandCloseDoesNotExecuteAction() {
        var calls = 0
        compose.setContent {
            BousygramTheme {
                BousygramDynamicIsland(
                    state = DynamicIslandActivityState(
                        title = "Online",
                        showQuickAppearance = false,
                        actions = listOf(DynamicIslandAction("resume", "Continue", BousygramIcons.Continue) {
                            calls++
                        })
                    )
                )
            }
        }
        compose.onNodeWithTag("bousygram-dynamic-island").performClick()
        compose.onNodeWithTag("dynamic-island-close").performClick()
        compose.onNodeWithTag("bousygram-dynamic-island-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithTag("bousygram-dynamic-island").performClick()
        compose.onNodeWithTag("bousygram-dynamic-island-sheet").assertExists()
    }
}

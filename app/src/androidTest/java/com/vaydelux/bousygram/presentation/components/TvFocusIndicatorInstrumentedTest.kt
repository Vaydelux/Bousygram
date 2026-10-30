package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the same focusable controls used by TV navigation and playback chrome.
 * This tests actual Compose focus transitions; visual contrast still needs device testing.
 */
@RunWith(AndroidJUnit4::class)
class TvFocusIndicatorInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun dpadMovesSingleFocusTargetAndUpdatesVisibleFocusedState() {
        val firstRequester = FocusRequester()
        val secondRequester = FocusRequester()
        var secondClicks = 0
        composeRule.setContent {
            BousygramTheme {
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TvFocusSurface(
                        onClick = {},
                        modifier = Modifier
                            // Explicit TV focus path prevents ambiguous wrap/search
                            // when the two cards are embedded inside other controls.
                            .focusProperties { right = secondRequester }
                            .focusRequester(firstRequester)
                            .testTag("tv_first_focus")
                    ) { focused ->
                        Text(
                            if (focused) "First focused" else "First unfocused",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    TvFocusSurface(
                        onClick = { secondClicks++ },
                        modifier = Modifier
                            .focusProperties { left = firstRequester }
                            .focusRequester(secondRequester)
                            .testTag("tv_second_focus")
                    ) { focused ->
                        Text(
                            if (focused) "Second focused" else "Second unfocused",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle { firstRequester.requestFocus() }
        composeRule.onNodeWithTag("tv_first_focus").assertIsFocused()
        composeRule.onNodeWithText("First focused").assertExists()

        composeRule.onNodeWithTag("tv_first_focus").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithTag("tv_second_focus").assertIsFocused()
        composeRule.onNodeWithText("Second focused").assertExists()
        composeRule.onNodeWithText("First unfocused").assertExists()

        // D-pad center/Enter must activate the same target whose outline is visible.
        composeRule.onNodeWithTag("tv_second_focus").performKeyInput {
            pressKey(Key.Enter)
        }
        composeRule.runOnIdle { assertEquals(1, secondClicks) }
    }
}

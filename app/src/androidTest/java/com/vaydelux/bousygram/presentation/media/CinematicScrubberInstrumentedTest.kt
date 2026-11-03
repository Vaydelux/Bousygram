package com.vaydelux.bousygram.presentation.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Compose touch interaction on the active cinematic timeline. Metadata
 * may refine a previously-known duration without changing the media identity.
 */
@RunWith(AndroidJUnit4::class)
class CinematicScrubberInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun midSessionDurationRefinementDoesNotDisableSeeking() {
        val seeks = mutableListOf<Long>()
        var refineDuration: ((Long) -> Unit)? = null
        composeRule.setContent {
            var duration by remember { mutableLongStateOf(120_000L) }
            refineDuration = { duration = it }
            CinematicScrubber(
                mediaKey = "test-media",
                position = 10_000L,
                buffered = 45_000L,
                duration = duration,
                onSeek = { seeks += it },
                onScrub = {},
                focus = null
            )
        }
        composeRule.onNodeWithTag("cinematic_scrubber").performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, seeks.size)
            assertTrue("first seek should be near half duration", seeks[0] in 57_000L..63_000L)
            refineDuration?.invoke(240_000L)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cinematic_scrubber").performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(2, seeks.size)
            assertTrue("refined duration should be used", seeks[1] in 117_000L..123_000L)
        }
    }
}

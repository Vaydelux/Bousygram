package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No player, network, decoder, or Telegram login required. */
@RunWith(AndroidJUnit4::class)
class PlayerLoadingBannerInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeHundredTwentyDpPortraitLoadingFitsInViewport() {
        compose.setContent {
            BousygramTheme {
                Box(Modifier.width(320.dp).height(250.dp)) {
                    PlayerLoadingBanner(
                        phase = PlayerLoadingPhase.BUFFERING,
                        compact = true,
                        retryCount = 0,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag("player_loading_banner").assertIsDisplayed()
        val rect = compose.onNodeWithTag("player_loading_banner").getUnclippedBoundsInRoot()
        assertTrue("Banner must fit 320dp width: $rect", rect.left >= 0.dp && rect.right <= 320.dp)
    }

    @Test fun highContrastLandscapeReconnectingFitsAvailableScreen() {
        compose.setContent {
            BousygramTheme(BousygramThemePreset.HIGH_CONTRAST) {
                Box(Modifier.width(540.dp).height(240.dp)) {
                    PlayerLoadingBanner(
                        phase = PlayerLoadingPhase.RECONNECTING,
                        compact = true,
                        retryCount = 2,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithTag("player_loading_banner").assertIsDisplayed()
        val rect = compose.onNodeWithTag("player_loading_banner").getUnclippedBoundsInRoot()
        assertTrue("Banner must not overflow 540dp landscape: $rect",
            rect.left >= 0.dp && rect.right <= 540.dp)
    }
}

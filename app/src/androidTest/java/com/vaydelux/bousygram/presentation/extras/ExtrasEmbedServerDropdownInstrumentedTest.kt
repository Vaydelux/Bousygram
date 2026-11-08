package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline source picker checks: no Telegram login or provider stream required. */
@RunWith(AndroidJUnit4::class)
class ExtrasEmbedServerDropdownInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tvRemoteCanOpenServerMenuAndChangeProvider() {
        var selected: ExtrasEmbedServer = ExtrasEmbedServer.CINESRC
        compose.setContent {
            BousygramTheme {
                var current by remember { mutableStateOf(ExtrasEmbedServer.CINESRC) }
                ExtrasEmbedServerDropdown(
                    server = current,
                    isTv = true,
                    onSelected = { current = it; selected = it }
                )
            }
        }
        val button = compose.onNodeWithTag("extras-server-dropdown")
        button.assertExists()
        button.performSemanticsAction(SemanticsActions.RequestFocus)
        button.performClick()
        compose.onNodeWithTag("extras-server-xxiku").assertExists().performClick()
        compose.runOnIdle { assertEquals(ExtrasEmbedServer.XXIKU, selected) }
        compose.onNodeWithTag("extras-server-dropdown").performClick()
        compose.onNodeWithTag("extras-server-xxiku").assertIsSelected()
    }

    @Test fun narrowPhoneStillShowsSourceSelector() {
        compose.setContent {
            BousygramTheme {
                Box(Modifier.width(280.dp)) {
                    ExtrasEmbedServerDropdown(
                        server = ExtrasEmbedServer.CINESRC,
                        isTv = false,
                        onSelected = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("extras-server-dropdown").assertExists().performClick()
        compose.onNodeWithTag("extras-server-embed_heaven").assertExists()
    }
}

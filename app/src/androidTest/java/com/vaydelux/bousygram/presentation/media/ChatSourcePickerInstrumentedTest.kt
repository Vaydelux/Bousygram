package com.vaydelux.bousygram.presentation.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.domain.model.ChatSourceKind
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.components.AllChatsPickerDialog
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Catalog updates must not close or remount the source picker while a user is browsing. */
@RunWith(AndroidJUnit4::class)
class ChatSourcePickerInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun filteringRemainsActiveWhileMoreTelegramChannelsArrive() {
        val channels = mutableStateOf(
            listOf(
                VideoChatItem(11L, "Movies", false, kind = ChatSourceKind.CHANNEL),
                VideoChatItem(12L, "Family Chat", false, kind = ChatSourceKind.GROUP)
            )
        )
        composeRule.setContent {
            BousygramTheme {
                AllChatsPickerDialog(
                    chats = channels.value,
                    selectedChatId = null,
                    savedSelected = true,
                    pinnedChatIds = emptySet(),
                    isLoading = true,
                    onDismiss = {},
                    onSelectSavedMessages = {},
                    onSelectChat = {}
                )
            }
        }

        composeRule.onNodeWithText("Choose a video source").assertExists()
        composeRule.onNodeWithText("Channels").performClick()
        composeRule.onNodeWithText("Movies").assertExists()
        composeRule.onNodeWithText("Family Chat").assertDoesNotExist()

        composeRule.runOnIdle {
            channels.value = channels.value + VideoChatItem(
                13L, "New Telegram Channel", false, kind = ChatSourceKind.CHANNEL
            )
        }
        composeRule.onNodeWithText("Choose a video source").assertExists()
        composeRule.onNodeWithText("Movies").assertExists()
        composeRule.onNodeWithText("New Telegram Channel").assertExists()
        composeRule.onNodeWithText("Family Chat").assertDoesNotExist()
    }
    @Test fun pickerRemainsActionableWithADeepSourceList() {
        val sources = (1L..80L).map { id ->
            VideoChatItem(id, "Channel $id", false, kind = ChatSourceKind.CHANNEL)
        }
        var selected: Long? = null
        var closeCount = 0
        composeRule.setContent {
            BousygramTheme {
                AllChatsPickerDialog(
                    chats = sources,
                    selectedChatId = null,
                    savedSelected = false,
                    pinnedChatIds = emptySet(),
                    onDismiss = { closeCount++ },
                    onSelectSavedMessages = {},
                    onSelectChat = { selected = it.chatId }
                )
            }
        }
        composeRule.onNodeWithTag("telegram-chat-source-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("telegram-chat-source-grid").assertIsDisplayed()
        composeRule.onNodeWithText("Channels").performClick()
        composeRule.onNodeWithText("Channel 1").performClick()
        composeRule.runOnIdle { assertEquals(1L, selected) }
        composeRule.onNodeWithTag("telegram-chat-source-close").performClick()
        composeRule.runOnIdle { assertEquals(1, closeCount) }
    }

}

package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.ChatSourceKind
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerSourcePolicyTest {
    private val chats = listOf(
        VideoChatItem(1, "Saved", isSavedMessages = true),
        VideoChatItem(2, "My Channel", isSavedMessages = false, kind = ChatSourceKind.CHANNEL),
        VideoChatItem(3, "Group", isSavedMessages = false, kind = ChatSourceKind.GROUP),
        VideoChatItem(4, "Forum", isSavedMessages = false, kind = ChatSourceKind.GROUP, isForum = true),
        VideoChatItem(5, "Private", isSavedMessages = false, kind = ChatSourceKind.PRIVATE),
        VideoChatItem(6, "Secret", isSavedMessages = false, kind = ChatSourceKind.SECRET),
        VideoChatItem(2, "Duplicated source update", isSavedMessages = false, kind = ChatSourceKind.CHANNEL)
    )

    @Test
    fun onlyActualChannelsAppearUnderChannelsAndDuplicatesAreCollapsed() {
        assertEquals(listOf(2L), explorerSources(chats, ExplorerSection.CHANNELS).map { it.chatId })
    }

    @Test
    fun forumAndBasicGroupsAppearUnderGroupsButPrivateChatsNeverLeak() {
        assertEquals(listOf(3L, 4L), explorerSources(chats, ExplorerSection.GROUPS).map { it.chatId })
    }

    @Test
    fun savedMessagesIsNotDuplicatedAsSourceFolder() {
        assertTrue(explorerSources(chats, ExplorerSection.SAVED).isEmpty())
    }
}

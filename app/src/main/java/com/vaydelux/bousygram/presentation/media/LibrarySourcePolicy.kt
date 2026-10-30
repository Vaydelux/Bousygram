package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.VideoChatItem

/** The Library's first tab already owns Saved Messages; preserve one chat per source. */
internal fun libraryOtherChatSources(sources: List<VideoChatItem>): List<VideoChatItem> =
    sources.filterNot { it.isSavedMessages }.distinctBy { it.chatId }

/** Preserve the visible catalog order while TDLib updates chat metadata or adds sources.
 * Re-sorting/replacing the whole list while the user scrolls sends the sidebar to the top.
 * Removed chats disappear, existing sources keep their positions, new chats append once.
 */
internal fun stableChatCatalog(previous: List<com.vaydelux.bousygram.domain.model.VideoChatItem>, incoming: List<com.vaydelux.bousygram.domain.model.VideoChatItem>): List<com.vaydelux.bousygram.domain.model.VideoChatItem> {
    val latestById = incoming.associateBy { it.chatId }
    val retained = previous.mapNotNull { latestById[it.chatId] }.distinctBy { it.chatId }
    val alreadyIncluded = retained.mapTo(mutableSetOf()) { it.chatId }
    return retained + incoming.filter { alreadyIncluded.add(it.chatId) }
}

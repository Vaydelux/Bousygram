package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.VideoChatItem

/**
 * Repeated TDLib directory entries must use their newest metadata while keeping
 * their first visible position. This avoids a stale title in newly appended chats.
 */
private fun latestChatsInStableOrder(chats: List<VideoChatItem>): List<VideoChatItem> {
    val latest = LinkedHashMap<Long, VideoChatItem>(chats.size)
    for (chat in chats) latest[chat.chatId] = chat
    return latest.values.toList()
}

/** The Library's first tab already owns Saved Messages; preserve one chat per source. */
internal fun libraryOtherChatSources(sources: List<VideoChatItem>): List<VideoChatItem> =
    latestChatsInStableOrder(sources).filterNot { it.isSavedMessages }

/** Preserve the visible catalog order while TDLib updates chat metadata or adds sources.
 * Re-sorting/replacing the whole list while the user scrolls sends the sidebar to the top.
 * Removed chats disappear, existing sources keep their positions, new chats append once.
 */
internal fun stableChatCatalog(
    previous: List<VideoChatItem>,
    incoming: List<VideoChatItem>,
    retainMissing: Boolean = false
): List<VideoChatItem> {
    val latest = latestChatsInStableOrder(incoming)
    val latestById = latest.associateBy { it.chatId }
    val retained = previous.mapNotNull { latestById[it.chatId] ?: if (retainMissing) it else null }
        .distinctBy { it.chatId }
    val alreadyIncluded = retained.mapTo(mutableSetOf()) { it.chatId }
    return retained + latest.filter { alreadyIncluded.add(it.chatId) }
}

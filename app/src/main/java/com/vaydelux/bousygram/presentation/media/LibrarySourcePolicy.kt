package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.VideoChatItem

/** The Library's first tab already owns Saved Messages; preserve one chat per source. */
internal fun libraryOtherChatSources(sources: List<VideoChatItem>): List<VideoChatItem> =
    sources.filterNot { it.isSavedMessages }.distinctBy { it.chatId }

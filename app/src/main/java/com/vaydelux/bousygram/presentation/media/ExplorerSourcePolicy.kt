package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.ChatSourceKind
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection

/** Actual Telegram chat types only. Never infer a source from its title. */
internal fun explorerSources(
    sources: List<VideoChatItem>,
    section: ExplorerSection
): List<VideoChatItem> = sources.asSequence()
    .filterNot { it.isSavedMessages }
    .filter { source ->
        when (section) {
            ExplorerSection.SAVED -> false
            ExplorerSection.CHANNELS -> source.kind == ChatSourceKind.CHANNEL
            ExplorerSection.GROUPS -> source.kind == ChatSourceKind.GROUP
        }
    }
    .distinctBy { it.chatId }
    .toList()

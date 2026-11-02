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

/** UI labels use real Telegram source metadata, never guessed media counts. */
internal fun explorerSourceLabel(source: VideoChatItem): String = when {
    source.isForum -> "Forum group"
    source.kind == ChatSourceKind.CHANNEL -> "Channel"
    source.kind == ChatSourceKind.GROUP -> "Group"
    else -> "Telegram chat"
}

internal fun explorerDirectoryStatus(
    section: ExplorerSection,
    discovered: Int,
    loading: Boolean
): String {
    val noun = if (section == ExplorerSection.CHANNELS) "channels" else "groups"
    return if (loading) "$discovered $noun found · Still syncing"
    else "$discovered $noun available"
}

internal fun explorerSourceDetail(source: VideoChatItem): String = when {
    source.isForum -> "Explore topics and shared media"
    source.kind == ChatSourceKind.CHANNEL -> "Browse channel media and files"
    else -> "Browse shared videos and audio"
}

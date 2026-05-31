package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.tdlib.fastlink.FastLinkManager
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackMode
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun SavedMessagesRepository.requestFastLinkInternal(
    item: MediaItem,
    onResult: (String?, String?) -> Unit
) {
    val fileId = item.fileId
    if (fileId == null) {
        onResult(null, "No fileId associated with this item")
        return
    }
    if (EnterprisePreferences.playbackMode() == PlaybackMode.FastLink) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = FastLinkManager.requestFastLink(item.chatId, item.messageId)
            result.fold(
                onSuccess = { onResult(it, null) },
                onFailure = { onResult(null, it.message ?: "Fast-link request failed") }
            )
        }
        return
    }
    val preparation = LocalStreamingServer.prepareStream(
        fileId = fileId,
        declaredSize = item.fileSizeBytes,
        title = item.title,
        mimeType = item.mimeType,
        fileName = item.fileName
    )
    if (preparation == null) {
        onResult(null, "File is not ready to stream yet")
        return
    }
    onResult(preparation.url, null)
}

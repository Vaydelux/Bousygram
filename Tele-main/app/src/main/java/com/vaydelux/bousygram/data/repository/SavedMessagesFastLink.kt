package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.domain.model.MediaItem

internal fun SavedMessagesRepository.requestFastLinkInternal(
    item: MediaItem,
    onResult: (String?, String?) -> Unit
) {
    val fileId = item.fileId
    if (fileId == null) {
        onResult(null, "No fileId associated with this item")
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

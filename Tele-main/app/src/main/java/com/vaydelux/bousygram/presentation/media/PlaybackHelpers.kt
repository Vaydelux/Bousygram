package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType

fun playableVideos(items: List<MediaItem>): List<MediaItem> =
    items.filter { it.type == MediaType.Video && it.fileId != null }

fun buildPlaybackQueue(videos: List<MediaItem>): List<PlaybackQueueItem> =
    playableVideos(videos).mapNotNull { item ->
        val fileId = item.fileId ?: return@mapNotNull null
        PlaybackQueueItem(
            // Queue entries only carry metadata. PlayerScreen prepares a scoped stream URL
            // when an item actually becomes active; pre-generating URLs for every queue item
            // created unused access tokens and could invalidate an active playback URL.
            url = null,
            chatId = item.chatId,
            messageId = item.messageId,
            title = mediaDisplayLabels(item, "").title,
            fileId = fileId,
            fileName = item.fileName,
            mimeType = item.mimeType,
            durationSeconds = item.durationSeconds,
            fileSizeBytes = item.fileSizeBytes
        )
    }

fun buildPlaybackRequest(
    item: MediaItem,
    streamUrl: String,
    queueVideos: List<MediaItem>,
    subtitleUrl: String? = null
): PlaybackRequest {
    val queue = buildPlaybackQueue(queueVideos)
    val fileId = item.fileId ?: 0
    return PlaybackRequest(
        url = streamUrl,
        chatId = item.chatId,
        messageId = item.messageId,
        fileId = fileId,
        title = mediaDisplayLabels(item, "").title,
        queue = queue,
        subtitleUrl = subtitleUrl,
        fileName = item.fileName,
        mimeType = item.mimeType,
        durationSeconds = item.durationSeconds,
        fileSizeBytes = item.fileSizeBytes,
        startsWithFastLink = !streamUrl.startsWith("http://127.0.0.1")
    )
}

fun queueIndexForFileId(queue: List<PlaybackQueueItem>, fileId: Int): Int =
    queue.indexOfFirst { it.fileId == fileId }

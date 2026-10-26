package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType

fun playableVideos(items: List<MediaItem>): List<MediaItem> =
    items.filter { (it.type == MediaType.Video || it.type == MediaType.Audio) && it.fileId != null }

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
            fileSizeBytes = item.fileSizeBytes,
            alternativeVideos = item.alternativeVideos
        )
    }

fun buildPlaybackRequest(
    item: MediaItem,
    streamUrl: String?,
    queueVideos: List<MediaItem>,
    subtitleUrl: String? = null
): PlaybackRequest {
    val queue = buildPlaybackQueue(queueVideos)
    val fileId = item.fileId ?: 0
    return PlaybackRequest(
        initialStreamUrl = streamUrl?.takeIf { it.isNotBlank() },
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
        alternativeVideos = item.alternativeVideos
    )
}

fun queueItemFromRequest(request: PlaybackRequest): PlaybackQueueItem =
    PlaybackQueueItem(
        url = null,
        chatId = request.chatId,
        messageId = request.messageId,
        title = request.title,
        fileId = request.fileId,
        fileName = request.fileName,
        mimeType = request.mimeType,
        durationSeconds = request.durationSeconds,
        fileSizeBytes = request.fileSizeBytes
    )

fun queueIndexForFileId(queue: List<PlaybackQueueItem>, fileId: Int): Int =
    if (fileId > 0) queue.indexOfFirst { it.fileId == fileId } else -1

/** The tapped Telegram message wins, even when an earlier row shares the same file. */
fun queueIndexForRequest(
    queue: List<PlaybackQueueItem>,
    chatId: Long,
    messageId: Long,
    fileId: Int
): Int {
    if (messageId != 0L) {
        val byMessage = queue.indexOfFirst { it.chatId == chatId && it.messageId == messageId }
        if (byMessage >= 0) return byMessage
    }
    return queueIndexForFileId(queue, fileId)
}

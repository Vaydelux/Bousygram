package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType

fun playableVideos(items: List<MediaItem>): List<MediaItem> =
    items.filter { it.type == MediaType.Video && it.fileId != null }

fun buildPlaybackQueue(videos: List<MediaItem>): List<PlaybackQueueItem> =
    videos.mapNotNull { item ->
        val fileId = item.fileId ?: return@mapNotNull null
        PlaybackQueueItem(
            url = LocalStreamingServer.getStreamUrl(
                fileId,
                item.fileSizeBytes,
                item.title,
                item.mimeType,
                item.fileName
            ),
            title = mediaDisplayLabels(item, "").title,
            fileId = fileId,
            fileName = item.fileName,
            mimeType = item.mimeType,
            durationSeconds = item.durationSeconds
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
        fileId = fileId,
        title = mediaDisplayLabels(item, "").title,
        queue = queue,
        subtitleUrl = subtitleUrl,
        fileName = item.fileName,
        mimeType = item.mimeType,
        durationSeconds = item.durationSeconds
    )
}

fun queueIndexForFileId(queue: List<PlaybackQueueItem>, fileId: Int): Int =
    queue.indexOfFirst { it.fileId == fileId }

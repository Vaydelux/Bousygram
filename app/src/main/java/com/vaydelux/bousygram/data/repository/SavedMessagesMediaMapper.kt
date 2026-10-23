package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Generates a friendly date-based title from a TDLib message timestamp (seconds since epoch). */
private fun dateTitle(prefix: String, messageDate: Int): String {
    val sdf = SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.getDefault())
    return "$prefix - ${sdf.format(Date(messageDate.toLong() * 1000L))}"
}

private fun tdFileSize(file: TdApi.File?): Long {
    file ?: return 0L
    if (file.size > 0L) return file.size
    if (file.expectedSize > 0L) return file.expectedSize
    val local = file.local
    if (local != null && local.isDownloadingCompleted && !local.path.isNullOrBlank()) {
        val diskLen = java.io.File(local.path).length()
        if (diskLen > 0L) return diskLen
    }
    return 0L
}

internal fun SavedMessagesRepository.mapMessageToMediaInternal(message: TdApi.Message): MediaItem? {
    return when (val content = message.content) {
        is TdApi.MessageVideo -> {
            val video = content.video
            val fileName = video?.fileName?.takeIf { it.isNotBlank() }
            val mimeType = video?.mimeType?.takeIf { it.isNotBlank() }
            val title = content.caption?.text?.takeIf { it.isNotBlank() }
                ?: fileName
                ?: dateTitle("Video", message.date)
            val file = video?.video
            val thumb = video?.thumbnail
            val altVideos = content.alternativeVideos?.mapNotNull { alt ->
                com.vaydelux.bousygram.domain.model.MediaAlternativeVideo(
                    id = alt.id,
                    width = alt.width,
                    height = alt.height,
                    codec = alt.codec.orEmpty(),
                    hlsFileId = alt.hlsFile?.id,
                    fileId = alt.video?.id
                )
            }.orEmpty()
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Video,
                title = title,
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = video?.minithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = video?.duration ?: 0,
                trustedDurationMs = trustedDurationMs(file?.id, video?.duration ?: 0),
                fileSizeBytes = tdFileSize(file),
                fileName = fileName,
                mimeType = mimeType,
                alternativeVideos = altVideos
            )
        }
        is TdApi.MessageVideoNote -> {
            val videoNote = content.videoNote
            val file = videoNote?.video
            val thumb = videoNote?.thumbnail
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Video,
                title = dateTitle("Video Note", message.date),
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = videoNote?.minithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = videoNote?.duration ?: 0,
                trustedDurationMs = trustedDurationMs(file?.id, videoNote?.duration ?: 0),
                fileSizeBytes = tdFileSize(file)
            )
        }
        is TdApi.MessageDocument -> {
            val document = content.document
            if (!isVideoDocumentInternal(document)) return null
            val fileName = document?.fileName?.takeIf { it.isNotBlank() }
            val mimeType = document?.mimeType?.takeIf { it.isNotBlank() }
            val title = content.caption?.text?.takeIf { it.isNotBlank() }
                ?: fileName
                ?: dateTitle("File", message.date)
            val file = document?.document
            val thumb = document?.thumbnail
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Video,
                title = title,
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = document?.minithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = 0,
                trustedDurationMs = trustedDurationMs(file?.id, 0),
                fileSizeBytes = tdFileSize(file),
                fileName = fileName,
                mimeType = mimeType
            )
        }
        is TdApi.MessageAnimation -> {
            val animation = content.animation
            val file = animation?.animation
            val thumb = animation?.thumbnail
            val fileName = animation?.fileName?.takeIf { it.isNotBlank() }
            val mimeType = animation?.mimeType?.takeIf { it.isNotBlank() }
            val title = content.caption?.text?.takeIf { it.isNotBlank() }
                ?: fileName
                ?: dateTitle("Animation", message.date)
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Video,
                title = title,
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = animation?.minithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = animation?.duration ?: 0,
                trustedDurationMs = trustedDurationMs(file?.id, animation?.duration ?: 0),
                fileSizeBytes = tdFileSize(file),
                fileName = fileName,
                mimeType = mimeType
            )
        }
        is TdApi.MessagePhoto -> {
            val photo = content.photo
            val bestSize = photo.sizes?.maxByOrNull { it.width * it.height }
            val title = if (content.caption?.text.isNullOrBlank()) "Photo" else content.caption.text
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Photo,
                title = title,
                fileId = bestSize?.photo?.id,
                thumbnailFileId = bestSize?.photo?.id,
                miniThumbnailBytes = photo.minithumbnail?.data,
                thumbnailWidth = bestSize?.width ?: 0,
                thumbnailHeight = bestSize?.height ?: 0,
                durationSeconds = 0,
                trustedDurationMs = 0L,
                fileSizeBytes = tdFileSize(bestSize?.photo)
            )
        }
        else -> null
    }
}

internal fun isVideoDocumentInternal(document: TdApi.Document?): Boolean =
    VideoFormatSupport.isPlayableVideo(document?.mimeType, document?.fileName)

private fun trustedDurationMs(fileId: Int?, durationSeconds: Int): Long {
    val fromTdLib = durationSeconds.takeIf { it > 0 }?.times(1000L) ?: 0L
    if (fileId != null && fromTdLib > 0L) {
        MediaDurationResolver.rememberTdLibDuration(fileId, fromTdLib)
    }
    val cached = fileId?.let(MediaDurationResolver::cachedDurationMs) ?: 0L
    return maxOf(fromTdLib, cached)
}

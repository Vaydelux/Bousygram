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

internal fun SavedMessagesRepository.mapMessageToMediaInternal(
    message: TdApi.Message,
    fallbackTopicId: Long? = null
): MediaItem? {
    val topicThreadId: Long? = when (val t = message.topicId) {
        is TdApi.MessageTopicForum -> t.forumTopicId.toLong()
        is TdApi.MessageTopicThread -> t.messageThreadId
        else -> fallbackTopicId
    }
    return when (val content = message.content) {
        is TdApi.MessageVideo -> {
            val video = content.video
            val fileName = video?.fileName?.takeIf { it.isNotBlank() }
            val mimeType = video?.mimeType?.takeIf { it.isNotBlank() }
            val captionText = content.caption?.text?.takeIf { it.isNotBlank() }
            val title = captionText
                ?: fileName
                ?: dateTitle("Video", message.date)
            val file = video?.video
            val thumb = video?.thumbnail
            val seriesInfo = com.vaydelux.bousygram.core.media.SeriesMetadataParser.parse(title, fileName)
            val parsedMeta = com.vaydelux.bousygram.core.media.MediaMetadataParser.parse(title, captionText, fileName)
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
                alternativeVideos = altVideos,
                seriesTitle = if (parsedMeta.isSeries) (parsedMeta.title.ifBlank { seriesInfo.seriesTitle }) else seriesInfo.seriesTitle,
                seasonNumber = parsedMeta.seasonNumber ?: seriesInfo.seasonNumber,
                episodeNumber = parsedMeta.episodeNumber ?: seriesInfo.episodeNumber,
                releaseYear = parsedMeta.year,
                messageThreadId = topicThreadId
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
                fileSizeBytes = tdFileSize(file),
                messageThreadId = topicThreadId
            )
        }
        is TdApi.MessageAudio -> {
            val audio = content.audio
            val file = audio?.audio
            val thumb = audio?.albumCoverThumbnail
            val fileName = audio?.fileName?.takeIf { it.isNotBlank() }
            val mimeType = audio?.mimeType?.takeIf { it.isNotBlank() }
            val performer = audio?.performer?.takeIf { it.isNotBlank() }
            val songTitle = audio?.title?.takeIf { it.isNotBlank() }
                ?: fileName
                ?: dateTitle("Audio", message.date)
            val fullTitle = if (performer != null) "$performer - $songTitle" else songTitle
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Audio,
                title = fullTitle,
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = audio?.albumCoverMinithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = audio?.duration ?: 0,
                trustedDurationMs = trustedDurationMs(file?.id, audio?.duration ?: 0),
                fileSizeBytes = tdFileSize(file),
                fileName = fileName,
                mimeType = mimeType,
                performer = performer,
                docType = fileName?.substringAfterLast('.', "")?.uppercase()?.ifBlank { "AUDIO" } ?: "AUDIO",
                messageThreadId = topicThreadId
            )
        }
        is TdApi.MessageDocument -> {
            val document = content.document
            val fileName = document?.fileName?.takeIf { it.isNotBlank() }
            val mimeType = document?.mimeType?.takeIf { it.isNotBlank() }
            val isVideo = isVideoDocumentInternal(document)
            val isAudio = mimeType?.startsWith("audio/") == true ||
                fileName?.endsWith(".mp3", true) == true ||
                fileName?.endsWith(".flac", true) == true ||
                fileName?.endsWith(".m4a", true) == true ||
                fileName?.endsWith(".ogg", true) == true ||
                fileName?.endsWith(".wav", true) == true
            val captionText = content.caption?.text?.takeIf { it.isNotBlank() }
            val title = captionText
                ?: fileName
                ?: dateTitle(if (isVideo) "Video" else if (isAudio) "Audio" else "File", message.date)
            val file = document?.document
            val thumb = document?.thumbnail
            val seriesInfo = if (isVideo) com.vaydelux.bousygram.core.media.SeriesMetadataParser.parse(title, fileName) else null
            val parsedMeta = if (isVideo) com.vaydelux.bousygram.core.media.MediaMetadataParser.parse(title, captionText, fileName) else null
            val docType = fileName?.substringAfterLast('.', "")?.uppercase()?.ifBlank { "FILE" } ?: "FILE"
            val type = when {
                isVideo -> MediaType.Video
                isAudio -> MediaType.Audio
                else -> MediaType.Document
            }
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = type,
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
                mimeType = mimeType,
                docType = docType,
                seriesTitle = if (parsedMeta?.isSeries == true) (parsedMeta.title.ifBlank { seriesInfo?.seriesTitle }) else seriesInfo?.seriesTitle,
                seasonNumber = parsedMeta?.seasonNumber ?: seriesInfo?.seasonNumber,
                episodeNumber = parsedMeta?.episodeNumber ?: seriesInfo?.episodeNumber,
                releaseYear = parsedMeta?.year,
                messageThreadId = topicThreadId
            )
        }
        is TdApi.MessageAnimation -> {
            val animation = content.animation
            val file = animation?.animation
            val thumb = animation?.thumbnail
            val fileName = animation?.fileName?.takeIf { it.isNotBlank() }
            val mimeType = animation?.mimeType?.takeIf { it.isNotBlank() }
            val captionText = content.caption?.text?.takeIf { it.isNotBlank() }
            val title = captionText
                ?: fileName
                ?: dateTitle("Animation", message.date)
            val seriesInfo = com.vaydelux.bousygram.core.media.SeriesMetadataParser.parse(title, fileName)
            val parsedMeta = com.vaydelux.bousygram.core.media.MediaMetadataParser.parse(title, captionText, fileName)
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
                mimeType = mimeType,
                seriesTitle = if (parsedMeta.isSeries) (parsedMeta.title.ifBlank { seriesInfo.seriesTitle }) else seriesInfo.seriesTitle,
                seasonNumber = parsedMeta.seasonNumber ?: seriesInfo.seasonNumber,
                episodeNumber = parsedMeta.episodeNumber ?: seriesInfo.episodeNumber,
                releaseYear = parsedMeta.year,
                messageThreadId = topicThreadId
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

internal fun isVideoDocumentInternal(document: TdApi.Document?): Boolean {
    if (document == null) return false
    if (VideoFormatSupport.isPlayableVideo(document.mimeType, document.fileName)) return true
    // Telegram optimization: documents with a generated thumbnail or minithumbnail are playable media
    if (document.thumbnail != null || document.minithumbnail != null) {
        val mime = document.mimeType.orEmpty().lowercase()
        val name = document.fileName.orEmpty().lowercase()
        if (!name.endsWith(".apk") && !name.endsWith(".pdf") && !name.endsWith(".zip") &&
            !name.endsWith(".tar") && !name.endsWith(".gz") &&
            !mime.startsWith("image/") && !mime.startsWith("audio/")) {
            return true
        }
    }
    return false
}

private fun trustedDurationMs(fileId: Int?, durationSeconds: Int): Long {
    val fromTdLib = durationSeconds.takeIf { it > 0 }?.times(1000L) ?: 0L
    if (fileId != null && fromTdLib > 0L) {
        MediaDurationResolver.rememberTdLibDuration(fileId, fromTdLib)
    }
    val cached = fileId?.let(MediaDurationResolver::cachedDurationMs) ?: 0L
    return maxOf(fromTdLib, cached)
}

package com.vaydelux.bousygram.data.database

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.media.SeriesMetadataParser
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object VideoIndexRepository {
    private const val TAG = "VideoIndexRepository"
    private var database: AppDatabase? = null
    private val indexScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (database == null) {
            database = AppDatabase.getInstance(context)
        }
    }

    fun setDatabaseForTest(testDb: AppDatabase) {
        database = testDb
    }

    suspend fun getIndexedVideosForChat(chatId: Long?, topicId: Long?): List<MediaItem> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext emptyList()
        val dao = db.videoDao()
        val entities = when {
            chatId != null && topicId != null && topicId > 0L -> {
                dao.getVideosByChatAndTopic(chatId, topicId)
            }
            chatId != null -> {
                dao.getVideosByChat(chatId)
            }
            else -> {
                dao.getRecentVideos(100, 0)
            }
        }
        entities.map { it.toMediaItem() }
    }

    suspend fun searchIndexedVideos(query: String, chatId: Long?, limit: Int = 50): List<MediaItem> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext emptyList()
        if (query.isBlank()) return@withContext emptyList()
        val dao = db.videoDao()
        val entities = if (chatId != null) {
            dao.searchVideosInChat(chatId, query.trim(), limit)
        } else {
            dao.searchVideos(query.trim(), limit)
        }
        entities.map { it.toMediaItem() }
    }

    suspend fun indexMediaItems(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        if (items.isEmpty()) return@withContext
        val entities = items
            .filter { it.type == MediaType.Video || it.fileId != null }
            .map { it.toVideoEntity() }

        if (entities.isEmpty()) return@withContext

        entities.chunked(25).forEach { chunk ->
            while (PlaybackSessionController.isVideoPlaying()) {
                delay(1000L)
            }
            db.videoDao().insertVideos(chunk)
        }
    }

    fun indexMediaItemsAsync(items: List<MediaItem>) {
        if (items.isEmpty()) return
        indexScope.launch {
            try {
                indexMediaItems(items)
            } catch (e: Exception) {
                Log.w(TAG, "Error indexing media items: ${e.message}")
            }
        }
    }
}

fun MediaItem.toVideoEntity(): VideoEntity {
    val parsed = if (seasonNumber == null && episodeNumber == null) {
        SeriesMetadataParser.parse(title, fileName)
    } else null
    val season = seasonNumber ?: parsed?.seasonNumber
    val episode = episodeNumber ?: parsed?.episodeNumber

    return VideoEntity(
        chatId,
        messageThreadId ?: 0L,
        messageId,
        fileId ?: 0,
        title,
        durationSeconds.toLong(),
        fileSizeBytes,
        miniThumbnailBytes,
        season,
        episode,
        System.currentTimeMillis()
    )
}

fun VideoEntity.toMediaItem(): MediaItem {
    val parsed = SeriesMetadataParser.parse(title, null)
    val series = if (season != null || episode != null) {
        parsed.seriesTitle ?: title
    } else {
        parsed.seriesTitle
    }
    val finalSeason = season ?: parsed.seasonNumber
    val finalEpisode = episode ?: parsed.episodeNumber

    return MediaItem(
        chatId = chatId,
        messageId = messageId,
        date = (lastSeen / 1000L).toInt(),
        type = MediaType.Video,
        title = title,
        fileId = if (fileId > 0) fileId else null,
        thumbnailFileId = null,
        thumbnailPath = null,
        miniThumbnailBytes = minithumbnail,
        thumbnailWidth = 0,
        thumbnailHeight = 0,
        durationSeconds = duration.toInt(),
        trustedDurationMs = duration * 1000L,
        fileSizeBytes = size,
        fileName = null,
        mimeType = null,
        alternativeVideos = emptyList(),
        seriesTitle = series,
        seasonNumber = finalSeason,
        episodeNumber = finalEpisode,
        messageThreadId = if (topicId > 0L) topicId else null,
        topicName = null,
        performer = null,
        docType = null
    )
}

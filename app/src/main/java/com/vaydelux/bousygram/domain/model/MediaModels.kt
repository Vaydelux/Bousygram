package com.vaydelux.bousygram.domain.model

import java.io.Serializable

const val SAVED_MESSAGES_TITLE = "Saved Messages"

enum class MediaType {
    Photo,
    Video,
    Audio,
    Document
}

data class MediaAlternativeVideo(
    val id: Long,
    val width: Int,
    val height: Int,
    val codec: String = "",
    val hlsFileId: Int? = null,
    val fileId: Int? = null
) : Serializable

data class MediaItem(
    val chatId: Long,
    val messageId: Long,
    val date: Int,
    val type: MediaType,
    val title: String,
    val fileId: Int?,
    val thumbnailFileId: Int? = null,
    val thumbnailPath: String? = null,
    val miniThumbnailBytes: ByteArray? = null,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0,
    val durationSeconds: Int = 0,
    val trustedDurationMs: Long = 0L,
    val fileSizeBytes: Long = 0,
    /** Original Telegram file name (used for format detection). */
    val fileName: String? = null,
    /** MIME type from Telegram (e.g. video/x-matroska). */
    val mimeType: String? = null,
    /** Alternative video qualities (e.g. 1080p, 720p, 480p) provided by Telegram. */
    val alternativeVideos: List<MediaAlternativeVideo> = emptyList(),
    /** Series/Show Title if parsed from filename or caption. */
    val seriesTitle: String? = null,
    /** Season number if detected. */
    val seasonNumber: Int? = null,
    /** Episode number if detected. */
    val episodeNumber: Int? = null,
    /** Telegram message thread / topic ID. */
    val messageThreadId: Long? = null,
    /** Telegram topic / thread title. */
    val topicName: String? = null,
    /** Performer / Artist if audio track. */
    val performer: String? = null,
    /** Extracted document / archive format (e.g. APK, PDF, ZIP, FLAC). */
    val docType: String? = null,
    /** Release Year parsed from filename/caption or matched from TMDB. */
    val releaseYear: Int? = null,
    /** Local cached path or remote URL for TMDB poster artwork. */
    val tmdbPosterPath: String? = null,
    /** Local cached path or remote URL for TMDB backdrop artwork. */
    val tmdbBackdropPath: String? = null,
    /** Plot overview / synopsis from TMDB. */
    val tmdbOverview: String? = null,
    /** Vote average / rating from TMDB. */
    val tmdbRating: Float? = null,
    /** Matched official title from TMDB. */
    val tmdbTitle: String? = null,
    /** Matched episode title from TMDB if TV series episode. */
    val episodeTitle: String? = null
) : Serializable

data class SeriesGroup(
    val title: String,
    val episodes: List<MediaItem>,
    val totalSeasons: Int
) : Serializable

enum class MediaCategoryTab(val label: String) {
    ALL("All Videos"),
    SERIES("Series & Shows"),
    AUDIO("Audio & Music"),
    DOCUMENTS("Documents"),
    TOPICS("Topics")
}

enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    SIZE_DESC,
    SIZE_ASC
}

data class ForumTopicChannel(
    val id: Int, // forumTopicId / message_thread_id
    val chatId: Long,
    val name: String,
    val iconColor: Int = 0x6FB9F0,
    val iconCustomEmojiId: Long = 0L,
    val isPinned: Boolean = false,
    val isClosed: Boolean = false,
    val isGeneral: Boolean = false,
    val unreadCount: Int = 0,
    val totalMessages: Int = 0,
    val mediaCount: Int = 0
) : Serializable

data class MediaUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedChatTitle: String = "Saved Messages",
    val selectedChatId: Long? = null,
    val isSavedMessagesSelected: Boolean = true,
    val isLoadingMore: Boolean = false,
    val preparingFileId: Int? = null,
    val hasMore: Boolean = true,
    val nextFromMessageId: Long = 0L,
    val videoChats: List<VideoChatItem> = emptyList(),
    val isSidebarLoading: Boolean = false,
    val sidebarError: String? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val filterYear: Int? = null,
    val filterMonth: Int? = null,
    val selectedCategoryTab: MediaCategoryTab = MediaCategoryTab.ALL,
    val selectedSeriesTitle: String? = null,
    val availableTopics: List<ForumTopicChannel> = emptyList(),
    val selectedTopicId: Int? = null,
    val isLoadingTopics: Boolean = false,
    val quickSearchQuery: String = ""
) {
    val filteredAndSortedItems: List<MediaItem>
        get() {
            val topicNameMap = availableTopics.associate { it.id.toLong() to it.name }
            var result = items.map { item ->
                if (item.topicName == null && item.messageThreadId != null) {
                    val resolved = topicNameMap[item.messageThreadId]
                    if (resolved != null) item.copy(topicName = resolved) else item
                } else {
                    item
                }
            }
            if (selectedTopicId != null && selectedTopicId != 0) {
                result = result.filter { item ->
                    item.messageThreadId == selectedTopicId.toLong() ||
                    (selectedTopicId == 1 && (item.messageThreadId == null || item.messageThreadId == 0L || item.messageThreadId == 1L))
                }
            }
            val cleanQuery = quickSearchQuery.trim()
            if (cleanQuery.isNotEmpty()) {
                result = result.filter { item ->
                    item.title.contains(cleanQuery, ignoreCase = true) ||
                        (item.fileName?.contains(cleanQuery, ignoreCase = true) == true) ||
                        (item.seriesTitle?.contains(cleanQuery, ignoreCase = true) == true) ||
                        (item.topicName?.contains(cleanQuery, ignoreCase = true) == true) ||
                        (item.tmdbTitle?.contains(cleanQuery, ignoreCase = true) == true)
                }
            }
            if (filterYear != null) {
                result = result.filter { item ->
                    val cal = java.util.Calendar.getInstance().apply {
                        timeInMillis = item.date * 1000L
                    }
                    val y = cal.get(java.util.Calendar.YEAR)
                    val m = cal.get(java.util.Calendar.MONTH) + 1
                    y == filterYear && (filterMonth == null || m == filterMonth)
                }
            }
            result = when (sortOrder) {
                SortOrder.DATE_DESC -> result.sortedByDescending { it.date }
                SortOrder.DATE_ASC -> result.sortedBy { it.date }
                SortOrder.SIZE_DESC -> result.sortedByDescending { it.fileSizeBytes }
                SortOrder.SIZE_ASC -> result.sortedBy { it.fileSizeBytes }
            }
            return result
        }

    val seriesGroups: List<SeriesGroup>
        get() {
            val selectedTopicName = selectedTopicId?.let { id ->
                availableTopics.firstOrNull { it.id == id }?.name
            }
            return groupMediaIntoSeries(filteredAndSortedItems, selectedTopicName)
        }

    val topicGroups: Map<String, List<MediaItem>>
        get() {
            val topicNameMap = availableTopics.associate { it.id.toLong() to it.name }
            return items
                .groupBy { item ->
                    item.topicName?.takeIf { it.isNotBlank() }
                        ?: item.messageThreadId?.let { topicNameMap[it] }
                        ?: (if (item.messageThreadId != null && item.messageThreadId != 0L) "Topic #${item.messageThreadId}" else "General")
                }
        }

    val topicSeriesGroups: Map<String, List<SeriesGroup>>
        get() {
            return topicGroups.mapValues { (topicTitle, topicVideos) ->
                groupMediaIntoSeries(topicVideos, topicTitle)
            }
        }
}

/**
 * Groups videos into series within each topic or chat.
 * Videos with explicit series titles use them; otherwise they group under their topic name or default title.
 * Episodes within each series are ordered by season number, episode number, and date.
 */
fun groupMediaIntoSeries(
    items: List<MediaItem>,
    defaultSeriesTitle: String? = null
): List<SeriesGroup> {
    val cleanDefault = defaultSeriesTitle?.trim()?.takeIf {
        it.isNotBlank() && !it.equals("general", ignoreCase = true) && !it.startsWith("topic-", ignoreCase = true)
    }
    return items
        .filter { it.type == MediaType.Video || it.fileId != null }
        .mapNotNull { item ->
            val hasEpisodeInfo = item.seasonNumber != null || item.episodeNumber != null
            val isKnownSeries = hasEpisodeInfo || !item.seriesTitle.isNullOrBlank()
            val parsedTitle = (if (isKnownSeries) item.tmdbTitle?.trim()?.takeIf { it.isNotBlank() } else null)
                ?: item.seriesTitle?.trim()?.takeIf { it.isNotBlank() }
            val series = when {
                parsedTitle != null -> parsedTitle
                hasEpisodeInfo -> {
                    item.topicName?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("general", ignoreCase = true) && !it.startsWith("topic-", ignoreCase = true)
                    } ?: cleanDefault ?: "Series"
                }
                else -> null
            }
            if (series != null) item to series else null
        }
        .groupBy({ it.second }, { it.first })
        .map { (title, eps) ->
            val sortedEps = eps.sortedWith(
                compareBy<MediaItem> { it.seasonNumber ?: 1 }
                    .thenBy { it.episodeNumber ?: 0 }
                    .thenBy { it.date }
            )
            val seasons = sortedEps.mapNotNull { it.seasonNumber }.distinct().size.coerceAtLeast(1)
            SeriesGroup(title = title, episodes = sortedEps, totalSeasons = seasons)
        }
        .sortedBy { it.title }
}

enum class ChatSourceKind(val label: String) {
    SAVED_MESSAGES("Saved"),
    PRIVATE("Private"),
    GROUP("Group"),
    CHANNEL("Channel"),
    SECRET("Secret chat"),
    OTHER("Chat")
}

data class VideoChatItem(
    val chatId: Long,
    val title: String,
    val isSavedMessages: Boolean,
    val isSelected: Boolean = false,
    val kind: ChatSourceKind = if (isSavedMessages) ChatSourceKind.SAVED_MESSAGES else ChatSourceKind.OTHER,
    val isArchived: Boolean = false,
    val isForum: Boolean = false,
    val topicsCount: Int = 0
)

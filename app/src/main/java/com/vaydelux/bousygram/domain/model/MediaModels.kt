package com.vaydelux.bousygram.domain.model

import java.io.Serializable

const val SAVED_MESSAGES_TITLE = "Saved Messages"

enum class MediaType {
    Photo,
    Video
}

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
    val mimeType: String? = null
) : Serializable

enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    SIZE_DESC,
    SIZE_ASC
}

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
    val filterMonth: Int? = null
) {
    val filteredAndSortedItems: List<MediaItem>
        get() {
            var result = items
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
    val isArchived: Boolean = false
)

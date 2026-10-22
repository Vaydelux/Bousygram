package com.vaydelux.bousygram.presentation.preview

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType

private val unsplash16x9 = listOf(
    "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=640&h=360&fit=crop",
    "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=640&h=360&fit=crop",
    "https://images.unsplash.com/photo-1478720568477-152d9b164e63?w=640&h=360&fit=crop",
    "https://images.unsplash.com/photo-1594909122841-8cb533c69121?w=640&h=360&fit=crop",
    "https://images.unsplash.com/photo-1574267432644-f610f4b0a881?w=640&h=360&fit=crop"
)

/** Mock items for UI preview; thumbnailPath holds HTTPS URLs for Coil. */
fun mockMediaItems(): List<MediaItem> = unsplash16x9.mapIndexed { index, url ->
    MediaItem(
        chatId = 1L,
        messageId = index.toLong() + 1,
        date = 1_700_000_000 + index,
        type = MediaType.Video,
        title = "Preview Video ${index + 1}",
        fileId = 1000 + index,
        thumbnailPath = url,
        durationSeconds = 120 + index * 30,
        fileSizeBytes = 50_000_000L
    )
}

fun mockChannelLabel(): String = "Preview Channel"

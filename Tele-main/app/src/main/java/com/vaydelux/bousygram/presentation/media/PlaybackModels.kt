package com.vaydelux.bousygram.presentation.media

data class PlaybackRequest(
    val url: String,
    val fileId: Int,
    val title: String,
    val queue: List<PlaybackQueueItem> = emptyList(),
    val subtitleUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val durationSeconds: Int = 0
)

data class PlaybackQueueItem(
    val url: String,
    val title: String,
    val fileId: Int,
    val fileName: String? = null,
    val mimeType: String? = null,
    val durationSeconds: Int = 0
)

data class PlayerHudState(
    val title: String,
    val fileName: String?,
    val positionMs: Long,
    val durationMs: Long,
    val durationTrusted: Boolean,
    val speed: Float,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isOpening: Boolean = false,
    val isReconnecting: Boolean = false,
    val retryCount: Int = 0,
    val error: String?,
    val trackLabel: String,
    val cacheStateLabel: String? = null
)

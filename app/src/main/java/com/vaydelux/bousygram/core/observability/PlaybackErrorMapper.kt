package com.vaydelux.bousygram.core.observability

import androidx.media3.common.PlaybackException

enum class PlaybackRecoveryAction {
    Reconnect,
    LowerQuality,
    CacheFirst,
    Skip,
    ReportDiagnostics
}

data class PlaybackErrorResolution(
    val title: String,
    val message: String,
    val primaryAction: PlaybackRecoveryAction,
    val secondaryAction: PlaybackRecoveryAction? = null,
    val recoverable: Boolean
)

object PlaybackErrorMapper {
    fun fromPlaybackException(error: PlaybackException, retryCount: Int): PlaybackErrorResolution {
        val detail = throwableText(error)
        val lower = detail.lowercase()
        return when {
            "flood_wait" in lower -> PlaybackErrorResolution(
                title = "Telegram is rate limiting requests",
                message = "Bousygram will reconnect after Telegram allows more chunk requests.",
                primaryAction = PlaybackRecoveryAction.Reconnect,
                secondaryAction = PlaybackRecoveryAction.CacheFirst,
                recoverable = true
            )
            "still caching" in lower -> PlaybackErrorResolution(
                title = "Telegram is still preparing this range",
                message = "The stream is valid, but Telegram has not cached the requested bytes yet.",
                primaryAction = if (retryCount >= 2) PlaybackRecoveryAction.CacheFirst else PlaybackRecoveryAction.Reconnect,
                secondaryAction = PlaybackRecoveryAction.LowerQuality,
                recoverable = true
            )
            "source error" in lower -> PlaybackErrorResolution(
                title = "Video is still downloading",
                message = "Telegram is still sending this part. Playback keeps the same download and tries again.",
                primaryAction = PlaybackRecoveryAction.Reconnect,
                secondaryAction = PlaybackRecoveryAction.CacheFirst,
                recoverable = true
            )
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> PlaybackErrorResolution(
                    title = "This video format is not supported on this device",
                    message = "Try a lower-quality copy if available, or skip this item.",
                    primaryAction = PlaybackRecoveryAction.LowerQuality,
                    secondaryAction = PlaybackRecoveryAction.Skip,
                    recoverable = true
                )
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackErrorResolution(
                    title = "Video is still downloading",
                    message = "Telegram is still sending this part. Playback keeps the same download and tries again.",
                    primaryAction = PlaybackRecoveryAction.Reconnect,
                    secondaryAction = PlaybackRecoveryAction.CacheFirst,
                    recoverable = true
                )
            else -> PlaybackErrorResolution(
                title = "Playback failed",
                message = detail.ifBlank { "Unexpected runtime error while playing this media." },
                primaryAction = PlaybackRecoveryAction.Reconnect,
                secondaryAction = PlaybackRecoveryAction.ReportDiagnostics,
                recoverable = true
            )
        }
    }

    private fun throwableText(error: Throwable): String {
        val parts = ArrayList<String>(4)
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 6) {
            current.message?.takeIf { it.isNotBlank() }?.let(parts::add)
            current = current.cause
            depth++
        }
        return parts.joinToString(" | ")
    }
}

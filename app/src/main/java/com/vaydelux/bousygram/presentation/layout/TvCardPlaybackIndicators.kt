package com.vaydelux.bousygram.presentation.layout

import com.vaydelux.bousygram.data.repository.ContinueWatchingEntry

/**
 * Pure card presentation policy. A completed entry has no Resume pill, and the
 * account-scoped progress ledger is authoritative over legacy preference positions.
 */
internal data class TvCardPlaybackIndicators(
    val watched: Boolean,
    val resumeFraction: Float
)

internal fun tvCardPlaybackIndicators(
    entry: ContinueWatchingEntry?,
    fallbackResumeMs: Long,
    knownDurationMs: Long
): TvCardPlaybackIndicators {
    val durationMs = knownDurationMs.takeIf { it > 0L }
        ?: entry?.durationMs?.takeIf { it > 0L }
        ?: 0L
    val watched = entry?.completed == true ||
        (entry != null && durationMs > 0L &&
            entry.positionMs >= (durationMs * 0.90).toLong())
    if (watched || durationMs <= 0L) {
        return TvCardPlaybackIndicators(watched = watched, resumeFraction = 0f)
    }
    val positionMs = (entry?.positionMs ?: fallbackResumeMs).coerceAtLeast(0L)
    val progress = (positionMs.toDouble() / durationMs.toDouble())
        .coerceIn(0.0, 1.0).toFloat()
    return TvCardPlaybackIndicators(watched = false, resumeFraction = progress)
}

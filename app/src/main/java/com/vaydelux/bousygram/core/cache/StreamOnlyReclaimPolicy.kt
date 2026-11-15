package com.vaydelux.bousygram.core.cache

/**
 * Recovery/retry can still be streaming the same file. A normal exit or track
 * change may schedule reclaim, but deletion still needs a fresh TDLib status
 * and the active-file/pin/lease protections immediately before DeleteFile.
 */
internal fun shouldScheduleStreamOnlyReclaim(
    enabled: Boolean,
    fileId: Int,
    downloadsCancelled: Boolean,
    stopWasRecovery: Boolean
): Boolean = enabled && fileId > 0 && downloadsCancelled && !stopWasRecovery

internal fun isStreamOnlyReclaimCandidate(
    downloadedBytes: Long,
    path: String?,
    isDownloading: Boolean,
    protectedByApp: Boolean
): Boolean = downloadedBytes > 0L && !path.isNullOrBlank() &&
    !isDownloading && !protectedByApp

/** Bounded, fail-safe reclaim decisions. Only temporary, idle TDLib media is deleted. */
internal enum class StreamOnlyReclaimDecision { DELETE, RETRY, STOP }

internal fun decideStreamOnlyReclaim(
    offlinePinned: Boolean,
    activePlayback: Boolean,
    otherActiveConsumer: Boolean,
    fileResolved: Boolean,
    downloadedBytes: Long,
    path: String?,
    isDownloading: Boolean
): StreamOnlyReclaimDecision = when {
    offlinePinned || activePlayback -> StreamOnlyReclaimDecision.STOP
    otherActiveConsumer || !fileResolved || isDownloading -> StreamOnlyReclaimDecision.RETRY
    isStreamOnlyReclaimCandidate(downloadedBytes, path, false, false) ->
        StreamOnlyReclaimDecision.DELETE
    else -> StreamOnlyReclaimDecision.STOP
}

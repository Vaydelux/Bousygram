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

package com.vaydelux.bousygram.core.cache

/**
 * Determines which TDLib video payloads can be reclaimed when a quota sweep
 * finds storage pressure. The caller must also verify that the file has a
 * valid TDLib-managed path, nonzero downloaded bytes, and a recent-use grace.
 *
 * Important: Continue Watching and last-played checkpoints are *metadata*.
 * They must survive DeleteFile, but do not authorize indefinite retention of
 * their video bytes. The next playback may re-download the missing ranges.
 */
internal fun canEvictTemporaryMedia(
    offlinePinned: Boolean,
    activePlayback: Boolean,
    cacheAheadActive: Boolean,
    activeRange: Boolean,
    downloading: Boolean,
    focusPrefetch: Boolean
): Boolean =
    !offlinePinned && !activePlayback && !cacheAheadActive &&
        !activeRange && !downloading && !focusPrefetch

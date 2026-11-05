package com.vaydelux.bousygram.core.media

/** Native buffering notifications cannot override user Pause or active playback. */
internal fun libVlcBufferState(userWantsPlay: Boolean, nativePlaying: Boolean): EnginePlaybackState =
    if (nativePlaying || !userWantsPlay) EnginePlaybackState.Ready
    else EnginePlaybackState.Buffering

/**
 * libvlc may deliver Playing, Stopped or EndReached after an owner-initiated stop.
 * Events without a currently prepared media item must not resurrect Idle/Error.
 */
internal fun acceptsLibVlcMediaEvent(
    ownerStopped: Boolean,
    filePrepared: Boolean,
    released: Boolean
): Boolean = !ownerStopped && filePrepared && !released

/**
 * Old Stopped/EndReached events during a new prepare must not become an
 * erroneous stream error or auto-next before the new media starts playing.
 */
internal fun shouldReportLibVlcEnded(
    activeMedia: Boolean,
    playbackStarted: Boolean
): Boolean = activeMedia && playbackStarted

internal fun shouldReportLibVlcUnexpectedStop(
    activeMedia: Boolean,
    playbackStarted: Boolean,
    alreadyEnded: Boolean
): Boolean = activeMedia && playbackStarted && !alreadyEnded

/** Seek timestamps are native milliseconds; never overflow or exceed known duration. */
internal fun boundedLibVlcSeekMs(requestedMs: Long, durationMs: Long): Long =
    if (durationMs > 0L) requestedMs.coerceIn(0L, durationMs)
    else requestedMs.coerceAtLeast(0L)

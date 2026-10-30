package com.vaydelux.bousygram.core.media

/** Audio-only items have no video frame event. Requiring onRenderedFirstFrame would strand them. */
internal fun hasConfirmedAudioOutput(
    playbackState: EnginePlaybackState,
    tracks: List<MediaTrackInfo>,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    declaredAudioOnly: Boolean = false
): Boolean {
    if (playbackState !is EnginePlaybackState.Ready) return false
    val selectedAudio = tracks.any {
        it.type == MediaTrackInfo.TrackType.AUDIO && it.isSelected
    }
    val selectedVideo = tracks.any {
        it.type == MediaTrackInfo.TrackType.VIDEO && it.isSelected
    }
    // If media metadata declares audio-only, a track-list update may arrive after Ready.
    // In every other case, require an actual selected audio track before allowing readiness.
    val audioOnly = (selectedAudio && !selectedVideo) ||
        (declaredAudioOnly && !selectedVideo)
    return audioOnly && (isPlaying || !playWhenReady)
}

/** Prevent the startup watchdog from classifying an already-ready audio stream as a black screen. */
internal fun hasInitialMediaOutput(
    firstFrameMs: Long,
    firstAudioReadyMs: Long,
    firstProgressReadyMs: Long = 0L
): Boolean = firstFrameMs > 0L || firstAudioReadyMs > 0L || firstProgressReadyMs > 0L

/**
 * Some Surface/MPV combinations miss a first-frame callback even though video is decoding
 * and time advances. Two observed positions must prove natural progress (not a single seek).
 * Decoder dimensions alone or STATE_READY alone never count as visible playback.
 */
internal fun hasConfirmedVideoProgress(
    previousPositionMs: Long,
    currentPositionMs: Long,
    playing: Boolean,
    hasVideoDimensions: Boolean
): Boolean = playing && hasVideoDimensions &&
    previousPositionMs >= 0L &&
    currentPositionMs >= previousPositionMs + 2_000L

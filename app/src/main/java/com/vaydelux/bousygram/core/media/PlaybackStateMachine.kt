package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic state machine governing the playback lifecycle.
 * Valid transitions strictly follow:
 * Idle -> Preparing -> Buffering -> Playing / Paused -> Ending -> Released
 * Any active state can transition to Error or Idle/Released upon teardown.
 */
sealed interface PlaybackCoreState {
    data object Idle : PlaybackCoreState
    data class Preparing(val session: PlaybackSession, val queueItem: PlaybackQueueItem?) : PlaybackCoreState
    data class Buffering(val session: PlaybackSession, val positionMs: Long, val durationMs: Long) : PlaybackCoreState
    data class Playing(val session: PlaybackSession, val positionMs: Long, val durationMs: Long) : PlaybackCoreState
    data class Paused(val session: PlaybackSession, val positionMs: Long, val durationMs: Long) : PlaybackCoreState
    data class Ending(val session: PlaybackSession, val queueItem: PlaybackQueueItem?) : PlaybackCoreState
    data class Error(val session: PlaybackSession?, val message: String, val cause: Throwable? = null) : PlaybackCoreState
    data object Released : PlaybackCoreState
}

class PlaybackStateMachine {
    private val _state = MutableStateFlow<PlaybackCoreState>(PlaybackCoreState.Idle)
    val state: StateFlow<PlaybackCoreState> = _state.asStateFlow()

    val currentState: PlaybackCoreState get() = _state.value

    val isPlaying: Boolean
        get() = _state.value is PlaybackCoreState.Playing

    val isBuffering: Boolean
        get() = _state.value is PlaybackCoreState.Buffering

    val isPreparing: Boolean
        get() = _state.value is PlaybackCoreState.Preparing

    val isPaused: Boolean
        get() = _state.value is PlaybackCoreState.Paused

    val isEnded: Boolean
        get() = _state.value is PlaybackCoreState.Ending

    @Synchronized
    fun onPreparing(session: PlaybackSession, item: PlaybackQueueItem?): Boolean {
        _state.value = PlaybackCoreState.Preparing(session, item)
        return true
    }

    @Synchronized
    fun onBuffering(session: PlaybackSession, positionMs: Long, durationMs: Long): Boolean {
        when (_state.value) {
            is PlaybackCoreState.Preparing,
            is PlaybackCoreState.Buffering,
            is PlaybackCoreState.Playing,
            is PlaybackCoreState.Paused -> {
                _state.value = PlaybackCoreState.Buffering(
                    session = session,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L)
                )
                return true
            }
            else -> return false
        }
    }

    @Synchronized
    fun onPlaying(session: PlaybackSession, positionMs: Long, durationMs: Long): Boolean {
        when (_state.value) {
            is PlaybackCoreState.Preparing,
            is PlaybackCoreState.Buffering,
            is PlaybackCoreState.Playing,
            is PlaybackCoreState.Paused -> {
                _state.value = PlaybackCoreState.Playing(
                    session = session,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L)
                )
                return true
            }
            else -> return false
        }
    }

    @Synchronized
    fun onPaused(session: PlaybackSession, positionMs: Long, durationMs: Long): Boolean {
        when (_state.value) {
            is PlaybackCoreState.Preparing,
            is PlaybackCoreState.Buffering,
            is PlaybackCoreState.Playing,
            is PlaybackCoreState.Paused -> {
                _state.value = PlaybackCoreState.Paused(
                    session = session,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L)
                )
                return true
            }
            else -> return false
        }
    }

    @Synchronized
    fun onEnding(session: PlaybackSession, item: PlaybackQueueItem?): Boolean {
        _state.value = PlaybackCoreState.Ending(session, item)
        return true
    }

    @Synchronized
    fun onError(session: PlaybackSession?, message: String, cause: Throwable? = null): Boolean {
        _state.value = PlaybackCoreState.Error(session, message, cause)
        return true
    }

    @Synchronized
    fun onResetToIdle(): Boolean {
        _state.value = PlaybackCoreState.Idle
        return true
    }

    @Synchronized
    fun onReleased(): Boolean {
        _state.value = PlaybackCoreState.Released
        return true
    }
}

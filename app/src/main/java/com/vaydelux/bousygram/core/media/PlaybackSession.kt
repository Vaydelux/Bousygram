package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sealed playback state for [PlaybackSession].
 * UI observes this state machine directly.
 */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Preparing(val item: PlaybackQueueItem) : PlaybackState
    data class Playing(val item: PlaybackQueueItem) : PlaybackState
    data class Ended(val item: PlaybackQueueItem) : PlaybackState
    data class Error(val item: PlaybackQueueItem?, val message: String, val cause: Throwable? = null) : PlaybackState
}

/**
 * Single PlaybackSession owning the queue and sealed playback state machine (Idle, Preparing, Playing, Ended, Error).
 * Next and previous are pure queue operations, not navigation.
 * Automatically cancels previous video's downloads upon switching tracks.
 */
class PlaybackSession(
    val queue: List<PlaybackQueueItem>,
    initialIndex: Int = 0,
    val sessionId: String = "pb-${System.currentTimeMillis()}",
    val startedAtMs: Long = System.currentTimeMillis(),
    startPositionMs: Long = 0L,
    private val onCancelDownloads: ((fileId: Int) -> Unit)? = null
) {
    sealed interface State {
        data object Idle : State
        data class Preparing(val item: PlaybackQueueItem) : State
        data class Playing(val item: PlaybackQueueItem) : State
        data class Ended(val item: PlaybackQueueItem) : State
        data class Error(val item: PlaybackQueueItem?, val message: String, val cause: Throwable? = null) : State
    }

    private val _currentIndex = MutableStateFlow(
        if (queue.isNotEmpty()) initialIndex.coerceIn(0, queue.lastIndex) else 0
    )
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _state = MutableStateFlow<PlaybackState>(
        if (queue.isNotEmpty() && initialIndex in queue.indices) {
            PlaybackState.Preparing(queue[initialIndex])
        } else {
            PlaybackState.Idle
        }
    )
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val currentItem: PlaybackQueueItem?
        get() = queue.getOrNull(_currentIndex.value)

    val fileId: Int
        get() = currentItem?.fileId ?: 0

    val hasNext: Boolean
        get() = _currentIndex.value < queue.lastIndex

    val hasPrevious: Boolean
        get() = _currentIndex.value > 0

    var currentPositionMs: Long = startPositionMs
        internal set
    var currentOffsetBytes: Long = 0L
        internal set
    var retryCount: Int = 0
        internal set
    var consecutiveRangeStalls: Int = 0
        internal set
    var lastStallReason: PlaybackStallReason = PlaybackStallReason.None
        internal set
    var lastStopReason: PlaybackCacheStopReason = PlaybackCacheStopReason.None
        internal set
    private val stopped = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val transitionLock = Any()
    private val _isTransitioning = AtomicBoolean(false)
    val isTransitioning: Boolean get() = _isTransitioning.get()

    fun finishTransition() {
        _isTransitioning.set(false)
    }

    /**
     * Backward-compatible constructor for single-file sessions.
     */
    internal constructor(
        sessionId: String,
        fileId: Int,
        startedAtMs: Long,
        startPositionMs: Long
    ) : this(
        queue = listOf(
            PlaybackQueueItem(
                chatId = 0L,
                messageId = 0L,
                title = "",
                fileId = fileId
            )
        ),
        initialIndex = 0,
        sessionId = sessionId,
        startedAtMs = startedAtMs,
        startPositionMs = startPositionMs
    )

    internal fun markStoppedOnce(): Boolean = stopped.compareAndSet(false, true)

    fun isStopped(): Boolean = stopped.get()

    /**
     * Advances to the next video in the queue as an in-place queue operation without navigation.
     * Cancels downloads for the previous video.
     * Returns true if queue advanced, false if already at the end or if a transition is currently in flight.
     */
    fun next(): Boolean {
        synchronized(transitionLock) {
            if (!hasNext || _isTransitioning.get()) return false
            _isTransitioning.set(true)
            val prevFileId = currentItem?.fileId ?: 0
            val nextIdx = _currentIndex.value + 1
            _currentIndex.value = nextIdx
            currentPositionMs = 0L
            retryCount = 0
            if (prevFileId > 0) {
                onCancelDownloads?.invoke(prevFileId)
            }
            val nextItem = queue[nextIdx]
            _state.value = PlaybackState.Preparing(nextItem)
            return true
        }
    }

    /**
     * Steps to the previous video in the queue as an in-place queue operation without navigation.
     * Cancels downloads for the previous video.
     * Returns true if queue stepped back, false if already at the beginning or if a transition is currently in flight.
     */
    fun previous(): Boolean {
        synchronized(transitionLock) {
            if (!hasPrevious || _isTransitioning.get()) return false
            _isTransitioning.set(true)
            val prevFileId = currentItem?.fileId ?: 0
            val prevIdx = _currentIndex.value - 1
            _currentIndex.value = prevIdx
            currentPositionMs = 0L
            retryCount = 0
            if (prevFileId > 0) {
                onCancelDownloads?.invoke(prevFileId)
            }
            val prevItem = queue[prevIdx]
            _state.value = PlaybackState.Preparing(prevItem)
            return true
        }
    }

    /**
     * Jumps directly to a specific track index in the queue.
     * Cancels downloads for the previous video.
     */
    fun goToIndex(newIndex: Int): Boolean {
        synchronized(transitionLock) {
            if (newIndex !in queue.indices || newIndex == _currentIndex.value || _isTransitioning.get()) return false
            _isTransitioning.set(true)
            val prevFileId = currentItem?.fileId ?: 0
            _currentIndex.value = newIndex
            currentPositionMs = 0L
            retryCount = 0
            if (prevFileId > 0) {
                onCancelDownloads?.invoke(prevFileId)
            }
            val item = queue[newIndex]
            _state.value = PlaybackState.Preparing(item)
            return true
        }
    }

    /**
     * Advances on track completion. Returns true if advanced to next, false if ended queue.
     */
    fun onAutoNext(): Boolean {
        synchronized(transitionLock) {
            return if (hasNext) {
                next()
            } else {
                val current = currentItem
                if (current != null) {
                    _state.value = PlaybackState.Ended(current)
                } else {
                    _state.value = PlaybackState.Idle
                }
                _isTransitioning.set(false)
                false
            }
        }
    }

    fun onPreparing(item: PlaybackQueueItem? = currentItem) {
        val target = item ?: currentItem ?: return
        _state.value = PlaybackState.Preparing(target)
    }

    fun onPlaying(item: PlaybackQueueItem? = currentItem) {
        val target = item ?: currentItem ?: return
        _state.value = PlaybackState.Playing(target)
        _isTransitioning.set(false)
    }

    fun onEnded(item: PlaybackQueueItem? = currentItem) {
        val target = item ?: currentItem ?: return
        _state.value = PlaybackState.Ended(target)
        _isTransitioning.set(false)
    }

    fun onError(message: String, cause: Throwable? = null, item: PlaybackQueueItem? = currentItem) {
        _state.value = PlaybackState.Error(item ?: currentItem, message, cause)
        _isTransitioning.set(false)
    }

    fun release() {
        markStoppedOnce()
        if (released.compareAndSet(false, true)) {
            _isTransitioning.set(false)
            val activeId = fileId
            if (activeId > 0) {
                onCancelDownloads?.invoke(activeId)
            }
            _state.value = PlaybackState.Idle
        }
    }
}

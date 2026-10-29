package com.vaydelux.bousygram.presentation.media.launch

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.media.PlaybackRequest
import com.vaydelux.bousygram.presentation.media.buildPlaybackRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Stable identity of the card that started a launch, used to restore focus on return. */
data class LaunchAnchor(
    val screenKey: String,
    val sectionKey: String? = null,
    val mediaKey: String
)

/** Observable launch state for UI feedback. */
data class LaunchState(
    val pendingKey: String? = null,
    /** True once preparation has exceeded the indicator delay (~120 ms). */
    val showIndicator: Boolean = false
) {
    val isPending: Boolean get() = pendingKey != null
}

sealed interface LaunchOutcome {
    /** Navigate to the player. [prepared] is false when the player must prepare the stream itself. */
    data class Open(val request: PlaybackRequest, val prepared: Boolean) : LaunchOutcome
    /** Only for media that can never play (e.g. no Telegram file). */
    data class Rejected(val reason: String) : LaunchOutcome
    data object Cancelled : LaunchOutcome
    data object Duplicate : LaunchOutcome
}

/** Bounded stream preflight. Returns a ready URL, or null when the player should prepare it. */
fun interface StreamPreflight {
    suspend fun prepare(item: MediaItem, deadlineAtMs: Long): String?
}

/**
 * Single launch path for every media entry point (Home, Library, Search, Favorites, details).
 *
 * Preflight is capped at [preflightBudgetMs]; afterwards the player always opens and owns loading,
 * retry, and error handling. Only genuinely unplayable items are rejected.
 */
class PlaybackLaunchCoordinator(
    private val preflight: StreamPreflight,
    private val preflightBudgetMs: Long = PREFLIGHT_BUDGET_MS,
    private val indicatorDelayMs: Long = INDICATOR_DELAY_MS,
    private val duplicateWindowMs: Long = DUPLICATE_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onBeforeOpen: (MediaItem) -> Unit = {}
) {
    private val _state = MutableStateFlow(LaunchState())
    val state: StateFlow<LaunchState> = _state.asStateFlow()

    private val anchors = java.util.concurrent.ConcurrentHashMap<String, LaunchAnchor>()
    private var job: Job? = null
    private var pendingCallback: ((LaunchOutcome) -> Unit)? = null
    private var generation = 0L
    private var lastOpenAtMs: Long? = null

    /**
     * Starts a launch. [onOutcome] is invoked exactly once on the caller's coroutine context.
     * Returns true when a launch was started.
     */
    fun launch(
        scope: CoroutineScope,
        item: MediaItem,
        queue: List<MediaItem>,
        anchor: LaunchAnchor? = null,
        onOutcome: (LaunchOutcome) -> Unit
    ): Boolean {
        val fileId = item.fileId
        if (fileId == null || fileId <= 0) {
            onOutcome(LaunchOutcome.Rejected("This item has no playable Telegram file"))
            return false
        }
        if (_state.value.isPending) {
            onOutcome(LaunchOutcome.Duplicate)
            return false
        }
        val lastOpen = lastOpenAtMs
        if (lastOpen != null && clock() - lastOpen < duplicateWindowMs) {
            onOutcome(LaunchOutcome.Duplicate)
            return false
        }

        val launchGeneration = ++generation
        val key = mediaKey(item)
        anchor?.let { anchors[it.screenKey] = it }
        _state.value = LaunchState(pendingKey = key)
        pendingCallback = onOutcome

        job = scope.launch {
            val indicator = launch {
                delay(indicatorDelayMs)
                if (launchGeneration == generation && _state.value.pendingKey == key) {
                    _state.value = _state.value.copy(showIndicator = true)
                }
            }
            try {
                val deadline = clock() + preflightBudgetMs
                // The preflight runs as a separate child so a blocking implementation can never
                // hold navigation past the budget; the deadline stops it from minting late tokens.
                val attempt = async {
                    try {
                        preflight.prepare(item, deadline)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null // Transient Telegram failure: the player retries.
                    }
                }
                val url = withTimeoutOrNull(preflightBudgetMs) { attempt.await() }
                if (url == null) attempt.cancel()
                indicator.cancel()
                if (launchGeneration != generation) return@launch
                _state.value = LaunchState()
                pendingCallback = null
                lastOpenAtMs = clock()
                // Optional progress persistence must not make playable media fail to open.
                runCatching { onBeforeOpen(item) }
                onOutcome(
                    LaunchOutcome.Open(
                        request = buildPlaybackRequest(item, url, queueWithSelected(item, queue)),
                        prepared = !url.isNullOrBlank()
                    )
                )
            } finally {
                indicator.cancel()
                // If the hosting scope died mid-launch, never leave the UI stuck in "pending".
                if (launchGeneration == generation && _state.value.pendingKey == key) {
                    _state.value = LaunchState()
                    pendingCallback = null
                }
            }
        }
        return true
    }

    /** Cancels a pending launch (Back during the preflight window). Returns true if one was pending. */
    fun cancel(): Boolean {
        if (!_state.value.isPending) return false
        generation++
        val callback = pendingCallback
        pendingCallback = null
        job?.cancel()
        job = null
        _state.value = LaunchState()
        callback?.invoke(LaunchOutcome.Cancelled)
        return true
    }

    /** Anchor saved for [screenKey] by the last launch from that screen. */
    fun anchorFor(screenKey: String): LaunchAnchor? = anchors[screenKey]

    fun consumeAnchor(screenKey: String): LaunchAnchor? = anchors.remove(screenKey)

    /** Test hook / logout: forget duplicate-window and anchors. */
    fun reset() {
        cancel()
        lastOpenAtMs = null
        anchors.clear()
    }

    companion object {
        const val PREFLIGHT_BUDGET_MS = 500L
        const val INDICATOR_DELAY_MS = 120L
        const val DUPLICATE_WINDOW_MS = 600L

        fun mediaKey(item: MediaItem): String = "${item.chatId}:${item.messageId}"

        /** The selected Telegram message is always present in the queue. */
        fun queueWithSelected(item: MediaItem, queue: List<MediaItem>): List<MediaItem> =
            if (queue.any { it.chatId == item.chatId && it.messageId == item.messageId }) queue
            else listOf(item) + queue
    }
}

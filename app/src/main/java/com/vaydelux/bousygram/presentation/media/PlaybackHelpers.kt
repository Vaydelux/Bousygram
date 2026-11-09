package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.core.media.PlaybackRetryDecision
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import com.vaydelux.bousygram.domain.model.MediaUiState

/**
 * Poll decoder progress frequently while controls or seek feedback are visible,
 * but avoid waking the entire Compose playback screen twice per second during
 * ordinary full-screen viewing. Buffering retains the previous 500 ms cadence
 * so recovery and progress diagnostics remain responsive.
 *
 * This changes only UI/maintenance sampling; the TDLib streaming scheduler,
 * native player clock and Media3 playback callbacks continue independently.
 */
/**
 * A recoverable streaming error may retry the same quality only a bounded
 * number of times. Cache-first retries get two attempts, ordinary reconnects
 * one; a lower-quality or stop decision is never a same-quality retry.
 *
 * No backoff loop or pending playback intent is created here.
 */
internal fun shouldRetryCurrentQuality(
    retryCount: Int,
    decision: PlaybackRetryDecision
): Boolean = retryCount >= 0 && when (decision) {
    PlaybackRetryDecision.RetrySameQuality -> retryCount < 1
    PlaybackRetryDecision.CacheFirstRetry -> retryCount < 2
    PlaybackRetryDecision.LowerQuality,
    PlaybackRetryDecision.SkipOrReport -> false
}

internal fun playbackUiPollingDelayMs(
    isHudVisible: Boolean,
    isSeeking: Boolean,
    isPreparingOrBuffering: Boolean
): Long = when {
    isHudVisible || isSeeking -> 250L
    isPreparingOrBuffering -> 500L
    else -> 1_000L
}

/**
 * Separates resume checkpoint policy from Compose frame updates.
 *
 * Saving only when a position grows by 5 seconds silently loses backward seeks
 * if the process dies before Back/ON_PAUSE. Use monotonic elapsed time for normal
 * checkpoints, and persist immediately after a confirmed large discontinuity.
 * A zero position is only persisted following an explicit Start Over, never
 * during the initial player preparation before resume has been applied.
 */
internal class PlaybackProgressCheckpoint {
    private var lastSavedPositionMs = -1L
    private var lastSavedAtElapsedMs = -1L
    private var waitingForStartOver = false

    fun reset() {
        lastSavedPositionMs = -1L
        lastSavedAtElapsedMs = -1L
        waitingForStartOver = false
    }

    fun onExplicitSeek(targetMs: Long) {
        // A requested rewind is not yet a decoded rewind. Keep saving the
        // previous playable position until the seek is actually confirmed.
        if (targetMs > 0L) waitingForStartOver = false
    }

    /** The decoder confirmed a rewind, rather than merely accepting a seek command. */
    fun onConfirmedStartOver(observedPositionMs: Long) {
        // Reset the old resume high-water mark even if playback has already
        // advanced a few seconds past zero by the next UI polling tick.
        waitingForStartOver = observedPositionMs == 0L
        lastSavedPositionMs = -1L
        lastSavedAtElapsedMs = -1L
    }

    fun shouldPersist(positionMs: Long, elapsedMs: Long, playbackReady: Boolean): Boolean {
        if (!playbackReady || positionMs < 0L) return false
        if (waitingForStartOver && positionMs != 0L) return false
        if (positionMs == 0L && !waitingForStartOver) return false
        val last = lastSavedPositionMs
        if (positionMs == last && !waitingForStartOver) return false

        val jumped = last >= 0L && (
            (positionMs > last && positionMs - last >= 8_000L) ||
            (positionMs < last && last - positionMs >= 8_000L)
        )
        val periodic = lastSavedAtElapsedMs < 0L ||
            elapsedMs < lastSavedAtElapsedMs ||
            elapsedMs - lastSavedAtElapsedMs >= 5_000L
        if (!waitingForStartOver && !jumped && !periodic) return false

        lastSavedPositionMs = positionMs
        lastSavedAtElapsedMs = elapsedMs
        waitingForStartOver = false
        return true
    }
}

/**
 * Tracks decoder output time, excluding startup, user pauses and network stalls.
 * All inputs use monotonic elapsed time from the caller, never wall-clock dates.
 */
internal class ActivePlaybackClock {
    private var activeMs = 0L
    private var changedAtMs = 0L
    private var playing = false

    fun reset(nowMs: Long) {
        activeMs = 0L
        changedAtMs = nowMs
        playing = false
    }

    fun update(playingNow: Boolean, nowMs: Long) {
        if (playing && nowMs >= changedAtMs) {
            activeMs += nowMs - changedAtMs
        }
        changedAtMs = nowMs
        playing = playingNow
    }

    fun activeDurationMs(nowMs: Long): Long =
        activeMs + if (playing && nowMs >= changedAtMs) nowMs - changedAtMs else 0L
}

/** The current buffering interval is included even if playback is disposed mid-stall. */
internal fun measuredRebufferDurationMs(
    completedMs: Long,
    activeStallStartedMs: Long,
    nowMs: Long
): Long {
    val completed = completedMs.coerceAtLeast(0L)
    if (activeStallStartedMs <= 0L) return completed
    val active = (nowMs - activeStallStartedMs).coerceAtLeast(0L)
    return if (Long.MAX_VALUE - completed < active) Long.MAX_VALUE else completed + active
}

/**
 * Native Ended is authoritative only near the end of a known timeline.
 * For MPEG-TS/other unknown-duration containers, a genuine output event plus
 * measurable playback progress is enough to accept Ended; otherwise treat
 * an immediate EOF as a transient stream failure and retry instead of
 * unexpectedly skipping to the next episode.
 */
/**
 * Premature EOF is not a real episode completion. A damaged/short Telegram
 * response can emit Ended repeatedly without any decoded progress.
 *
 * Never allow an unbounded reopen loop. Only a foreground, non-casting
 * playback attempt that the user has not paused may auto-retry.
 * Explicit Retry starts a separate new budget in the UI.
 */
internal fun shouldRetryPrematurePlaybackEnd(
    retriesSoFar: Int,
    maximumRetries: Int,
    inForeground: Boolean,
    explicitlyPaused: Boolean,
    castConnected: Boolean
): Boolean = retriesSoFar >= 0 && maximumRetries > 0 &&
    retriesSoFar < maximumRetries && inForeground &&
    !explicitlyPaused && !castConnected

internal fun isCredibleNaturalPlaybackEnd(
    durationMs: Long,
    positionMs: Long,
    hasProducedMedia: Boolean
): Boolean = when {
    !hasProducedMedia -> false
    // A fixed 5s margin wrongly rejects real EOF on 2-4 second videos and
    // treats EOF after 1s of a 6s video as natural completion. On short
    // known timelines require progress into the final quarter instead.
    durationMs > 0L -> {
        val nearEndToleranceMs = minOf(5_000L, durationMs / 4L)
        positionMs >= durationMs - nearEndToleranceMs
    }
    // Without a demuxer duration, require measurable progress rather than
    // allowing an immediate native EndReached to skip the episode.
    else -> positionMs >= 5_000L
}

/** Buffer completion starts playback only if the user has not explicitly paused. */
internal fun shouldAutoResumeAfterReady(
    isReady: Boolean,
    playWhenReady: Boolean,
    isPlaying: Boolean
): Boolean = isReady && playWhenReady && !isPlaying

/**
 * Explicit user intent, not engine buffering state, controls replay after demux/retry.
 * The foreground and active Cast guards prevent accidental audio in the background.
 */
internal fun shouldRestorePlayIntent(
    isReady: Boolean,
    explicitlyPaused: Boolean,
    inForeground: Boolean,
    castConnected: Boolean,
    isPlaying: Boolean,
    audioFocusSuppressed: Boolean = false
): Boolean = isReady && !explicitlyPaused && inForeground &&
    !castConnected && !audioFocusSuppressed && !isPlaying

/** TS has no mandatory time index; only seek after the actual demuxer declares support. */
internal fun canSeekTransportStream(
    isTransportStream: Boolean,
    engineSeekable: Boolean
): Boolean = !isTransportStream || engineSeekable

/** Keep displayed/pending seek targets identical to what the decoder can accept. */
internal fun boundedPlayerSeekTarget(targetMs: Long, knownDurationMs: Long): Long =
    if (knownDurationMs > 0L) targetMs.coerceIn(0L, knownDurationMs)
    else targetMs.coerceAtLeast(0L)

/**
 * Confirmation is based on a decoder-reported position, never a requested
 * timestamp. Avoid signed-Long subtraction overflow on malformed native clocks.
 */
internal fun isConfirmedPlayerSeekPosition(
    observedMs: Long, targetMs: Long, toleranceMs: Long = 5_000L
): Boolean {
    if (observedMs < 0L || targetMs < 0L || toleranceMs < 0L) return false
    return if (observedMs >= targetMs) observedMs - targetMs <= toleranceMs
    else targetMs - observedMs <= toleranceMs
}

/** A pending *backward* seek wins over an old high-water progress marker.
 * Otherwise reconnect would jump forward to an earlier timestamp after rewind.
 */
internal fun recoveryPositionMs(
    pendingSeekTargetMs: Long,
    enginePositionMs: Long,
    lastConfirmedMs: Long
): Long = if (pendingSeekTargetMs >= 0L) pendingSeekTargetMs
else maxOf(enginePositionMs, lastConfirmedMs).coerceAtLeast(0L)

/**
 * Snapshot the playback timeline before an engine is released. A pending seek, including
 * an explicit Start Over at 0ms, always wins over the old decoder's position and the
 * previous resume checkpoint. A resume awaiting a seek index must not be discarded.
 */
internal fun engineHandoffPositionMs(
    pendingSeekTargetMs: Long,
    enginePositionMs: Long,
    lastConfirmedMs: Long,
    pendingResumeMs: Long
): Long = if (pendingSeekTargetMs >= 0L) pendingSeekTargetMs
else maxOf(enginePositionMs, lastConfirmedMs, pendingResumeMs).coerceAtLeast(0L)

/**
 * The nullable handoff value distinguishes "resume exactly at zero" from "no handoff".
 * Without this distinction, a decoder switch after Start Over restores the old saved
 * checkpoint instead of the beginning of the media.
 */
internal fun enginePreparationResumePositionMs(
    handoffPositionMs: Long?,
    lastConfirmedMs: Long,
    savedResumeMs: Long
): Long = (handoffPositionMs?.coerceAtLeast(0L)
    ?: lastConfirmedMs.takeIf { it > 0L }
    ?: savedResumeMs).coerceAtLeast(0L)

/**
 * An interrupted audio focus is not an explicit user pause.
 * Never re-arm playback without a fresh focus-gain event.
 */
internal class AudioFocusRecoveryPolicy {
    @Volatile var isSuppressed: Boolean = false
        private set
    private var resumePending = false

    fun onFocusLost(transient: Boolean, wasPlayRequested: Boolean) {
        isSuppressed = true
        resumePending = transient && (wasPlayRequested || resumePending)
    }

    fun onFocusGained(mayResume: Boolean): Boolean {
        isSuppressed = false
        val resume = resumePending && mayResume
        resumePending = false
        return resume
    }

    fun noteExplicitPause() { resumePending = false }

    fun reset() {
        isSuppressed = false
        resumePending = false
    }
}

/** During buffering isPlaying may be false even when autoplay is armed.
 * The play/pause button must cancel that pending autoplay, not reissue play.
 */
internal fun shouldPauseOnTransportToggle(
    isPlaying: Boolean,
    playWhenReady: Boolean
): Boolean = isPlaying || playWhenReady

/**
 * Lifecycle-only playback suppression. Temporary loss of Activity foreground must
 * not erase the user's Play intent (including while the engine is buffering).
 * Explicitly paused playback never gets an automatic resume.
 */
/**
 * A prior foreground Play request is necessary but never sufficient to resume.
 * A user pause, active Cast session, or interrupted audio focus wins.
 */
internal fun shouldResumeAfterActivityReturn(
    pendingPlay: Boolean,
    explicitlyPaused: Boolean,
    castConnected: Boolean,
    audioFocusSuppressed: Boolean
): Boolean = pendingPlay && !explicitlyPaused && !castConnected &&
    !audioFocusSuppressed

internal class ForegroundPlaybackIntent {
    private var pendingResume = false

    fun onActivityPaused(playWhenReady: Boolean): Boolean {
        if (playWhenReady) pendingResume = true
        return playWhenReady
    }

    fun consumeResume(): Boolean {
        val resume = pendingResume
        pendingResume = false
        return resume
    }
}

fun playableVideos(items: List<MediaItem>): List<MediaItem> =
    items.filter { (it.type == MediaType.Video || it.type == MediaType.Audio) && it.fileId != null }

/**
 * Launch the player with the loaded SOURCE CATALOG, not a UI-filtered rail or
 * search result. For non-forum groups/channels, messageThreadId can refer to a
 * reply thread; it must not hide other videos as if it were a forum subtopic.
 *
 * This includes only already-loaded, authorized chat media and is bounded by
 * the existing browsing window; it does not issue extra TDLib requests.
 */
internal fun playbackQueueForLoadedSource(
    selected: MediaItem,
    loaded: List<MediaItem>,
    isForum: Boolean
): List<MediaItem> = playbackQueueForLoadedSource(
    selectedChatId = selected.chatId,
    selectedTopicId = selected.messageThreadId,
    loaded = loaded,
    isForum = isForum
)

internal fun normalizedForumTopicId(topicId: Long?): Long =
    topicId?.takeIf { it > 1L } ?: 0L

internal fun playbackQueueForLoadedSource(
    selectedChatId: Long,
    selectedTopicId: Long?,
    loaded: List<MediaItem>,
    isForum: Boolean
): List<MediaItem> {
    val selectedTopic = normalizedForumTopicId(selectedTopicId)
    return playableVideos(loaded)
        .asSequence()
        .filter { it.chatId == selectedChatId }
        .filter { item ->
            !isForum || normalizedForumTopicId(item.messageThreadId) == selectedTopic
        }
        .map { item ->
            // Make reply threads part of a plain channel's single source queue;
            // normalize Telegram General topic (null, 0 and 1) in forums.
            if (!isForum || selectedTopic == 0L) item.copy(messageThreadId = null)
            else item
        }
        .distinctBy { it.chatId to it.messageId }
        .toList()
}

/**
 * The player may request an older page only for the currently selected,
 * authenticated browse source, never for an unrelated Search/Favorites/Cast
 * request. Forum topic identity must match before fetching or appending.
 */
internal fun isMatchingEpisodeSource(request: PlaybackRequest, source: MediaUiState): Boolean {
    // Remain matched after the *final page* clears hasMore/cursor, so that
    // its media can still be appended to the live player queue.
    if (source.items.none { it.chatId == request.chatId }) return false
    if (source.isSavedMessagesSelected) return source.selectedChatId == null &&
        source.items.all { it.chatId == request.chatId }
    if (source.selectedChatId != request.chatId) return false
    val chat = source.videoChats.firstOrNull { it.chatId == request.chatId }
        ?: return false
    if (!chat.isForum) return source.selectedTopicId == null
    val browsedTopic = source.selectedTopicId ?: return false
    return normalizedForumTopicId(browsedTopic.toLong()) ==
        normalizedForumTopicId(request.topicId)
}

internal fun canPageOlderEpisodes(request: PlaybackRequest, source: MediaUiState): Boolean =
    isMatchingEpisodeSource(request, source) && source.hasMore &&
        source.nextFromMessageId != 0L && !source.isLoading

/**
 * Append only never-before-queued metadata. TDLib URLs are generated only
 * when a video becomes active, and the queue stays bounded on 32-bit TVs.
 */
/**
 * Bounded launch queue. Retain the actively selected item even if the source
 * list is much larger than the available device memory budget.
 */
internal fun boundedInitialEpisodeQueue(request: PlaybackRequest, capacity: Int): List<PlaybackQueueItem> {
    require(capacity > 0) { "Episode queue capacity must be positive" }
    val seeded = request.queue.ifEmpty { listOf(queueItemFromRequest(request)) }
    val selectedIndex = queueIndexForRequest(
        seeded, request.chatId, request.messageId, request.fileId
    )
    val window = if (seeded.size > capacity) {
        val first = if (selectedIndex >= 0) {
            (selectedIndex - capacity / 2).coerceIn(0, seeded.size - capacity)
        } else 0
        seeded.subList(first, first + capacity)
    } else seeded
    return if (queueIndexForRequest(window, request.chatId, request.messageId, request.fileId) >= 0) {
        window
    } else {
        listOf(queueItemFromRequest(request)) + window.take(capacity - 1)
    }
}

internal fun newEpisodesForPlayer(
    request: PlaybackRequest,
    loaded: List<MediaItem>,
    isForum: Boolean,
    alreadyQueued: List<PlaybackQueueItem>,
    capacity: Int
): List<PlaybackQueueItem> {
    if (alreadyQueued.size >= capacity) return emptyList()
    val existing = alreadyQueued.mapTo(mutableSetOf()) { it.chatId to it.messageId }
    return buildPlaybackQueue(playbackQueueForLoadedSource(
        selectedChatId = request.chatId,
        selectedTopicId = request.topicId,
        loaded = loaded,
        isForum = isForum
    )).filter { (it.chatId to it.messageId) !in existing }
        .take((capacity - alreadyQueued.size).coerceAtLeast(0))
}

fun buildPlaybackQueue(videos: List<MediaItem>): List<PlaybackQueueItem> =
    playableVideos(videos).mapNotNull { item ->
        val fileId = item.fileId ?: return@mapNotNull null
        PlaybackQueueItem(
            // Queue entries only carry metadata. PlayerScreen prepares a scoped stream URL
            // when an item actually becomes active; pre-generating URLs for every queue item
            // created unused access tokens and could invalidate an active playback URL.
            url = null,
            chatId = item.chatId,
            messageId = item.messageId,
            title = mediaDisplayLabels(item, "").title,
            fileId = fileId,
            fileName = item.fileName,
            mimeType = item.mimeType,
            durationSeconds = item.durationSeconds,
            fileSizeBytes = item.fileSizeBytes,
            alternativeVideos = item.alternativeVideos,
            topicId = item.messageThreadId
        )
    }

fun buildPlaybackRequest(
    item: MediaItem,
    streamUrl: String?,
    queueVideos: List<MediaItem>,
    subtitleUrl: String? = null
): PlaybackRequest {
    // Keep the *request itself* bounded, not only the copy PlayerScreen
    // creates later. A large source/search catalog may otherwise remain
    // retained for the full duration of playback on memory-limited TVs.
    val queue = buildPlaybackQueue(queueVideos)
        .distinctBy { it.chatId to it.messageId }
    val fileId = item.fileId ?: 0
    val request = PlaybackRequest(
        initialStreamUrl = streamUrl?.takeIf { it.isNotBlank() },
        chatId = item.chatId,
        messageId = item.messageId,
        fileId = fileId,
        title = mediaDisplayLabels(item, "").title,
        queue = queue,
        subtitleUrl = subtitleUrl,
        fileName = item.fileName,
        mimeType = item.mimeType,
        durationSeconds = item.durationSeconds,
        fileSizeBytes = item.fileSizeBytes,
        alternativeVideos = item.alternativeVideos,
        topicId = item.messageThreadId
    )
    return request.copy(queue = boundedInitialEpisodeQueue(request, 256))
}

fun queueItemFromRequest(request: PlaybackRequest): PlaybackQueueItem =
    PlaybackQueueItem(
        url = null,
        chatId = request.chatId,
        messageId = request.messageId,
        title = request.title,
        fileId = request.fileId,
        fileName = request.fileName,
        mimeType = request.mimeType,
        durationSeconds = request.durationSeconds,
        fileSizeBytes = request.fileSizeBytes,
        alternativeVideos = request.alternativeVideos,
        topicId = request.topicId
    )

/**
 * All related tray entries must share the current Telegram chat AND actual
 * TDLib forum topic. This never falls back to matching titles or filenames.
 */
fun relatedSourceQueue(
    queue: List<PlaybackQueueItem>,
    currentIndex: Int
): List<Pair<Int, PlaybackQueueItem>> {
    val current = queue.getOrNull(currentIndex) ?: return emptyList()
    return queue.mapIndexedNotNull { index, item ->
        if (item.chatId == current.chatId && item.topicId == current.topicId) {
            index to item
        } else null
    }
}

fun queueIndexForFileId(queue: List<PlaybackQueueItem>, fileId: Int): Int =
    if (fileId > 0) queue.indexOfFirst { it.fileId == fileId } else -1

/** The tapped Telegram message wins, even when an earlier row shares the same file. */
fun queueIndexForRequest(
    queue: List<PlaybackQueueItem>,
    chatId: Long,
    messageId: Long,
    fileId: Int
): Int {
    if (messageId != 0L) {
        val byMessage = queue.indexOfFirst { it.chatId == chatId && it.messageId == messageId }
        if (byMessage >= 0) return byMessage
    }
    // A TDLib file can appear in more than one message or chat. Never select
    // a different chat's item merely because it references the same file.
    return if (fileId > 0) queue.indexOfFirst {
        it.chatId == chatId && it.fileId == fileId
    } else -1
}

/** Seek buttons must work while an MKV is still resolving its duration from Cues.
 * A known duration bounds the target; unknown duration permits a non-negative seek.
 */
fun relativePlayerSeekTarget(baseMs: Long, deltaMs: Long, durationMs: Long): Long {
    val safeBase = baseMs.coerceAtLeast(0L)
    val target = if (deltaMs > 0L && safeBase > Long.MAX_VALUE - deltaMs) Long.MAX_VALUE
        else (safeBase + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}

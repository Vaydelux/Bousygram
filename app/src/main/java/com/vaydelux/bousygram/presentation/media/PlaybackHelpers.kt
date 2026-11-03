package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType

/**
 * Poll decoder progress frequently while controls or seek feedback are visible,
 * but avoid waking the entire Compose playback screen twice per second during
 * ordinary full-screen viewing. Buffering retains the previous 500 ms cadence
 * so recovery and progress diagnostics remain responsive.
 *
 * This changes only UI/maintenance sampling; the TDLib streaming scheduler,
 * native player clock and Media3 playback callbacks continue independently.
 */
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
internal fun isCredibleNaturalPlaybackEnd(
    durationMs: Long,
    positionMs: Long,
    hasProducedMedia: Boolean
): Boolean = when {
    !hasProducedMedia -> false
    durationMs > 5_000L -> positionMs >= durationMs - 5_000L
    else -> positionMs >= 5_000L
}

/** Buffer completion starts playback only if the user has not explicitly paused. */
internal fun shouldAutoResumeAfterReady(
    isReady: Boolean,
    playWhenReady: Boolean,
    isPlaying: Boolean
): Boolean = isReady && playWhenReady && !isPlaying

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
    val queue = buildPlaybackQueue(queueVideos)
    val fileId = item.fileId ?: 0
    return PlaybackRequest(
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
    return queueIndexForFileId(queue, fileId)
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

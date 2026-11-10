package com.vaydelux.bousygram.core.tdlib.server

import android.util.Log
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.core.cache.StoragePressureMonitor
import com.vaydelux.bousygram.core.tdlib.server.TdLibTelegramFileGateway.toGatewayFile
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Decide whether an extractor's next byte range needs its own TDLib download window.
 * Matroska Cues are often at EOF, so no tail-offset exemption is safe here.
 */
internal fun requiresPlaybackWindowReposition(
    requestOffset: Long,
    windowOffset: Long?,
    contiguousEndExclusive: Long
): Boolean = windowOffset == null ||
    requestOffset < windowOffset || requestOffset >= contiguousEndExclusive

internal enum class TelegramChunkPurpose {
    Preflight,
    Priming,
    Streaming,
    CacheAhead,
    BackgroundFull,
    Metadata
}

internal enum class TelegramChunkReadSource {
    DirectDisk,
    ReadFilePart
}

internal enum class PlaybackCacheState {
    Preparing,
    Priming,
    Streaming,
    Reconnecting,
    CachingAhead,
    Cached,
    Failed
}

internal enum class BackgroundFullCachePolicy {
    WifiOnly
}

internal data class StreamingCachePolicy(
    val backgroundFullCache: BackgroundFullCachePolicy = BackgroundFullCachePolicy.WifiOnly,
    val maxConcurrentChunks: Int = TelegramChunkScheduler.MAX_CONCURRENT_CHUNKS_PER_FILE
)

internal data class TelegramChunkRequest(
    val fileId: Int,
    val offset: Long,
    val limit: Int,
    val priority: Int,
    val purpose: TelegramChunkPurpose
)

internal enum class TelegramStreamConsumer {
    ExoPlayer,
    LocalHttp,
    Prefetch,
    Offline
}

internal enum class TelegramLeaseReleaseReason {
    Completed,
    ConsumerClosed,
    SeekSuperseded,
    TimedOut,
    Failed
}

internal data class TelegramRangeLease(
    internal val leaseId: Long,
    val fileId: Int,
    val offset: Long,
    val length: Long,
    val consumer: TelegramStreamConsumer,
    val priority: Int,
    val deadlineAtMs: Long?
)

internal sealed class TelegramChunkResult {
    data class Ready(
        val bytes: ByteArray,
        val source: TelegramChunkReadSource,
        val retryCount: Int
    ) : TelegramChunkResult()

    data object Pending : TelegramChunkResult()

    data class RetryableFailure(
        val message: String
    ) : TelegramChunkResult()

    data class FatalFailure(
        val message: String
    ) : TelegramChunkResult()
}

internal object TelegramChunkScheduler {
    const val CHUNK_SIZE_BYTES = 1024 * 1024
    const val MAX_CONCURRENT_CHUNKS_PER_FILE = 1

    private const val TAG = "TelegramChunkScheduler"
    const val PLAYBACK_PRIORITY = 32
    const val PREFETCH_PRIORITY = 16
    private const val PLAYBACK_WINDOW_BYTES = 8 * 1024 * 1024
    private const val MIN_PARTIAL_BYTES = 32 * 1024
    private const val TAIL_PREFETCH_BYTES = 8L * 1024L * 1024L
    private const val MIN_FILE_FOR_TAIL_BYTES = 4L * 1024L * 1024L
    private const val FAST_STALL_MS = 3_000L
    private const val STALL_MS = 6_000L
    // Local HTTP preflight must outlive both the first 3s wait and the 6s
    // targeted TDLib recovery attempt. The previous 2s cutoff canceled the
    // scheduler before its own fast-stall recovery could begin.
    internal const val STREAM_FIRST_CHUNK_WAIT_MS =
        FAST_STALL_MS + STALL_MS + 2_000L
    private const val TAIL_MAX_WAIT_MS = 60_000L
    private const val MAX_PLAYBACK_WAIT_MS = 30L * 60L * 1000L
    private const val OPEN_ENDED = Long.MAX_VALUE / 4
    private const val BACKGROUND_CHUNK_WAIT_MS = 4_000L
    private const val WINDOW_TTL_MS = 90_000L
    private const val PLAYBACK_BUSY_MS = 30_000L
    private const val STATUS_TIMEOUT_MS = 500L

    const val HEAD_BURST_BYTES = 2L * 1024L * 1024L
    const val TAIL_BURST_BYTES = 4L * 1024L * 1024L

    // This is a startup fallback for cache runway estimates, not a measured rate.
    @Volatile private var ewmaThroughputBps: Double = 3_000_000.0
    @Volatile private var hasMeasuredNetworkThroughput: Boolean = false
    @Volatile private var throughputLastSampleAtMs: Long = 0L

    internal fun isNetworkEstimateFresh(sampleAtMs: Long, nowMs: Long): Boolean =
        sampleAtMs > 0L && nowMs >= sampleAtMs && nowMs - sampleAtMs <= 15_000L

    /** Stale bandwidth must not authorize speculative downloads after a network change. */
    fun getAverageThroughput(): Long =
        if (hasMeasuredNetworkThroughput &&
            isNetworkEstimateFresh(throughputLastSampleAtMs, System.nanoTime() / 1_000_000L)
        ) ewmaThroughputBps.toLong() else 0L

    private data class DownloadProgressSnapshot(
        val offset: Long,
        val prefixBytes: Long,
        val observedAtMs: Long
    )

    private val downloadProgress = ConcurrentHashMap<Int, DownloadProgressSnapshot>()

    /** Only monotonic growth in the *same* TDLib download window counts as transfer. */
    internal fun downloadedByteDelta(
        previousOffset: Long,
        previousPrefix: Long,
        nextOffset: Long,
        nextPrefix: Long
    ): Long = if (previousOffset == nextOffset && nextPrefix > previousPrefix) {
        nextPrefix - previousPrefix
    } else 0L

    private data class ChunkKey(
        val fileId: Int,
        val offset: Long,
        val limit: Int
    )

    private data class DownloadStart(
        val file: TelegramGatewayFile? = null,
        val failure: TelegramChunkResult? = null
    )

    private data class DownloadWindow(
        val offset: Long,
        val endExclusive: Long,
        val priority: Int,
        val purpose: TelegramChunkPurpose,
        val startedAtMs: Long
    )

    private class SavedTail(
        val offset: Long,
        val bytes: ByteArray
    )

    private data class FileMessageContext(
        val chatId: Long,
        val messageId: Long
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permitsByFile = ConcurrentHashMap<Int, Semaphore>()
    private val activeRequests = ConcurrentHashMap<ChunkKey, kotlinx.coroutines.Deferred<TelegramChunkResult>>()
    private val nextLeaseId = AtomicLong(1L)
    private val leaseLock = Any()
    private val activeLeases = ConcurrentHashMap<Long, TelegramRangeLease>()
    private val leaseCountsByFile = ConcurrentHashMap<Int, Int>()
    private val activeCounts = ConcurrentHashMap<Int, AtomicInteger>()
    private val playbackBusyUntilMs = ConcurrentHashMap<Int, Long>()
    private val downloadWindows = ConcurrentHashMap<Int, DownloadWindow>()
    private val fileSizes = ConcurrentHashMap<Int, Long>()
    private val fileDurationsMs = ConcurrentHashMap<Int, Long>()
    private val savedTails = ConcurrentHashMap<Int, SavedTail>()
    private val playbackReady = ConcurrentHashMap.newKeySet<Int>()
    private val prepareLocks = ConcurrentHashMap<Int, Mutex>()
    private val downloadCommandLocks = ConcurrentHashMap<Int, Mutex>()
    private val cacheStates = ConcurrentHashMap<Int, PlaybackCacheState>()
    private val backgroundPaused = AtomicBoolean(false)
    private val coldStartFiles = ConcurrentHashMap.newKeySet<Int>()

    private val fileContexts = ConcurrentHashMap<Int, FileMessageContext>()
    private val reResolveAttempted = ConcurrentHashMap.newKeySet<Int>()
    private val remappedFileIds = ConcurrentHashMap<Int, Int>()

    private val latestFiles = ConcurrentHashMap<Int, TelegramGatewayFile>()
    // TDLib emits UpdateFile for thumbnails, chat search and catalog browsing.
    // Keeping every old status for a whole TV session grows without bound.
    // Re-query evicted idle statuses on demand; never trim active playback.
    private const val FILE_STATUS_TARGET = 256
    private const val FILE_STATUS_WATERMARK = 384
    private val statusCacheLock = Any()
    private val fileStatusTouchOrder = HashMap<Int, Long>()
    private var nextFileStatusTouch = 0L

    private fun retainFileStatus(file: TelegramGatewayFile) {
        if (file.fileId <= 0) return
        synchronized(statusCacheLock) {
            latestFiles[file.fileId] = file
            nextFileStatusTouch++
            fileStatusTouchOrder[file.fileId] = nextFileStatusTouch
            if (latestFiles.size <= FILE_STATUS_WATERMARK) return
            // Prune only at a high watermark to avoid sorting for every update.
            // Never steal status from a player, pending reader or active range.
            for (entry in fileStatusTouchOrder.entries.sortedBy { it.value }) {
                if (latestFiles.size <= FILE_STATUS_TARGET) break
                val id = entry.key
                if (id == file.fileId ||
                    PlaybackSessionController.isCurrentPlaybackFile(id) ||
                    (leaseCountsByFile[id] ?: 0) > 0 ||
                    (activeCounts[id]?.get() ?: 0) > 0 ||
                    downloadWindows.containsKey(id) ||
                    updateListeners.containsKey(id) ||
                    activeRequests.keys.any { it.fileId == id }
                ) continue
                latestFiles.remove(id)
                fileStatusTouchOrder.remove(id)
                cacheStates.remove(id)
            }
        }
    }

    internal fun retainedFileStatusCountForTests(): Int = latestFiles.size
    internal fun hasRetainedFileStatusForTests(fileId: Int): Boolean =
        latestFiles.containsKey(fileId)

    private val updateListeners = ConcurrentHashMap<Int, MutableSet<(TelegramGatewayFile) -> Unit>>()

    // Subscribe/unsubscribe through the same per-key compute lock. Adding a
    // listener outside compute can race with the last subscriber's removal,
    // orphaning a live waiter in a set that is no longer in the map.
    private fun subscribeFileUpdates(fileId: Int, listener: (TelegramGatewayFile) -> Unit) {
        updateListeners.compute(fileId) { _, existing ->
            (existing ?: ConcurrentHashMap.newKeySet<(TelegramGatewayFile) -> Unit>())
                .also { it.add(listener) }
        }
    }

    private fun unsubscribeFileUpdates(fileId: Int, listener: (TelegramGatewayFile) -> Unit) {
        updateListeners.computeIfPresent(fileId) { _, listeners ->
            listeners.remove(listener)
            listeners.takeIf { it.isNotEmpty() }
        }
    }

    internal fun activeFileUpdateListenerFilesForTests(): Int = updateListeners.size

    private val prefetchJobs = ConcurrentHashMap<Int, MutableSet<Job>>()
    private val prefetchLock = Any()
    private val activePrefetchRanges = ConcurrentHashMap<Int, MutableList<Pair<LongRange, Job>>>()

    init {
        TdLibClient.addUpdateHandler { update ->
            val file = (update as? TdApi.UpdateFile)?.file ?: return@addUpdateHandler
            onFileUpdated(file.toGatewayFile())
        }
    }

    fun onFileUpdated(file: TelegramGatewayFile) {
        retainFileStatus(file)
        // Cache reads and ReadFilePart return bytes immediately, so timing those
        // operations measures disk/IPC speed, NOT Telegram network throughput.
        // Sample only positive contiguous TDLib download-prefix growth.
        val nowMs = System.nanoTime() / 1_000_000L
        var transferSample: Pair<Long, Long>? = null
        if (file.isDownloadingActive && !file.isDownloadingCompleted) {
            downloadProgress.compute(file.fileId) { _, previous ->
                val next = DownloadProgressSnapshot(
                    file.downloadOffset, file.downloadedPrefixSize.coerceAtLeast(0L), nowMs
                )
                if (previous == null) return@compute next
                val elapsed = nowMs - previous.observedAtMs
                if (elapsed in 0L..249L && previous.offset == next.offset &&
                    next.prefixBytes >= previous.prefixBytes
                ) return@compute previous // accumulate enough time for a stable rate
                val bytes = downloadedByteDelta(
                    previous.offset, previous.prefixBytes, next.offset, next.prefixBytes
                )
                if (bytes > 0L && elapsed >= 250L) transferSample = bytes to elapsed
                next
            }
        } else {
            downloadProgress.remove(file.fileId)
        }
        transferSample?.let { (bytes, elapsed) -> recordNetworkTransfer(file.fileId, bytes, elapsed) }
        if (file.isDownloadingCompleted) {
            cacheStates[file.fileId] = PlaybackCacheState.Cached
        } else if (cacheStates[file.fileId] == PlaybackCacheState.Cached) {
            // TDLib may evict an on-disk file while the app is backgrounded.
            // Do not keep presenting a stale Cached verdict after fresh GetFile.
            cacheStates[file.fileId] =
                if (file.isDownloadingActive) PlaybackCacheState.Streaming else PlaybackCacheState.Preparing
        } else if (file.isDownloadingActive) {
            if (cacheStates[file.fileId] == PlaybackCacheState.Preparing) {
                cacheStates[file.fileId] = PlaybackCacheState.Streaming
            }
        }
        val listeners = updateListeners[file.fileId]?.toList().orEmpty()
        for (listener in listeners) {
            runCatching { listener(file) }
        }
    }

    fun noteFileContext(fileId: Int, chatId: Long, messageId: Long) {
        if (fileId > 0 && chatId != 0L && messageId != 0L) {
            fileContexts[fileId] = FileMessageContext(chatId, messageId)
        }
    }

    fun resolveEffectiveFileId(fileId: Int): Int =
        remappedFileIds[fileId] ?: fileId

    fun cacheState(fileId: Int): PlaybackCacheState =
        cacheStates[resolveEffectiveFileId(fileId)] ?: PlaybackCacheState.Preparing

    fun markState(fileId: Int, state: PlaybackCacheState) {
        val effectiveId = resolveEffectiveFileId(fileId)
        if (effectiveId > 0) {
            cacheStates[effectiveId] = state
        }
    }

    fun activeChunkCount(fileId: Int): Int =
        activeCounts[resolveEffectiveFileId(fileId)]?.get() ?: 0

    fun pauseBackgroundWork() {
        backgroundPaused.set(true)
    }

    fun resumeBackgroundWork() {
        backgroundPaused.set(false)
    }

    internal fun isPlaybackBusy(fileId: Int): Boolean {
        val effectiveId = resolveEffectiveFileId(fileId)
        if ((playbackBusyUntilMs[effectiveId] ?: 0L) > System.currentTimeMillis()) return true
        // A long network stall can outlive the short activity timestamp. Protect
        // the foreground download window for as long as a real player owns it.
        // Prefetch and Offline consumers alone must not block background work.
        return synchronized(leaseLock) {
            activeLeases.values.any { lease ->
                lease.fileId == effectiveId &&
                    (lease.consumer == TelegramStreamConsumer.ExoPlayer ||
                        lease.consumer == TelegramStreamConsumer.LocalHttp)
            }
        }
    }

    internal fun noteFileSize(fileId: Int, size: Long) {
        val effectiveId = resolveEffectiveFileId(fileId)
        if (effectiveId > 0 && size > 0L) {
            fileSizes[effectiveId] = size
        }
    }

    internal fun fileSize(fileId: Int): Long =
        fileSizes[resolveEffectiveFileId(fileId)] ?: 0L

    internal fun noteFileDuration(fileId: Int, durationMs: Long) {
        val effectiveId = resolveEffectiveFileId(fileId)
        if (effectiveId > 0 && durationMs > 0L) {
            fileDurationsMs[effectiveId] = durationMs
        }
    }

    internal fun resetForTests() {
        scope.coroutineContext[Job]?.cancelChildren()
        playbackBusyUntilMs.clear()
        downloadWindows.clear()
        downloadProgress.clear()
        ewmaThroughputBps = 3_000_000.0
        hasMeasuredNetworkThroughput = false
        throughputLastSampleAtMs = 0L
        fileSizes.clear()
        fileDurationsMs.clear()
        savedTails.clear()
        playbackReady.clear()
        prepareLocks.clear()
        downloadCommandLocks.clear()
        activeRequests.clear()
        cacheStates.clear()
        coldStartFiles.clear()
        fileContexts.clear()
        reResolveAttempted.clear()
        remappedFileIds.clear()
        synchronized(statusCacheLock) {
            latestFiles.clear()
            fileStatusTouchOrder.clear()
            nextFileStatusTouch = 0L
        }
        updateListeners.clear()
        prefetchJobs.values.forEach { jobs -> jobs.toList().forEach { it.cancel() } }
        prefetchJobs.clear()
        activePrefetchRanges.clear()
        permitsByFile.clear()
        activeCounts.clear()
        activeLeases.clear()
        leaseCountsByFile.clear()
    }

    /** Drop every file-id namespace before a different TDLib account can be admitted. */
    fun invalidateAccountResources() {
        val files = (latestFiles.keys + fileSizes.keys + downloadWindows.keys + leaseCountsByFile.keys).toSet()
        val client = if (files.isNotEmpty()) TdLibClient.client else null
        resetForTests()
        files.forEach { fileId ->
            runCatching { client?.send(TdApi.CancelDownloadFile(fileId, false)) { } }
        }
    }

    fun acquireLease(
        fileId: Int,
        offset: Long,
        length: Long,
        consumer: TelegramStreamConsumer,
        priority: Int,
        deadlineAtMs: Long? = null
    ): TelegramRangeLease {
        require(fileId > 0) { "fileId must be positive" }
        require(offset >= 0L) { "offset must not be negative" }
        require(length >= 0L) { "length must not be negative" }
        val effectiveId = resolveEffectiveFileId(fileId)
        val lease = TelegramRangeLease(
            leaseId = nextLeaseId.getAndIncrement(),
            fileId = effectiveId,
            offset = offset,
            length = length,
            consumer = consumer,
            priority = priority.coerceIn(1, PLAYBACK_PRIORITY),
            deadlineAtMs = deadlineAtMs
        )
        synchronized(leaseLock) {
            activeLeases[lease.leaseId] = lease
            leaseCountsByFile[effectiveId] = (leaseCountsByFile[effectiveId] ?: 0) + 1
        }
        return lease
    }

    fun releaseLease(
        lease: TelegramRangeLease?,
        reason: TelegramLeaseReleaseReason = TelegramLeaseReleaseReason.ConsumerClosed,
        lastOffset: Long = -1L
    ) {
        lease ?: return
        val shouldCancel = synchronized(leaseLock) {
            val removed = activeLeases.remove(lease.leaseId) ?: return@synchronized false
            val remaining = ((leaseCountsByFile[removed.fileId] ?: 1) - 1).coerceAtLeast(0)
            if (remaining == 0) leaseCountsByFile.remove(removed.fileId) else leaseCountsByFile[removed.fileId] = remaining
            remaining == 0 && removed.consumer != TelegramStreamConsumer.Offline
        }
        if (reason != TelegramLeaseReleaseReason.Completed) {
            PlaybackTelemetryRecorder.recordCancellation(lease.fileId, reason.name)
        }
        if (shouldCancel) cancelFileIfUnused(lease.fileId, lastOffset)
    }

    internal fun activeLeaseCount(fileId: Int): Int =
        synchronized(leaseLock) { leaseCountsByFile[resolveEffectiveFileId(fileId)] ?: 0 }

    private fun cancelFileIfUnused(fileId: Int, offset: Long) {
        val effectiveId = resolveEffectiveFileId(fileId)
        if (activeLeaseCount(effectiveId) > 0 || OfflineCacheManager.isPinned(effectiveId)) return
        // ExoPlayer/Media3 closes the old DataSource before opening a new one
        // during a seek or container metadata probe. No reader lease exists in
        // that short gap, but the active playback session still owns the file.
        // The explicit session stop/track-switch path cancels the download.
        if (PlaybackSessionController.isCurrentPlaybackFile(effectiveId)) return
        Log.i(TAG, "Last transient consumer released fileId=$effectiveId offset=$offset")
        cancelPrefetch(effectiveId)
        activeRequests.entries.removeIf { (key, deferred) ->
            if (key.fileId == effectiveId) {
                deferred.cancel()
                true
            } else {
                false
            }
        }
        downloadWindows.remove(effectiveId)
        downloadProgress.remove(effectiveId)
        playbackBusyUntilMs.remove(effectiveId)
        playbackReady.remove(effectiveId)
        coldStartFiles.remove(effectiveId)
        scope.launch {
            TelegramFileGatewayRegistry.current.cancelDownloadFile(effectiveId, onlyIfPending = true)
        }
    }

    private fun recordNetworkTransfer(fileId: Int, bytes: Long, durationMs: Long) {
        if (bytes <= 0L || durationMs <= 0L) return
        // During playback, TDLib callbacks from thumbnail/other-file traffic
        // must not be mistaken for sustainable throughput of the active stream.
        if (PlaybackSessionController.isVideoPlaying() &&
            !PlaybackSessionController.isCurrentPlaybackFile(fileId)) return
        // Drop implausibly large samples (window jumps/replays) instead of
        // inflating the bandwidth estimate used for adaptive cache decisions.
        val rate = (bytes.toDouble() * 1000.0) / durationMs.toDouble()
        if (rate <= 0.0 || rate > 64.0 * 1024.0 * 1024.0) return
        synchronized(downloadProgress) {
            ewmaThroughputBps = if (hasMeasuredNetworkThroughput) {
                0.2 * rate + 0.8 * ewmaThroughputBps
            } else rate
            hasMeasuredNetworkThroughput = true
            throughputLastSampleAtMs = System.nanoTime() / 1_000_000L
            PlaybackTelemetryRecorder.recordThroughput(fileId, ewmaThroughputBps.toLong())
        }
    }

    private fun effectiveLimit(fileId: Int, offset: Long, limit: Int): Int {
        val effectiveId = resolveEffectiveFileId(fileId)
        val totalSize = fileSizes[effectiveId] ?: 0L
        if (totalSize > 2L * 1024L * 1024L && offset >= (totalSize - 2L * 1024L * 1024L)) {
            val optimalTail = com.vaydelux.bousygram.core.media.ContainerHeaderParser.calculateOptimalTailSize(
                sampleTail = savedTails[effectiveId]?.bytes,
                fileTotalSize = totalSize,
                isMkv = false
            ).toInt()
            val remaining = (totalSize - offset).coerceAtLeast(1L)
            return min(optimalTail, min(limit.toLong(), remaining).toInt())
        }
        return limit
    }

    /**
     * Prepares TDLib download window, approving the download request if not already covered.
     */
    internal fun prepareDownload(
        fileId: Int,
        offset: Long,
        readLimit: Int,
        downloadLimit: Int,
        priority: Int,
        purpose: TelegramChunkPurpose
    ): Int? {
        val effectiveId = resolveEffectiveFileId(fileId)
        if (effectiveId <= 0 || readLimit <= 0 || downloadLimit <= 0) return null
        val playback = isPlaybackPurpose(purpose)
        val now = System.currentTimeMillis()
        var approved: Int? = null
        downloadWindows.compute(effectiveId) { _, existing ->
            val fresh = existing != null && now - existing.startedAtMs < WINDOW_TTL_MS
            if (!playback && isPlaybackBusy(effectiveId)) {
                approved = null
                return@compute existing
            }
            if (existing != null && existing.endExclusive >= OPEN_ENDED && isPlaybackBusy(effectiveId)) {
                approved = null
                return@compute existing.copy(startedAtMs = now)
            }
            if (fresh &&
                offset >= existing.offset &&
                saturatingPrefetchEnd(offset, readLimit.toLong()) <= existing.endExclusive
            ) {
                approved = null
                return@compute existing.copy(startedAtMs = now)
            }
            approved = downloadLimit
            DownloadWindow(
                offset = offset,
                endExclusive = saturatingPrefetchEnd(offset, downloadLimit.toLong()),
                priority = priority,
                purpose = purpose,
                startedAtMs = now
            )
        }
        return approved
    }

    /**
     * Start one continuous foreground TDLib download for the current file.
     *
     * Media3 reopens DataSources for seeks, MKV cues and MP4 metadata. Repeating
     * the head download on every reopen discards the current TDLib download
     * window and can stall playback near the initial 2 MiB boundary.
     * Keep the open-ended window until a true seek or the final consumer exits.
     */
    @Suppress("UNUSED_PARAMETER")
    fun openPlayback(fileId: Int, totalSize: Long, isMp4: Boolean = false): Job = scope.launch {
        if (fileId <= 0) return@launch
        val effectiveId = resolveEffectiveFileId(fileId)
        val prepareLock = prepareLocks.getOrPut(effectiveId) { Mutex() }
        var started = false
        var fullyCached = false
        prepareLock.withLock {
            val existing = downloadWindows[effectiveId]
            if (playbackReady.contains(effectiveId) && existing != null) {
                // Even a distant metadata/seek reopen must not reset the head.
                touchPlayback(effectiveId)
                return@withLock
            }

            val initialFile = latestFiles[effectiveId] ?: getFileStatus(effectiveId)
            val resolvedSize = if (totalSize > 0L) totalSize else (initialFile?.size ?: 0L)
            if (resolvedSize > 0L) noteFileSize(effectiveId, resolvedSize)
            fullyCached = initialFile?.isDownloadingCompleted == true
            PlaybackTelemetryRecorder.recordFullyCachedAtStart(effectiveId, fullyCached)
            touchPlayback(effectiveId)
            if (fullyCached) {
                markState(effectiveId, PlaybackCacheState.Cached)
                playbackReady.add(effectiveId)
                return@withLock
            }

            markState(effectiveId, PlaybackCacheState.Preparing)
            // A single open-ended download advances through the entire media
            // while the session is active. Seek handling repositions it on demand.
            val failure = issueOpenEnded(effectiveId, 0L)
            if (failure != null) {
                PlaybackDiagnostics.recordStreamError("Unable to open playback window fileId=$effectiveId")
                return@withLock
            }
            playbackReady.add(effectiveId)
            started = true
        }
        if (started) {
            val headStartMs = System.currentTimeMillis()
            val headLimit = if (totalSize > 0L) min(HEAD_BURST_BYTES, totalSize).toInt() else HEAD_BURST_BYTES.toInt()
            awaitPrefixReady(effectiveId, 0L, min(headLimit, MIN_PARTIAL_BYTES), timeoutMs = 15_000L)
            PlaybackTelemetryRecorder.recordHeaderChunkTime(effectiveId, (System.currentTimeMillis() - headStartMs).coerceAtLeast(1L))
        }
    }

    fun cancelPrefetch(fileId: Int) {
        val effectiveId = resolveEffectiveFileId(fileId)
        synchronized(prefetchLock) {
            prefetchJobs.remove(effectiveId)?.toList()?.forEach { it.cancel() }
            activePrefetchRanges.remove(effectiveId)?.toList()?.forEach { (_, job) -> job.cancel() }
        }
    }

    fun onClientDisconnected(fileId: Int, offset: Long = -1L) {
        if (fileId <= 0) return
        val effectiveId = resolveEffectiveFileId(fileId)
        if (activeLeaseCount(effectiveId) == 0) cancelFileIfUnused(effectiveId, offset)
    }

    suspend fun fetchChunk(request: TelegramChunkRequest): TelegramChunkResult {
        if (request.fileId <= 0) return TelegramChunkResult.FatalFailure("Invalid Telegram file id")
        val effectiveId = resolveEffectiveFileId(request.fileId)
        val mappedRequest = if (effectiveId != request.fileId) request.copy(fileId = effectiveId) else request
        val cappedLimit = effectiveLimit(mappedRequest.fileId, mappedRequest.offset, mappedRequest.limit)
        if (cappedLimit <= 0) {
            return TelegramChunkResult.Ready(ByteArray(0), TelegramChunkReadSource.DirectDisk, retryCount = 0)
        }
        val effectiveRequest = if (cappedLimit != mappedRequest.limit) mappedRequest.copy(limit = cappedLimit) else mappedRequest

        sliceSavedTail(effectiveRequest.fileId, effectiveRequest.offset, effectiveRequest.limit)?.let { bytes ->
            if (isPlaybackPurpose(effectiveRequest.purpose)) touchPlayback(effectiveRequest.fileId)
            PlaybackTelemetryRecorder.recordLocalBytesServed(effectiveRequest.fileId, bytes.size)
            return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
        }

        // Container duration probes are observational only. A TS tail/PCR scan
        // must not start or reposition TDLib's native download window, including
        // when playback is preparing and no foreground reader has a lease yet.
        // The next scheduled probe can retry after these bytes arrive naturally.
        if (effectiveRequest.purpose == TelegramChunkPurpose.Metadata) {
            return readReadyChunk(
                latestFiles[effectiveRequest.fileId] ?: getFileStatus(effectiveRequest.fileId),
                effectiveRequest
            ) ?: TelegramChunkResult.Pending
        }

        if (isPlaybackPurpose(effectiveRequest.purpose)) {
            touchPlayback(effectiveRequest.fileId)
        } else if (isPlaybackBusy(effectiveRequest.fileId)) {
            return readReadyChunk(latestFiles[effectiveRequest.fileId] ?: getFileStatus(effectiveRequest.fileId), effectiveRequest)
                ?: TelegramChunkResult.Pending
        }

        val key = ChunkKey(effectiveRequest.fileId, effectiveRequest.offset.coerceAtLeast(0L), effectiveRequest.limit)
        return coroutineScope {
            val covering = activeRequests.entries.firstOrNull { (activeKey, _) ->
                activeKey.fileId == key.fileId &&
                    activeKey.offset <= key.offset &&
                    saturatingPrefetchEnd(activeKey.offset, activeKey.limit.toLong()) >=
                        saturatingPrefetchEnd(key.offset, key.limit.toLong())
            }
            if (covering != null) {
                PlaybackTelemetryRecorder.recordDeduplicatedRequest(key.fileId)
                val shared = covering.value.await()
                if (shared is TelegramChunkResult.Ready) {
                    val relativeStart = (key.offset - covering.key.offset).toInt()
                    val relativeEnd = relativeStart + key.limit
                    if (relativeStart >= 0 && relativeEnd <= shared.bytes.size) {
                        return@coroutineScope shared.copy(bytes = shared.bytes.copyOfRange(relativeStart, relativeEnd))
                    }
                }
                // A covering *requested* range can complete with a short read. Never
                // return its offset-zero bytes for a different requested offset.
                if (shared !is TelegramChunkResult.Ready) return@coroutineScope shared
            }
            val candidate = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val semaphore = semaphoreForFile(effectiveRequest.fileId)
                semaphore.withPermit {
                    val active = activeCountForFile(effectiveRequest.fileId)
                    active.incrementAndGet()
                    try {
                        if (isBackgroundPurpose(effectiveRequest.purpose) &&
                            (backgroundPaused.get() || isPlaybackBusy(effectiveRequest.fileId))
                        ) {
                            readReadyChunk(latestFiles[effectiveRequest.fileId] ?: getFileStatus(effectiveRequest.fileId), effectiveRequest)
                                ?: TelegramChunkResult.Pending
                        } else {
                            fetchWithFallback(effectiveRequest.copy(offset = key.offset))
                        }
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }

            val existing = activeRequests.putIfAbsent(key, candidate)
            if (existing != null) {
                candidate.cancel()
                PlaybackTelemetryRecorder.recordDeduplicatedRequest(key.fileId)
                existing.await()
            } else {
                candidate.start()
                try {
                    candidate.await()
                } finally {
                    activeRequests.remove(key, candidate)
                }
            }
        }
    }

    fun prefetchRange(
        fileId: Int,
        offset: Long,
        length: Long,
        purpose: TelegramChunkPurpose,
        priority: Int = PREFETCH_PRIORITY
    ): Job {
        val effectiveId = resolveEffectiveFileId(fileId)
        val effectivePriority = if (isPlaybackPurpose(purpose)) PLAYBACK_PRIORITY else min(priority, PREFETCH_PRIORITY)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            if (effectiveId <= 0 || length <= 0L) return@launch
            // Streaming and Preflight are user-driven reads and must still work.
            // Only speculative range work yields under storage pressure.
            val speculative = purpose == TelegramChunkPurpose.CacheAhead ||
                purpose == TelegramChunkPurpose.BackgroundFull ||
                purpose == TelegramChunkPurpose.Priming
            if (speculative && StoragePressureMonitor.isLow()) return@launch
            val endExclusive = saturatingPrefetchEnd(offset, length)
            var currentOffset = offset.coerceAtLeast(0L)
            var failures = 0
            markState(
                effectiveId,
                when (purpose) {
                    TelegramChunkPurpose.BackgroundFull,
                    TelegramChunkPurpose.CacheAhead -> PlaybackCacheState.CachingAhead
                    TelegramChunkPurpose.Priming,
                    TelegramChunkPurpose.Preflight -> PlaybackCacheState.Priming
                    TelegramChunkPurpose.Streaming -> PlaybackCacheState.Streaming
                    TelegramChunkPurpose.Metadata -> PlaybackCacheState.Preparing
                }
            )

            while (currentOffset < endExclusive && coroutineContext.isActive) {
                // A previously started next-episode/poster prefetch cannot keep
                // filling flash after free space drops below 500 MiB.
                if (speculative && StoragePressureMonitor.isLow()) return@launch
                val limit = min(CHUNK_SIZE_BYTES.toLong(), endExclusive - currentOffset).toInt()
                val result = fetchChunk(
                    TelegramChunkRequest(
                        fileId = effectiveId,
                        offset = currentOffset,
                        limit = limit,
                        priority = effectivePriority,
                        purpose = purpose
                    )
                )
                when (result) {
                    is TelegramChunkResult.Ready -> {
                        failures = 0
                        PlaybackTelemetryRecorder.notifyFirstByte(effectiveId)
                        if (result.bytes.isEmpty()) return@launch
                        currentOffset += result.bytes.size
                        PlaybackDiagnostics.recordProxyState(
                            lastStalledOffset = currentOffset,
                            chunkRetryCount = result.retryCount,
                            cacheAheadBytes = currentOffset - offset.coerceAtLeast(0L),
                            activeChunkCount = activeChunkCount(effectiveId),
                            proxyStatus = purpose.name,
                            readSource = result.source.name
                        )
                    }
                    is TelegramChunkResult.FatalFailure -> {
                        markState(effectiveId, PlaybackCacheState.Failed)
                        PlaybackDiagnostics.recordStreamError(result.message)
                        return@launch
                    }
                    else -> {
                        failures++
                        if (failures >= 3) {
                            PlaybackDiagnostics.recordStreamError("Prefetch paused fileId=$effectiveId offset=$currentOffset")
                            return@launch
                        }
                        delay(750L * failures)
                    }
                }
            }
            if (purpose == TelegramChunkPurpose.BackgroundFull && currentOffset >= endExclusive) {
                markState(effectiveId, PlaybackCacheState.Cached)
            }
        }
        val range = offset.coerceAtLeast(0L)..(offset.coerceAtLeast(0L) + length)
        val rangeList: MutableList<Pair<LongRange, Job>>
        val rangePair = range to job
        synchronized(prefetchLock) {
            prefetchJobs.computeIfAbsent(effectiveId) { ConcurrentHashMap.newKeySet() }.add(job)
            rangeList = activePrefetchRanges.computeIfAbsent(effectiveId) { java.util.Collections.synchronizedList(mutableListOf()) }
            rangeList.add(rangePair)
        }
        job.invokeOnCompletion {
            synchronized(prefetchLock) {
                rangeList.remove(rangePair)
                prefetchJobs[effectiveId]?.let { jobs ->
                    jobs.remove(job)
                    if (jobs.isEmpty()) prefetchJobs.remove(effectiveId, jobs)
                }
                if (rangeList.isEmpty()) activePrefetchRanges.remove(effectiveId, rangeList)
            }
        }
        job.start()
        return job
    }

    /**
     * Cancels active prefetch chunks outside the [targetOffset - 2MB, targetOffset + windowBytes] range.
     */
    fun cancelOutOfWindowChunks(fileId: Int, targetOffset: Long, windowBytes: Long = 32L * 1024L * 1024L) {
        val effectiveId = resolveEffectiveFileId(fileId)
        val validWindow = prefetchSeekWindow(targetOffset, windowBytes)

        activePrefetchRanges[effectiveId]?.let { list ->
            synchronized(list) {
                val it = list.iterator()
                while (it.hasNext()) {
                    val (range, job) = it.next()
                    if (range.last < validWindow.first || range.first > validWindow.last) {
                        job.cancel()
                        it.remove()
                    }
                }
            }
        }
    }

    private fun prefetchAhead(fileId: Int, offset: Long, length: Int) {
        if (isPlaybackBusy(fileId)) return // Open-ended playback owns TDLib's download window.
        val totalSize = fileSizes[fileId] ?: 0L
        val nextOffset = saturatingPrefetchEnd(offset, length.toLong())
        // Media3 may deliver hundreds of short sequential reads while an existing
        // 4-MiB cache-ahead job is active. Avoid launching overlapping TDLib jobs
        // for every short read; playback still has its own high-priority request.
        val alreadyPrefetching = synchronized(prefetchLock) {
            activePrefetchRanges[fileId]?.any { (range, job) ->
                job.isActive && nextOffset in range
            } == true
        }
        if (alreadyPrefetching) return
        if (totalSize > 0L && nextOffset >= totalSize) return
        val prefetchBytes = min(4L * 1024L * 1024L, if (totalSize > 0L) totalSize - nextOffset else 4L * 1024L * 1024L)
        if (prefetchBytes > 0L) {
            prefetchRange(
                fileId = fileId,
                offset = nextOffset,
                length = prefetchBytes,
                purpose = TelegramChunkPurpose.CacheAhead,
                priority = PREFETCH_PRIORITY
            )
        }
    }

    private fun semaphoreForFile(fileId: Int): Semaphore {
        permitsByFile[fileId]?.let { return it }
        val created = Semaphore(MAX_CONCURRENT_CHUNKS_PER_FILE)
        return permitsByFile.putIfAbsent(fileId, created) ?: created
    }

    private fun activeCountForFile(fileId: Int): AtomicInteger {
        activeCounts[fileId]?.let { return it }
        val created = AtomicInteger(0)
        return activeCounts.putIfAbsent(fileId, created) ?: created
    }

    private suspend fun fetchWithFallback(request: TelegramChunkRequest): TelegramChunkResult {
        if (isPlaybackPurpose(request.purpose)) {
            touchPlayback(request.fileId)
            // Check the current TDLib file window before deciding to reposition it.
            // MKV cue and MP4 moov bytes may already have arrived through UpdateFile;
            // seeking again would discard usable bytes and slow startup.
            readReadyChunk(latestFiles[request.fileId] ?: getFileStatus(request.fileId), request)?.let { cached ->
                PlaybackTelemetryRecorder.recordCacheHit(request.fileId, cached.bytes.size)
                return cached
            }
            // A distant extractor seek must not reopen the head window first.
            if (request.offset < HEAD_BURST_BYTES) {
                ensurePlaybackPrefix(request.fileId)?.let { return it }
            }
            sliceSavedTail(request.fileId, request.offset, request.limit)?.let { bytes ->
                return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
            }
            // Avoid waiting for a full tail probe before a small urgent range.
            handleSeek(request)
            val result = awaitPlaybackChunk(request)
            if (result is TelegramChunkResult.Ready) {
                prefetchAhead(request.fileId, request.offset, result.bytes.size)
            }
            return result
        }

        val issuedLimit = prepareDownload(
            fileId = request.fileId,
            offset = request.offset,
            readLimit = request.limit,
            downloadLimit = request.limit,
            priority = request.priority,
            purpose = request.purpose
        )
        if (issuedLimit != null) {
            val started = startChunkDownload(
                fileId = request.fileId,
                offset = request.offset,
                limit = issuedLimit,
                priority = request.priority
            )
            started.failure?.let { failure ->
                clearDownloadWindow(request.fileId, request.offset)
                return failure
            }
        }

        var file = latestFiles[request.fileId] ?: getFileStatus(request.fileId)
        readReadyChunk(file, request)?.let { return it }

        val neededBytes = min(request.limit, MIN_PARTIAL_BYTES).coerceAtLeast(1)
        awaitPrefixReady(request.fileId, request.offset, neededBytes, timeoutMs = BACKGROUND_CHUNK_WAIT_MS)
        file = latestFiles[request.fileId] ?: getFileStatus(request.fileId)
        readReadyChunk(file, request)?.let { return it }

        return TelegramChunkResult.Pending
    }

    private suspend fun ensurePlaybackPrefix(fileId: Int): TelegramChunkResult? {
        if (fileId <= 0 || playbackReady.contains(fileId)) return null
        val lock = prepareLocks.getOrPut(fileId) { Mutex() }
        return lock.withLock {
            if (playbackReady.contains(fileId)) return@withLock null
            touchPlayback(fileId)
            val window = downloadWindows[fileId]
            val headOpen = window != null && window.offset == 0L && window.endExclusive >= OPEN_ENDED
            if (!headOpen) {
                val failure = issueOpenEnded(fileId, 0L)
                if (failure != null) return@withLock failure
            }
            playbackReady.add(fileId)
            null
        }
    }

    private suspend fun captureTail(fileId: Int, size: Long): TelegramChunkResult? {
        if (size <= MIN_FILE_FOR_TAIL_BYTES || savedTails.containsKey(fileId)) return null
        val optimal = com.vaydelux.bousygram.core.media.ContainerHeaderParser.calculateOptimalTailSize(
            sampleTail = null,
            fileTotalSize = size,
            isMkv = false
        )
        val tailBytes = min(optimal, size / 2L).coerceAtLeast(1L)
        if (tailBytes < 64L * 1024L) return null
        val tailOffset = size - tailBytes
        val limit = tailBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val currentFile = latestFiles[fileId] ?: getFileStatus(fileId)
        val alreadyComplete = currentFile?.isDownloadingCompleted == true
        if (!alreadyComplete) {
            val isReady = currentFile != null && isRangeReady(currentFile, tailOffset, limit)
            if (!isReady) {
                val window = downloadWindows[fileId]
                val sameTail = window != null && window.offset == tailOffset && window.endExclusive == tailOffset + tailBytes
                if (!sameTail) {
                    rememberWindow(fileId, tailOffset, tailOffset + tailBytes, TelegramChunkPurpose.Streaming)
                    val started = startChunkDownload(fileId, tailOffset, limit, PLAYBACK_PRIORITY, synchronous = false)
                    if (started.failure is TelegramChunkResult.FatalFailure) {
                        clearDownloadWindow(fileId, tailOffset)
                        return started.failure
                    }
                }
                awaitPrefixReady(fileId, tailOffset, minNeededBytes = min(limit, 64 * 1024), timeoutMs = TAIL_MAX_WAIT_MS)
            }
        }
        val bytes = readRange(fileId, tailOffset, limit) ?: return null
        savedTails[fileId] = SavedTail(tailOffset, bytes)
        return null
    }

    private suspend fun issueOpenEnded(fileId: Int, offset: Long): TelegramChunkResult? {
        rememberWindow(fileId, offset, OPEN_ENDED, TelegramChunkPurpose.Streaming)
        val started = startChunkDownload(fileId, offset, limit = 0, priority = PLAYBACK_PRIORITY, synchronous = false)
        val failure = started.failure
        // A retryable TDLib network/disconnect error did not start a download.
        // Treating it as success leaves a phantom playback window and reports
        // playbackReady even though no bytes can arrive. Let the next open or
        // foreground extractor read retry the real request after reconnection.
        if (failure != null) {
            clearDownloadWindow(fileId, offset)
            return failure
        }
        return null
    }

    private fun playbackWindowBytes(fileId: Int, offset: Long): Long {
        if (offset == 0L || coldStartFiles.contains(fileId)) {
            return 2L * 1024L * 1024L // 2MB cold-start burst (< 350ms TTFF)
        }
        val isCellular = com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.isCellular
        val durationMs = fileDurationsMs[fileId] ?: 0L
        val totalSize = fileSizes[fileId] ?: 0L
        return boundedPlaybackWindowBytes(
            totalSize = totalSize,
            durationMs = durationMs,
            measuredThroughputBytesPerSecond = ewmaThroughputBps,
            isCellular = isCellular
        )
    }

    private suspend fun handleSeek(request: TelegramChunkRequest) {
        val window = downloadWindows[request.fileId]
        val saved = savedTails[request.fileId]
        if (saved != null && request.offset >= saved.offset &&
            request.offset < saturatingPrefetchEnd(saved.offset, saved.bytes.size.toLong())
        ) return
        // Matroska extractors commonly seek to Cues near EOF during startup. Previously
        // *all* tail seeks were skipped here, even when MKV had not prefetched any tail
        // bytes. The caller would then wait for bytes never requested from TDLib.
        // readReadyChunk/sliceSavedTail handle cached bytes before reaching this method;
        // a missing tail range must be requested just like any other random access.
        val currentFile = latestFiles[request.fileId] ?: getFileStatus(request.fileId)
        val prefix = knownPrefixBytes(currentFile, window?.offset ?: request.offset)
        val contiguousEnd = saturatingPrefetchEnd(
            saturatingPrefetchEnd(window?.offset ?: request.offset, prefix),
            PLAYBACK_WINDOW_BYTES.toLong()
        )
        val isOutside = requiresPlaybackWindowReposition(request.offset, window?.offset, contiguousEnd)
        if (isOutside) {
            Log.d(TAG, "Seek detected fileId=${request.fileId} offset=${request.offset}; cancelling stale in-flight downloads")
            PlaybackTelemetryRecorder.recordSupersededWindow(request.fileId)
            cancelPrefetch(request.fileId)
            TelegramFileGatewayRegistry.current.cancelDownloadFile(request.fileId, onlyIfPending = false)

            // Cancel any in-flight deferred requests that are outside the new playhead window
            val newPlayheadStart = request.offset
            val newPlayheadEnd = saturatingPrefetchEnd(request.offset, PLAYBACK_WINDOW_BYTES.toLong())
            activeRequests.entries.removeIf { (key, deferred) ->
                if (key.fileId == request.fileId && (key.offset < newPlayheadStart || key.offset >= newPlayheadEnd)) {
                    deferred.cancel()
                    true
                } else {
                    false
                }
            }

            coldStartFiles.add(request.fileId)
            issueOpenEnded(request.fileId, request.offset)
        }
    }

    private suspend fun awaitPlaybackChunk(request: TelegramChunkRequest): TelegramChunkResult {
        val fetchStart = System.currentTimeMillis()
        touchPlayback(request.fileId)
        touchDownloadWindow(request.fileId)

        var file = latestFiles[request.fileId] ?: getFileStatus(request.fileId)
        readReadyChunk(file, request)?.let { ready ->
            PlaybackTelemetryRecorder.recordCacheHit(request.fileId, ready.bytes.size)
            return ready
        }
        PlaybackTelemetryRecorder.recordCacheMiss(request.fileId)

        // Wait via UpdateFile events for the requested prefix without polling
        val neededBytes = min(request.limit, MIN_PARTIAL_BYTES).coerceAtLeast(1)
        var ready = awaitPrefixReady(request.fileId, request.offset, neededBytes, timeoutMs = FAST_STALL_MS)
        file = latestFiles[request.fileId] ?: getFileStatus(request.fileId)
        if (ready || file != null) {
            readReadyChunk(file, request)?.let { chunk ->
                return chunk
            }
        }

        // Fast-stall recovery: re-issue download at high priority once
        if (!ready) {
            markState(request.fileId, PlaybackCacheState.Preparing)
            val anchor = downloadWindows[request.fileId]
            val offset = anchor?.offset ?: request.offset
            val limit = if (anchor != null && anchor.endExclusive >= OPEN_ENDED) 0 else request.limit
            startChunkDownload(request.fileId, offset, limit, PLAYBACK_PRIORITY, synchronous = false)
            ready = awaitPrefixReady(request.fileId, request.offset, neededBytes, timeoutMs = STALL_MS)
            file = latestFiles[request.fileId] ?: getFileStatus(request.fileId)
            readReadyChunk(file, request)?.let { chunk ->
                return chunk
            }
        }

        val finalFile = latestFiles[request.fileId] ?: getFileStatus(request.fileId) ?: file
        if (finalFile != null) {
            readExactChunkFromDisk(finalFile, request.offset, request.limit)?.let { bytes ->
                return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
            }
            readChunkViaTdLib(request.fileId, request.offset, request.limit)?.let { bytes ->
                return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.ReadFilePart, retryCount = 0)
            }
        }
        return TelegramChunkResult.Pending
    }

    internal suspend fun awaitPrefixReady(
        fileId: Int,
        offset: Long,
        minNeededBytes: Int,
        timeoutMs: Long
    ): Boolean {
        val initialFile = latestFiles[fileId] ?: getFileStatus(fileId)
        if (initialFile != null && isRangeReady(initialFile, offset, minNeededBytes)) {
            return true
        }

        val deferred = CompletableDeferred<Boolean>()
        val listener: (TelegramGatewayFile) -> Unit = { updatedFile ->
            if (isRangeReady(updatedFile, offset, minNeededBytes)) {
                deferred.complete(true)
            }
        }

        subscribeFileUpdates(fileId, listener)
        try {
            val freshFile = latestFiles[fileId] ?: getFileStatus(fileId)
            if (freshFile != null && isRangeReady(freshFile, offset, minNeededBytes)) {
                return true
            }
            return withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: false
        } finally {
            unsubscribeFileUpdates(fileId, listener)
        }
    }

    internal fun isRangeReady(file: TelegramGatewayFile, offset: Long, minNeededBytes: Int): Boolean {
        if (file.isDownloadingCompleted) return true
        val knownTotal = fileSizes[file.fileId] ?: (if (file.size > 0L) file.size else if (file.expectedSize > 0L) file.expectedSize else 0L)
        if (knownTotal > 0L && offset >= knownTotal) return true

        val prefix = knownPrefixBytes(file, offset)
        if (prefix <= 0L) return false
        if (minNeededBytes <= 0) return prefix > 0L
        if (prefix >= minNeededBytes) return true
        if (knownTotal > 0L && saturatingPrefetchEnd(offset, prefix) >= knownTotal) return true
        return false
    }

    private suspend fun readRange(fileId: Int, offset: Long, length: Int): ByteArray? {
        if (length <= 0) return ByteArray(0)
        val buffer = ByteArray(length)
        var filled = 0
        while (filled < length && coroutineContext.isActive) {
            val request = TelegramChunkRequest(
                fileId = fileId,
                offset = offset + filled,
                limit = min(CHUNK_SIZE_BYTES, length - filled),
                priority = PLAYBACK_PRIORITY,
                purpose = TelegramChunkPurpose.Streaming
            )
            val ready = readReadyChunk(latestFiles[fileId] ?: getFileStatus(fileId), request) ?: return null
            if (ready.bytes.isEmpty()) return null
            val copyCount = min(ready.bytes.size, length - filled)
            ready.bytes.copyInto(buffer, destinationOffset = filled, endIndex = copyCount)
            filled += copyCount
        }
        return if (filled == length) buffer else null
    }

    private fun sliceSavedTail(fileId: Int, offset: Long, limit: Int): ByteArray? {
        if (limit <= 0) return null
        val tail = savedTails[fileId] ?: return null
        if (offset < tail.offset) return null
        val start = (offset - tail.offset).toInt()
        if (start < 0 || start >= tail.bytes.size) return null
        val take = min(limit, tail.bytes.size - start)
        if (take <= 0) return null
        return tail.bytes.copyOfRange(start, start + take)
    }

    private fun rememberWindow(
        fileId: Int,
        offset: Long,
        endExclusive: Long,
        purpose: TelegramChunkPurpose
    ) {
        downloadWindows[fileId] = DownloadWindow(
            offset = offset,
            endExclusive = endExclusive,
            priority = PLAYBACK_PRIORITY,
            purpose = purpose,
            startedAtMs = System.currentTimeMillis()
        )
    }

    private fun touchDownloadWindow(fileId: Int) {
        val now = System.currentTimeMillis()
        downloadWindows.computeIfPresent(fileId) { _, window -> window.copy(startedAtMs = now) }
    }

    private fun touchPlayback(fileId: Int) {
        if (fileId > 0) {
            playbackBusyUntilMs[fileId] = System.currentTimeMillis() + PLAYBACK_BUSY_MS
        }
    }

    private fun clearDownloadWindow(fileId: Int, offset: Long) {
        downloadWindows.computeIfPresent(fileId) { _, window ->
            if (window.offset == offset) null else window
        }
    }

    internal fun hasDownloadWindowForTests(fileId: Int): Boolean =
        downloadWindows.containsKey(resolveEffectiveFileId(fileId))

    private fun isPlaybackPurpose(purpose: TelegramChunkPurpose): Boolean =
        purpose == TelegramChunkPurpose.Streaming || purpose == TelegramChunkPurpose.Preflight

    private fun isBackgroundPurpose(purpose: TelegramChunkPurpose): Boolean =
        purpose == TelegramChunkPurpose.CacheAhead ||
            purpose == TelegramChunkPurpose.BackgroundFull ||
            purpose == TelegramChunkPurpose.Priming ||
            purpose == TelegramChunkPurpose.Metadata

    private suspend fun readReadyChunk(
        file: TelegramGatewayFile?,
        request: TelegramChunkRequest
    ): TelegramChunkResult.Ready? {
        val take = readyByteCount(file, request.fileId, request.offset, request.limit)
        if (take <= 0) return null
        coldStartFiles.remove(request.fileId)
        if (file != null) {
            val diskBytes = readExactChunkFromDisk(file, request.offset, take)
            if (diskBytes != null && diskBytes.size >= take) {
                PlaybackTelemetryRecorder.recordLocalBytesServed(request.fileId, diskBytes.size)
                return TelegramChunkResult.Ready(
                    bytes = diskBytes,
                    source = TelegramChunkReadSource.DirectDisk,
                    retryCount = 0
                )
            }
        }
        readChunkViaTdLib(request.fileId, request.offset, take)?.let { bytes ->
            PlaybackTelemetryRecorder.recordLocalBytesServed(request.fileId, bytes.size)
            return TelegramChunkResult.Ready(
                bytes = bytes,
                source = TelegramChunkReadSource.ReadFilePart,
                retryCount = 0
            )
        }
        if (file != null) {
            readExactChunkFromDisk(file, request.offset, take)?.let { bytes ->
                PlaybackTelemetryRecorder.recordLocalBytesServed(request.fileId, bytes.size)
                return TelegramChunkResult.Ready(
                    bytes = bytes,
                    source = TelegramChunkReadSource.DirectDisk,
                    retryCount = 0
                )
            }
        }
        return null
    }

    private suspend fun readyByteCount(
        file: TelegramGatewayFile?,
        fileId: Int,
        offset: Long,
        limit: Int
    ): Int {
        if (limit <= 0) return 0
        val known = knownPrefixBytes(file, offset)
        // A positive contiguous prefix is already sufficient for a partial player
        // read. Avoid a separate TDLib status roundtrip for every short Media3
        // read: it can stall the decoder even though the bytes are on disk.
        // Query TDLib only when UpdateFile has not exposed any bytes at this offset.
        val prefix = if (known > 0L) known else getDownloadedPrefixSize(fileId, offset)
        if (prefix <= 0L) return 0
        val available = min(limit.toLong(), prefix).toInt()
        val isCompleted = file?.isDownloadingCompleted == true
        val knownTotal = fileSizes[fileId] ?: (if (file != null) (if (file.size > 0L) file.size else if (file.expectedSize > 0L) file.expectedSize else 0L) else 0L)
        val isAtOrPastEnd = knownTotal > 0L &&
            saturatingPrefetchEnd(offset, available.toLong()) >= knownTotal
        val enough = available >= limit ||
            isCompleted ||
            isAtOrPastEnd ||
            (limit > MIN_PARTIAL_BYTES && available >= MIN_PARTIAL_BYTES) ||
            available > 0
        return if (enough) available else 0
    }

    private fun knownPrefixBytes(file: TelegramGatewayFile?, offset: Long): Long {
        file ?: return 0L
        if (file.isDownloadingCompleted) return Long.MAX_VALUE / 4
        if (file.downloadOffset == 0L && offset >= 0L) {
            return (file.downloadedPrefixSize - offset).coerceAtLeast(0L)
        }
        if (offset >= file.downloadOffset) {
            return (file.downloadedPrefixSize - (offset - file.downloadOffset)).coerceAtLeast(0L)
        }
        return 0L
    }

    private suspend fun startChunkDownload(
        fileId: Int,
        offset: Long,
        limit: Int,
        priority: Int,
        synchronous: Boolean = false
    ): DownloadStart = downloadCommandLocks.getOrPut(resolveEffectiveFileId(fileId)) { Mutex() }.withLock {
        startChunkDownloadSerialized(fileId, offset, limit, priority, synchronous)
    }

    internal suspend fun awaitFileState(
        fileId: Int,
        timeoutMs: Long,
        predicate: (TelegramGatewayFile) -> Boolean
    ): TelegramGatewayFile? {
        val effectiveId = resolveEffectiveFileId(fileId)
        val initial = latestFiles[effectiveId] ?: getFileStatus(effectiveId)
        if (initial != null && predicate(initial)) return initial

        val deferred = CompletableDeferred<TelegramGatewayFile>()
        val listener: (TelegramGatewayFile) -> Unit = { updated ->
            if (predicate(updated)) deferred.complete(updated)
        }
        subscribeFileUpdates(effectiveId, listener)
        return try {
            val fresh = latestFiles[effectiveId] ?: getFileStatus(effectiveId)
            if (fresh != null && predicate(fresh)) fresh
            else withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            unsubscribeFileUpdates(effectiveId, listener)
        }
    }

    internal suspend fun downloadEntireFile(fileId: Int, timeoutMs: Long): TelegramGatewayFile? {
        if (fileId <= 0) return null
        val lease = acquireLease(fileId, 0L, 0L, TelegramStreamConsumer.LocalHttp, PLAYBACK_PRIORITY)
        var reason = TelegramLeaseReleaseReason.Completed
        return try {
            val started = startChunkDownload(fileId, 0L, 0, PLAYBACK_PRIORITY)
            if (started.failure != null) {
                reason = TelegramLeaseReleaseReason.Failed
                null
            } else {
                awaitFileState(fileId, timeoutMs) { it.isDownloadingCompleted }
                    ?: run { reason = TelegramLeaseReleaseReason.TimedOut; null }
            }
        } finally {
            releaseLease(lease, reason)
        }
    }

    private suspend fun startChunkDownloadSerialized(
        fileId: Int,
        offset: Long,
        limit: Int,
        priority: Int,
        synchronous: Boolean
    ): DownloadStart {
        val effectiveId = resolveEffectiveFileId(fileId)
        val response = TelegramFileGatewayRegistry.current.downloadFile(
            effectiveId,
            priority,
            offset,
            limit,
            synchronous
        )
        if (limit > 0) PlaybackTelemetryRecorder.recordTelegramBytesRequested(effectiveId, limit.toLong())
        when (response) {
            is TelegramGatewayResult.Success -> {
                response.value?.let { retainFileStatus(it) }
                return DownloadStart(file = response.value)
            }
            is TelegramGatewayResult.Failure -> {
                val context = fileContexts[fileId] ?: fileContexts[effectiveId]
                // Only stale file references warrant a GetMessage re-resolution.
                // A temporary offline/503 response must retain the opportunity
                // to recover a genuinely expired file id on a later request.
                val staleReference = response.code == 400 || response.code == 404 ||
                    response.message.contains("FILE_REFERENCE", ignoreCase = true)
                if (staleReference && context != null && !reResolveAttempted.contains(effectiveId)) {
                    reResolveAttempted.add(effectiveId)
                    Log.i(TAG, "Download failed for fileId=$effectiveId; re-resolving via getMessage(chatId=${context.chatId}, messageId=${context.messageId})")
                    val reResolved = TelegramFileGatewayRegistry.current.reResolveFile(context.chatId, context.messageId)
                    if (reResolved != null && reResolved.fileId > 0 && reResolved.fileId != effectiveId) {
                        Log.i(TAG, "Re-resolved fileId=$effectiveId -> ${reResolved.fileId}, retrying download")
                        remappedFileIds[fileId] = reResolved.fileId
                        remappedFileIds[effectiveId] = reResolved.fileId
                        fileContexts[reResolved.fileId] = context
                        retainFileStatus(reResolved)
                        if (reResolved.size > 0L) fileSizes[reResolved.fileId] = reResolved.size
                        val retryResponse = TelegramFileGatewayRegistry.current.downloadFile(
                            reResolved.fileId,
                            priority,
                            offset,
                            limit,
                            synchronous
                        )
                        if (retryResponse is TelegramGatewayResult.Success) {
                            retryResponse.value?.let { retainFileStatus(it) }
                            return DownloadStart(file = retryResponse.value)
                        }
                    }
                }

                val message = "DownloadFile failed fileId=$effectiveId offset=$offset " +
                    "limit=$limit priority=$priority code=${response.code} message=${response.message}"
                Log.w(TAG, message)
                PlaybackDiagnostics.recordStreamError(message)
                return DownloadStart(
                    failure = if (response.fatal) {
                        TelegramChunkResult.FatalFailure(message)
                    } else {
                        TelegramChunkResult.RetryableFailure(message)
                    }
                )
            }
        }
    }

    private suspend fun getDownloadedPrefixSize(fileId: Int, offset: Long): Long =
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            TelegramFileGatewayRegistry.current.downloadedPrefixSize(fileId, offset.coerceAtLeast(0L))
        } ?: 0L

    private suspend fun getFileStatus(fileId: Int): TelegramGatewayFile? =
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            TelegramFileGatewayRegistry.current.getFile(fileId)?.also {
                retainFileStatus(it)
            }
        }

    private suspend fun readChunkViaTdLib(fileId: Int, offset: Long, limit: Int): ByteArray? =
        TelegramFileGatewayRegistry.current.readFilePart(fileId, offset.coerceAtLeast(0L), limit)

    private fun readExactChunkFromDisk(file: TelegramGatewayFile, remoteOffset: Long, limit: Int): ByteArray? {
        val path = file.localPath.takeIf { it.isNotBlank() } ?: return null
        val disk = File(path)
        if (!disk.exists()) return null

        val diskLength = disk.length()
        val localOffset = when {
            diskLength > remoteOffset &&
                (file.isDownloadingCompleted || file.downloadOffset == 0L || remoteOffset >= file.downloadOffset) ->
                remoteOffset
            remoteOffset >= file.downloadOffset &&
                file.downloadedPrefixSize > (remoteOffset - file.downloadOffset) &&
                diskLength > (remoteOffset - file.downloadOffset) ->
                remoteOffset - file.downloadOffset
            else -> return null
        }

        val availableOnDisk = (diskLength - localOffset).coerceAtLeast(0L)
        // Guard against reading sparse hole zeroes: when file is not fully downloaded,
        // limit disk read strictly to the contiguous downloaded prefix from TDLib.
        val maxContiguousBytes = if (file.isDownloadingCompleted) {
            availableOnDisk
        } else {
            val prefixAtOffset = if (file.downloadOffset == 0L) {
                (file.downloadedPrefixSize - remoteOffset).coerceAtLeast(0L)
            } else {
                (file.downloadedPrefixSize - (remoteOffset - file.downloadOffset)).coerceAtLeast(0L)
            }
            min(availableOnDisk, prefixAtOffset)
        }

        val toRead = min(limit.toLong(), maxContiguousBytes).toInt()
        if (toRead <= 0) return null

        return runCatching {
            RandomAccessFile(disk, "r").use { reader ->
                val bytes = ByteArray(toRead)
                reader.seek(localOffset)
                val read = reader.read(bytes, 0, toRead)
                if (read > 0) {
                    if (read == toRead) bytes else bytes.copyOf(read)
                } else null
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed reading Telegram chunk path=$path offset=$localOffset limit=$limit: ${error.message}")
            null
        }
    }
}

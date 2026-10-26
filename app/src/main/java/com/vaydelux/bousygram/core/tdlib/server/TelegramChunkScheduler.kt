package com.vaydelux.bousygram.core.tdlib.server

import android.util.Log
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
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
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

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
    private const val TAIL_MAX_WAIT_MS = 60_000L
    private const val MAX_PLAYBACK_WAIT_MS = 30L * 60L * 1000L
    private const val OPEN_ENDED = Long.MAX_VALUE / 4
    private const val BACKGROUND_CHUNK_WAIT_MS = 4_000L
    private const val WINDOW_TTL_MS = 90_000L
    private const val PLAYBACK_BUSY_MS = 30_000L
    private const val STATUS_TIMEOUT_MS = 500L

    const val HEAD_BURST_BYTES = 2L * 1024L * 1024L
    const val TAIL_BURST_BYTES = 4L * 1024L * 1024L

    @Volatile
    private var ewmaThroughputBps: Double = 3_000_000.0 // 3 MB/s default initial estimate

    fun getAverageThroughput(): Long = ewmaThroughputBps.toLong()

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
    private val activeCounts = ConcurrentHashMap<Int, AtomicInteger>()
    private val playbackBusyUntilMs = ConcurrentHashMap<Int, Long>()
    private val downloadWindows = ConcurrentHashMap<Int, DownloadWindow>()
    private val fileSizes = ConcurrentHashMap<Int, Long>()
    private val fileDurationsMs = ConcurrentHashMap<Int, Long>()
    private val savedTails = ConcurrentHashMap<Int, SavedTail>()
    private val playbackReady = ConcurrentHashMap.newKeySet<Int>()
    private val prepareLocks = ConcurrentHashMap<Int, Mutex>()
    private val cacheStates = ConcurrentHashMap<Int, PlaybackCacheState>()
    private val backgroundPaused = AtomicBoolean(false)
    private val coldStartFiles = ConcurrentHashMap.newKeySet<Int>()

    private val fileContexts = ConcurrentHashMap<Int, FileMessageContext>()
    private val reResolveAttempted = ConcurrentHashMap.newKeySet<Int>()
    private val remappedFileIds = ConcurrentHashMap<Int, Int>()

    private val latestFiles = ConcurrentHashMap<Int, TelegramGatewayFile>()
    private val updateListeners = ConcurrentHashMap<Int, MutableSet<(TelegramGatewayFile) -> Unit>>()
    private val prefetchJobs = ConcurrentHashMap<Int, Job>()
    private val activePrefetchRanges = ConcurrentHashMap<Int, MutableList<Pair<LongRange, Job>>>()

    init {
        TdLibClient.addUpdateHandler { update ->
            val file = (update as? TdApi.UpdateFile)?.file ?: return@addUpdateHandler
            onFileUpdated(file.toGatewayFile())
        }
    }

    fun onFileUpdated(file: TelegramGatewayFile) {
        latestFiles[file.fileId] = file
        if (file.isDownloadingCompleted) {
            cacheStates[file.fileId] = PlaybackCacheState.Cached
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

    internal fun isPlaybackBusy(fileId: Int): Boolean =
        (playbackBusyUntilMs[resolveEffectiveFileId(fileId)] ?: 0L) > System.currentTimeMillis()

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
        fileSizes.clear()
        fileDurationsMs.clear()
        savedTails.clear()
        playbackReady.clear()
        prepareLocks.clear()
        activeRequests.clear()
        cacheStates.clear()
        coldStartFiles.clear()
        fileContexts.clear()
        reResolveAttempted.clear()
        remappedFileIds.clear()
        latestFiles.clear()
        updateListeners.clear()
        prefetchJobs.values.forEach { it.cancel() }
        prefetchJobs.clear()
        activePrefetchRanges.clear()
        permitsByFile.clear()
        activeCounts.clear()
    }

    private fun recordChunkSample(fileId: Int, bytes: Int, durationMs: Long) {
        if (bytes <= 0 || durationMs <= 0) return
        val sampleBps = (bytes.toDouble() * 1000.0) / durationMs.toDouble()
        val alpha = 0.2
        ewmaThroughputBps = alpha * sampleBps + (1.0 - alpha) * ewmaThroughputBps
        if (fileId > 0) {
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
                offset + readLimit <= existing.endExclusive
            ) {
                approved = null
                return@compute existing.copy(startedAtMs = now)
            }
            approved = downloadLimit
            DownloadWindow(
                offset = offset,
                endExclusive = offset + downloadLimit.toLong(),
                priority = priority,
                purpose = purpose,
                startedAtMs = now
            )
        }
        return approved
    }

    /**
     * Opens playback: immediately schedules two high-priority downloads:
     * the first ~2 MB and the last ~4 MB of the file (clamped for small files),
     * using TDLib downloadFile with explicit offset and limit.
     */
    fun openPlayback(fileId: Int, totalSize: Long, isMp4: Boolean = true): Job = scope.launch {
        if (fileId <= 0) return@launch
        val effectiveId = resolveEffectiveFileId(fileId)
        val initialFile = latestFiles[effectiveId] ?: getFileStatus(effectiveId)
        val resolvedSize = if (totalSize > 0L) totalSize else (initialFile?.size ?: 0L)
        if (resolvedSize > 0L) noteFileSize(effectiveId, resolvedSize)
        markState(effectiveId, PlaybackCacheState.Preparing)
        touchPlayback(effectiveId)

        val isFullyCached = initialFile?.isDownloadingCompleted == true
        PlaybackTelemetryRecorder.recordFullyCachedAtStart(effectiveId, isFullyCached)

        val headLimit = if (resolvedSize > 0L) min(HEAD_BURST_BYTES, resolvedSize).toInt() else HEAD_BURST_BYTES.toInt()
        val (tailOffset, tailLimit) = when {
            isFullyCached -> Pair(-1L, 0)
            resolvedSize <= 0L -> Pair(-1L, 0)
            resolvedSize <= HEAD_BURST_BYTES -> Pair(0L, 0)
            resolvedSize <= HEAD_BURST_BYTES + TAIL_BURST_BYTES -> {
                val offset = HEAD_BURST_BYTES
                val limit = (resolvedSize - HEAD_BURST_BYTES).toInt()
                Pair(offset, limit)
            }
            else -> {
                val offset = resolvedSize - TAIL_BURST_BYTES
                val limit = TAIL_BURST_BYTES.toInt()
                Pair(offset, limit)
            }
        }

        val headJob = launch {
            val startMs = System.currentTimeMillis()
            rememberWindow(effectiveId, 0L, headLimit.toLong(), TelegramChunkPurpose.Streaming)
            startChunkDownload(effectiveId, offset = 0L, limit = headLimit, priority = PLAYBACK_PRIORITY)
            awaitPrefixReady(effectiveId, 0L, min(headLimit, MIN_PARTIAL_BYTES), timeoutMs = 15_000L)
            val elapsed = if (isFullyCached) 0L else (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            PlaybackTelemetryRecorder.recordHeaderChunkTime(effectiveId, elapsed)
        }

        if (tailLimit > 0 && tailOffset >= 0L) {
            launch {
                val startMs = System.currentTimeMillis()
                startChunkDownload(effectiveId, offset = tailOffset, limit = tailLimit, priority = PLAYBACK_PRIORITY)
                awaitPrefixReady(effectiveId, tailOffset, min(tailLimit, MIN_PARTIAL_BYTES), timeoutMs = TAIL_MAX_WAIT_MS)
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                PlaybackTelemetryRecorder.recordTailChunkTime(effectiveId, elapsed)
                readRange(effectiveId, tailOffset, min(tailLimit, CHUNK_SIZE_BYTES))?.let { bytes ->
                    savedTails[effectiveId] = SavedTail(tailOffset, bytes)
                }
            }
        } else {
            launch {
                headJob.join()
                val headTime = if (isFullyCached) 0L else (PlaybackTelemetryRecorder.peekHeaderChunkTime(effectiveId) ?: 1L)
                PlaybackTelemetryRecorder.recordTailChunkTime(effectiveId, headTime)
            }
        }
        if (isFullyCached) {
            markState(effectiveId, PlaybackCacheState.Cached)
        }
        playbackReady.add(effectiveId)
    }

    fun cancelPrefetch(fileId: Int) {
        val effectiveId = resolveEffectiveFileId(fileId)
        prefetchJobs.remove(effectiveId)?.cancel()
    }

    fun onClientDisconnected(fileId: Int, offset: Long = -1L) {
        if (fileId <= 0) return
        val effectiveId = resolveEffectiveFileId(fileId)
        Log.i(TAG, "Client disconnected mid-range fileId=$effectiveId (requested=$fileId) offset=$offset; cancelling pending work")
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
        playbackBusyUntilMs.remove(effectiveId)
        playbackReady.remove(effectiveId)
        coldStartFiles.remove(effectiveId)
        scope.launch {
            TelegramFileGatewayRegistry.current.cancelDownloadFile(effectiveId, onlyIfPending = false)
        }
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
            return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
        }

        if (isPlaybackPurpose(effectiveRequest.purpose)) {
            touchPlayback(effectiveRequest.fileId)
        } else if (isPlaybackBusy(effectiveRequest.fileId)) {
            return readReadyChunk(latestFiles[effectiveRequest.fileId] ?: getFileStatus(effectiveRequest.fileId), effectiveRequest)
                ?: TelegramChunkResult.Pending
        }

        val key = ChunkKey(effectiveRequest.fileId, effectiveRequest.offset.coerceAtLeast(0L), effectiveRequest.limit)
        return coroutineScope {
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
        val job = scope.launch {
            if (effectiveId <= 0 || length <= 0L) return@launch
            val endExclusive = offset.coerceAtLeast(0L) + length
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
                        currentOffset += result.bytes.size.coerceAtLeast(limit)
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
        prefetchJobs[effectiveId] = job
        val range = offset.coerceAtLeast(0L)..(offset.coerceAtLeast(0L) + length)
        val rangeList = activePrefetchRanges.computeIfAbsent(effectiveId) { java.util.Collections.synchronizedList(mutableListOf()) }
        val rangePair = range to job
        rangeList.add(rangePair)
        job.invokeOnCompletion {
            rangeList.remove(rangePair)
        }
        return job
    }

    /**
     * Cancels active prefetch chunks outside the [targetOffset - 2MB, targetOffset + windowBytes] range.
     */
    fun cancelOutOfWindowChunks(fileId: Int, targetOffset: Long, windowBytes: Long = 32L * 1024L * 1024L) {
        val effectiveId = resolveEffectiveFileId(fileId)
        val validStart = (targetOffset - 2L * 1024L * 1024L).coerceAtLeast(0L)
        val validEnd = targetOffset + windowBytes

        activePrefetchRanges[effectiveId]?.let { list ->
            synchronized(list) {
                val it = list.iterator()
                while (it.hasNext()) {
                    val (range, job) = it.next()
                    if (range.last < validStart || range.first > validEnd) {
                        job.cancel()
                        it.remove()
                    }
                }
            }
        }
    }

    private fun prefetchAhead(fileId: Int, offset: Long, length: Int) {
        val totalSize = fileSizes[fileId] ?: 0L
        val nextOffset = offset + length
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
            ensurePlaybackPrefix(request.fileId)?.let { return it }
            sliceSavedTail(request.fileId, request.offset, request.limit)?.let { bytes ->
                return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
            }
            val totalSize = fileSizes[request.fileId] ?: 0L
            if (totalSize > MIN_FILE_FOR_TAIL_BYTES && !savedTails.containsKey(request.fileId)) {
                val optimalTail = com.vaydelux.bousygram.core.media.ContainerHeaderParser.calculateOptimalTailSize(
                    sampleTail = null,
                    fileTotalSize = totalSize,
                    isMkv = false
                )
                val tailThreshold = totalSize - optimalTail
                if (request.offset >= tailThreshold) {
                    captureTail(request.fileId, totalSize)
                    sliceSavedTail(request.fileId, request.offset, request.limit)?.let { bytes ->
                        return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
                    }
                }
            }
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
        if (failure is TelegramChunkResult.FatalFailure) {
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
        val bitrateBps = if (durationMs > 1_000L && totalSize > 0L) {
            (totalSize * 8_000L) / durationMs
        } else {
            (ewmaThroughputBps * 8.0).toLong().coerceIn(1_500_000L, 12_000_000L)
        }
        val targetSeconds = if (isCellular) 15.0 else 45.0
        val targetBytes = ((bitrateBps * targetSeconds) / 8.0).toLong()
        val minCap = if (isCellular) 4L * 1024L * 1024L else 8L * 1024L * 1024L
        val maxCap = if (isCellular) 16L * 1024L * 1024L else 48L * 1024L * 1024L
        return targetBytes.coerceIn(minCap, maxCap)
    }

    private suspend fun handleSeek(request: TelegramChunkRequest) {
        val window = downloadWindows[request.fileId]
        val saved = savedTails[request.fileId]
        if (saved != null && request.offset >= saved.offset && request.offset < saved.offset + saved.bytes.size) return
        val totalSize = fileSizes[request.fileId] ?: 0L
        if (totalSize > HEAD_BURST_BYTES && request.offset >= (totalSize - TAIL_BURST_BYTES)) {
            return
        }
        val currentFile = latestFiles[request.fileId] ?: getFileStatus(request.fileId)
        val prefix = knownPrefixBytes(currentFile, window?.offset ?: request.offset)
        val contiguousEnd = (window?.offset ?: request.offset) + prefix + PLAYBACK_WINDOW_BYTES
        val isOutside = window == null || request.offset < window.offset || request.offset >= contiguousEnd
        if (isOutside) {
            Log.d(TAG, "Seek detected fileId=${request.fileId} offset=${request.offset}; cancelling stale in-flight downloads")
            cancelPrefetch(request.fileId)
            TelegramFileGatewayRegistry.current.cancelDownloadFile(request.fileId, onlyIfPending = false)

            // Cancel any in-flight deferred requests that are outside the new playhead window
            val newPlayheadStart = request.offset
            val newPlayheadEnd = request.offset + PLAYBACK_WINDOW_BYTES
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
            recordChunkSample(request.fileId, ready.bytes.size, System.currentTimeMillis() - fetchStart)
            return ready
        }

        // Wait via UpdateFile events for the requested prefix without polling
        val neededBytes = min(request.limit, MIN_PARTIAL_BYTES).coerceAtLeast(1)
        var ready = awaitPrefixReady(request.fileId, request.offset, neededBytes, timeoutMs = FAST_STALL_MS)
        file = latestFiles[request.fileId] ?: getFileStatus(request.fileId)
        if (ready || file != null) {
            readReadyChunk(file, request)?.let { chunk ->
                recordChunkSample(request.fileId, chunk.bytes.size, System.currentTimeMillis() - fetchStart)
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
                recordChunkSample(request.fileId, chunk.bytes.size, System.currentTimeMillis() - fetchStart)
                return chunk
            }
        }

        val finalFile = latestFiles[request.fileId] ?: getFileStatus(request.fileId) ?: file
        if (finalFile != null) {
            readExactChunkFromDisk(finalFile, request.offset, request.limit)?.let { bytes ->
                recordChunkSample(request.fileId, bytes.size, System.currentTimeMillis() - fetchStart)
                return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
            }
            readChunkViaTdLib(request.fileId, request.offset, request.limit)?.let { bytes ->
                recordChunkSample(request.fileId, bytes.size, System.currentTimeMillis() - fetchStart)
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

        val listeners = updateListeners.computeIfAbsent(fileId) { ConcurrentHashMap.newKeySet() }
        listeners.add(listener)
        try {
            val freshFile = latestFiles[fileId] ?: getFileStatus(fileId)
            if (freshFile != null && isRangeReady(freshFile, offset, minNeededBytes)) {
                return true
            }
            return withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: false
        } finally {
            listeners.remove(listener)
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
        if (knownTotal > 0L && offset + prefix >= knownTotal) return true
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
                return TelegramChunkResult.Ready(
                    bytes = diskBytes,
                    source = TelegramChunkReadSource.DirectDisk,
                    retryCount = 0
                )
            }
        }
        readChunkViaTdLib(request.fileId, request.offset, take)?.let { bytes ->
            return TelegramChunkResult.Ready(
                bytes = bytes,
                source = TelegramChunkReadSource.ReadFilePart,
                retryCount = 0
            )
        }
        if (file != null) {
            readExactChunkFromDisk(file, request.offset, take)?.let { bytes ->
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
        val prefix = if (known >= limit) known else max(known, getDownloadedPrefixSize(fileId, offset))
        if (prefix <= 0L) return 0
        val available = min(limit.toLong(), prefix).toInt()
        val isCompleted = file?.isDownloadingCompleted == true
        val knownTotal = fileSizes[fileId] ?: (if (file != null) (if (file.size > 0L) file.size else if (file.expectedSize > 0L) file.expectedSize else 0L) else 0L)
        val isAtOrPastEnd = knownTotal > 0L && offset + available >= knownTotal
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
    ): DownloadStart {
        val effectiveId = resolveEffectiveFileId(fileId)
        val response = TelegramFileGatewayRegistry.current.downloadFile(
            effectiveId,
            priority,
            offset,
            limit,
            synchronous
        )
        when (response) {
            is TelegramGatewayResult.Success -> {
                response.value?.let { latestFiles[effectiveId] = it }
                return DownloadStart(file = response.value)
            }
            is TelegramGatewayResult.Failure -> {
                val context = fileContexts[fileId] ?: fileContexts[effectiveId]
                if (context != null && !reResolveAttempted.contains(effectiveId)) {
                    reResolveAttempted.add(effectiveId)
                    Log.i(TAG, "Download failed for fileId=$effectiveId; re-resolving via getMessage(chatId=${context.chatId}, messageId=${context.messageId})")
                    val reResolved = TelegramFileGatewayRegistry.current.reResolveFile(context.chatId, context.messageId)
                    if (reResolved != null && reResolved.fileId > 0 && reResolved.fileId != effectiveId) {
                        Log.i(TAG, "Re-resolved fileId=$effectiveId -> ${reResolved.fileId}, retrying download")
                        remappedFileIds[fileId] = reResolved.fileId
                        remappedFileIds[effectiveId] = reResolved.fileId
                        fileContexts[reResolved.fileId] = context
                        latestFiles[reResolved.fileId] = reResolved
                        if (reResolved.size > 0L) fileSizes[reResolved.fileId] = reResolved.size
                        val retryResponse = TelegramFileGatewayRegistry.current.downloadFile(
                            reResolved.fileId,
                            priority,
                            offset,
                            limit,
                            synchronous
                        )
                        if (retryResponse is TelegramGatewayResult.Success) {
                            retryResponse.value?.let { latestFiles[reResolved.fileId] = it }
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
                latestFiles[fileId] = it
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

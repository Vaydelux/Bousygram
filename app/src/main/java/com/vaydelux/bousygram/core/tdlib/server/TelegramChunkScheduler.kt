package com.vaydelux.bousygram.core.tdlib.server

import android.util.Log
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
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
    private const val PLAYBACK_PRIORITY = 32
    private const val PLAYBACK_WINDOW_BYTES = 8 * 1024 * 1024
    private const val MIN_PARTIAL_BYTES = 32 * 1024
    private const val TAIL_PREFETCH_BYTES = 8L * 1024L * 1024L
    private const val MIN_FILE_FOR_TAIL_BYTES = 4L * 1024L * 1024L
    private const val STALL_MS = 20_000L
    private const val TAIL_MAX_WAIT_MS = 60_000L
    private const val MAX_PLAYBACK_WAIT_MS = 30L * 60L * 1000L
    private const val OPEN_ENDED = Long.MAX_VALUE / 4
    private const val BACKGROUND_CHUNK_WAIT_MS = 4_000L
    private const val WINDOW_TTL_MS = 90_000L
    private const val PLAYBACK_BUSY_MS = 30_000L
    private const val READY_POLL_MS = 20L
    private const val STATUS_TIMEOUT_MS = 500L

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permitsByFile = ConcurrentHashMap<Int, Semaphore>()
    private val activeRequests = ConcurrentHashMap<ChunkKey, kotlinx.coroutines.Deferred<TelegramChunkResult>>()
    private val activeCounts = ConcurrentHashMap<Int, AtomicInteger>()
    private val playbackBusyUntilMs = ConcurrentHashMap<Int, Long>()
    private val downloadWindows = ConcurrentHashMap<Int, DownloadWindow>()
    private val fileSizes = ConcurrentHashMap<Int, Long>()
    private val savedTails = ConcurrentHashMap<Int, SavedTail>()
    private val playbackReady = ConcurrentHashMap.newKeySet<Int>()
    private val prepareLocks = ConcurrentHashMap<Int, Mutex>()
    private val cacheStates = ConcurrentHashMap<Int, PlaybackCacheState>()
    private val backgroundPaused = AtomicBoolean(false)

    fun cacheState(fileId: Int): PlaybackCacheState =
        cacheStates[fileId] ?: PlaybackCacheState.Preparing

    fun markState(fileId: Int, state: PlaybackCacheState) {
        if (fileId > 0) {
            cacheStates[fileId] = state
        }
    }

    fun activeChunkCount(fileId: Int): Int =
        activeCounts[fileId]?.get() ?: 0

    fun pauseBackgroundWork() {
        backgroundPaused.set(true)
    }

    fun resumeBackgroundWork() {
        backgroundPaused.set(false)
    }

    internal fun isPlaybackBusy(fileId: Int): Boolean =
        (playbackBusyUntilMs[fileId] ?: 0L) > System.currentTimeMillis()

    internal fun noteFileSize(fileId: Int, size: Long) {
        if (fileId > 0 && size > 0L) {
            fileSizes[fileId] = size
        }
    }

    internal fun resetForTests() {
        playbackBusyUntilMs.clear()
        downloadWindows.clear()
        fileSizes.clear()
        savedTails.clear()
        playbackReady.clear()
        prepareLocks.clear()
        activeRequests.clear()
        cacheStates.clear()
    }

    /**
     * TDLib keeps one download window per file. A second downloadFile call cancels the first,
     * which is what left playback stuck on "downloading" until the player gave up.
     *
     * @return the limit to pass to downloadFile, or null when the current window already covers
     * the read and must be left alone.
     */
    internal fun prepareDownload(
        fileId: Int,
        offset: Long,
        readLimit: Int,
        downloadLimit: Int,
        priority: Int,
        purpose: TelegramChunkPurpose
    ): Int? {
        if (fileId <= 0 || readLimit <= 0 || downloadLimit <= 0) return null
        val playback = isPlaybackPurpose(purpose)
        val now = System.currentTimeMillis()
        var approved: Int? = null
        downloadWindows.compute(fileId) { _, existing ->
            val fresh = existing != null && now - existing.startedAtMs < WINDOW_TTL_MS
            if (!playback && isPlaybackBusy(fileId)) {
                approved = null
                return@compute existing
            }
            if (existing != null && existing.endExclusive >= OPEN_ENDED && isPlaybackBusy(fileId)) {
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

    suspend fun fetchChunk(request: TelegramChunkRequest): TelegramChunkResult {
        if (request.fileId <= 0) return TelegramChunkResult.FatalFailure("Invalid Telegram file id")
        if (request.limit <= 0) {
            return TelegramChunkResult.Ready(ByteArray(0), TelegramChunkReadSource.DirectDisk, retryCount = 0)
        }

        sliceSavedTail(request.fileId, request.offset, request.limit)?.let { bytes ->
            if (isPlaybackPurpose(request.purpose)) touchPlayback(request.fileId)
            return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
        }

        if (isPlaybackPurpose(request.purpose)) {
            touchPlayback(request.fileId)
        } else if (isPlaybackBusy(request.fileId)) {
            return readReadyChunk(getFileStatus(request.fileId), request)
                ?: TelegramChunkResult.Pending
        }

        val key = ChunkKey(request.fileId, request.offset.coerceAtLeast(0L), request.limit)
        return coroutineScope {
            val candidate = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val semaphore = semaphoreForFile(request.fileId)
                semaphore.withPermit {
                    val active = activeCountForFile(request.fileId)
                    active.incrementAndGet()
                    try {
                        if (isBackgroundPurpose(request.purpose) &&
                            (backgroundPaused.get() || isPlaybackBusy(request.fileId))
                        ) {
                            readReadyChunk(getFileStatus(request.fileId), request)
                                ?: TelegramChunkResult.Pending
                        } else {
                            fetchWithFallback(request.copy(offset = key.offset))
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
        priority: Int
    ): Job = scope.launch {
        if (fileId <= 0 || length <= 0L) return@launch
        val endExclusive = offset.coerceAtLeast(0L) + length
        var currentOffset = offset.coerceAtLeast(0L)
        var failures = 0
        markState(
            fileId,
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
                    fileId = fileId,
                    offset = currentOffset,
                    limit = limit,
                    priority = priority,
                    purpose = purpose
                )
            )
            when (result) {
                is TelegramChunkResult.Ready -> {
                    failures = 0
                    currentOffset += result.bytes.size.coerceAtLeast(limit)
                    PlaybackDiagnostics.recordProxyState(
                        lastStalledOffset = currentOffset,
                        chunkRetryCount = result.retryCount,
                        cacheAheadBytes = currentOffset - offset.coerceAtLeast(0L),
                        activeChunkCount = activeChunkCount(fileId),
                        proxyStatus = purpose.name,
                        readSource = result.source.name
                    )
                }
                is TelegramChunkResult.FatalFailure -> {
                    markState(fileId, PlaybackCacheState.Failed)
                    PlaybackDiagnostics.recordStreamError(result.message)
                    return@launch
                }
                else -> {
                    failures++
                    if (failures >= 3) {
                        PlaybackDiagnostics.recordStreamError("Prefetch paused fileId=$fileId offset=$currentOffset")
                        return@launch
                    }
                    delay(750L * failures)
                }
            }
        }
        if (purpose == TelegramChunkPurpose.BackgroundFull && currentOffset >= endExclusive) {
            markState(fileId, PlaybackCacheState.Cached)
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
            maybeMovePlaybackAnchor(request)
            return awaitPlaybackChunk(request)
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

        val deadline = System.currentTimeMillis() + BACKGROUND_CHUNK_WAIT_MS
        var file = getFileStatus(request.fileId)
        while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
            readReadyChunk(file, request)?.let { return it }
            delay(READY_POLL_MS)
            file = getFileStatus(request.fileId) ?: file
        }
        return TelegramChunkResult.Pending
    }

    /**
     * TDLib exposes one contiguous downloaded prefix per file. A later downloadFile call with a
     * different offset drops that prefix, so the moov atom at the end is copied aside before the
     * open-ended download from byte 0 starts. Later reads of that tail are served from the copy.
     */
    private suspend fun ensurePlaybackPrefix(fileId: Int): TelegramChunkResult? {
        if (fileId <= 0 || playbackReady.contains(fileId)) return null
        val lock = prepareLocks.getOrPut(fileId) { Mutex() }
        return lock.withLock {
            if (playbackReady.contains(fileId)) return@withLock null
            touchPlayback(fileId)
            val fatal = captureTail(fileId, fileSizes[fileId] ?: 0L)
            if (fatal != null) return@withLock fatal
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
        val tailBytes = min(TAIL_PREFETCH_BYTES, size / 2L).coerceAtLeast(1L)
        if (tailBytes < 64L * 1024L) return null
        val tailOffset = size - tailBytes
        val limit = tailBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val alreadyComplete = getFileStatus(fileId)?.isDownloadingCompleted == true
        if (!alreadyComplete && getDownloadedPrefixSize(fileId, tailOffset) < tailBytes) {
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
            awaitPrefix(fileId, tailOffset, tailBytes, limit)
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

    private suspend fun maybeMovePlaybackAnchor(request: TelegramChunkRequest) {
        val window = downloadWindows[request.fileId] ?: return
        if (window.endExclusive < OPEN_ENDED) return
        val saved = savedTails[request.fileId]
        if (saved != null && request.offset >= saved.offset && request.offset < saved.offset + saved.bytes.size) return
        if (readyByteCount(getFileStatus(request.fileId), request.fileId, request.offset, request.limit) > 0) return
        val prefix = getDownloadedPrefixSize(request.fileId, window.offset)
        val contiguousEnd = window.offset + prefix + PLAYBACK_WINDOW_BYTES
        val beforeAnchor = request.offset < window.offset
        if (!beforeAnchor && request.offset < contiguousEnd) return
        issueOpenEnded(request.fileId, request.offset)
    }

    private suspend fun awaitPlaybackChunk(request: TelegramChunkRequest): TelegramChunkResult {
        var lastPrefix = getDownloadedPrefixSize(request.fileId, request.offset)
        var lastGrowthAt = System.currentTimeMillis()
        var deadline = lastGrowthAt + STALL_MS
        val cap = lastGrowthAt + MAX_PLAYBACK_WAIT_MS
        var file = getFileStatus(request.fileId)
        var reissued = false
        while (coroutineContext.isActive && System.currentTimeMillis() < deadline && System.currentTimeMillis() < cap) {
            touchPlayback(request.fileId)
            touchDownloadWindow(request.fileId)
            readReadyChunk(file, request)?.let { return it }
            if (file?.isDownloadingCompleted == true) {
                val total = fileSizes[request.fileId] ?: (if (file.size > 0L) file.size else if (file.expectedSize > 0L) file.expectedSize else 0L)
                if (total > 0L && request.offset >= total) {
                    return TelegramChunkResult.Ready(ByteArray(0), TelegramChunkReadSource.DirectDisk, retryCount = 0)
                }
                readExactChunkFromDisk(file, request.offset, request.limit)?.let { bytes ->
                    return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.DirectDisk, retryCount = 0)
                }
                readChunkViaTdLib(request.fileId, request.offset, request.limit)?.let { bytes ->
                    return TelegramChunkResult.Ready(bytes, TelegramChunkReadSource.ReadFilePart, retryCount = 0)
                }
            }
            val prefix = getDownloadedPrefixSize(request.fileId, request.offset)
            val now = System.currentTimeMillis()
            if (prefix > lastPrefix) {
                lastPrefix = prefix
                lastGrowthAt = now
                deadline = min(cap, now + STALL_MS)
            }
            file = getFileStatus(request.fileId) ?: file
            val stalled = now - lastGrowthAt >= STALL_MS
            val active = file?.isDownloadingActive == true || file?.isDownloadingCompleted == true
            if (stalled && !active && !reissued) {
                val anchor = downloadWindows[request.fileId]
                val offset = anchor?.offset ?: request.offset
                val limit = if (anchor != null && anchor.endExclusive >= OPEN_ENDED) 0 else request.limit
                startChunkDownload(request.fileId, offset, limit, PLAYBACK_PRIORITY, synchronous = false)
                reissued = true
                lastGrowthAt = System.currentTimeMillis()
                deadline = min(cap, lastGrowthAt + STALL_MS)
            } else if (stalled && !active) {
                break
            }
            delay(READY_POLL_MS)
            file = getFileStatus(request.fileId) ?: file
        }
        readReadyChunk(file, request)?.let { return it }
        val finalFile = getFileStatus(request.fileId) ?: file
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

    private suspend fun awaitPrefix(fileId: Int, offset: Long, needed: Long, limit: Int): Boolean {
        if (getDownloadedPrefixSize(fileId, offset) >= needed) return true
        var last = 0L
        var lastGrowthAt = System.currentTimeMillis()
        var deadline = lastGrowthAt + STALL_MS
        val cap = lastGrowthAt + TAIL_MAX_WAIT_MS
        var reissued = false
        while (coroutineContext.isActive && System.currentTimeMillis() < deadline && System.currentTimeMillis() < cap) {
            touchPlayback(fileId)
            val file = getFileStatus(fileId)
            if (file?.isDownloadingCompleted == true) return true
            val prefix = getDownloadedPrefixSize(fileId, offset)
            val now = System.currentTimeMillis()
            if (prefix >= needed) return true
            if (prefix > last) {
                last = prefix
                lastGrowthAt = now
                deadline = min(cap, now + STALL_MS)
            } else if (!reissued && file?.isDownloadingActive != true && now - lastGrowthAt >= STALL_MS) {
                withTimeoutOrNull(STALL_MS) {
                    startChunkDownload(fileId, offset, limit, PLAYBACK_PRIORITY, synchronous = true)
                }
                reissued = true
                lastGrowthAt = System.currentTimeMillis()
                deadline = min(cap, lastGrowthAt + STALL_MS)
                if (getDownloadedPrefixSize(fileId, offset) >= needed) return true
            } else if (now - lastGrowthAt >= STALL_MS && file?.isDownloadingActive != true) {
                break
            }
            delay(READY_POLL_MS)
        }
        return getDownloadedPrefixSize(fileId, offset) >= needed ||
            getFileStatus(fileId)?.isDownloadingCompleted == true
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
            val ready = readReadyChunk(getFileStatus(fileId), request) ?: return null
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
        if (file != null) {
            readExactChunkFromDisk(file, request.offset, take)?.let { bytes ->
                return TelegramChunkResult.Ready(
                    bytes = bytes,
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
    ): DownloadStart =
        when (
            val response = TelegramFileGatewayRegistry.current.downloadFile(
                fileId,
                priority,
                offset,
                limit,
                synchronous
            )
        ) {
            is TelegramGatewayResult.Success -> DownloadStart(file = response.value)
            is TelegramGatewayResult.Failure -> {
                val message = "DownloadFile failed fileId=$fileId offset=$offset " +
                    "limit=$limit priority=$priority code=${response.code} message=${response.message}"
                Log.w(TAG, message)
                PlaybackDiagnostics.recordStreamError(message)
                DownloadStart(
                    failure = if (response.fatal) {
                        TelegramChunkResult.FatalFailure(message)
                    } else {
                        TelegramChunkResult.RetryableFailure(message)
                    }
                )
            }
        }

    private suspend fun getDownloadedPrefixSize(fileId: Int, offset: Long): Long =
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            TelegramFileGatewayRegistry.current.downloadedPrefixSize(fileId, offset.coerceAtLeast(0L))
        } ?: 0L

    private suspend fun getFileStatus(fileId: Int): TelegramGatewayFile? =
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            TelegramFileGatewayRegistry.current.getFile(fileId)
        }

    private suspend fun readChunkViaTdLib(fileId: Int, offset: Long, limit: Int): ByteArray? =
        TelegramFileGatewayRegistry.current.readFilePart(fileId, offset.coerceAtLeast(0L), limit)

    private fun readExactChunkFromDisk(file: TelegramGatewayFile, remoteOffset: Long, limit: Int): ByteArray? {
        val path = file.localPath.takeIf { it.isNotBlank() } ?: return null
        val disk = File(path)
        if (!disk.exists()) return null

        val diskLength = disk.length()
        val localOffset = when {
            file.isDownloadingCompleted && diskLength > remoteOffset ->
                remoteOffset
            file.downloadOffset == 0L &&
                file.downloadedPrefixSize > remoteOffset &&
                diskLength > remoteOffset ->
                remoteOffset
            remoteOffset >= file.downloadOffset &&
                file.downloadedPrefixSize > (remoteOffset - file.downloadOffset) &&
                diskLength > (remoteOffset - file.downloadOffset) ->
                remoteOffset - file.downloadOffset
            else -> return null
        }

        val availableOnDisk = (diskLength - localOffset).coerceAtLeast(0L)
        val toRead = min(limit.toLong(), availableOnDisk).toInt()
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

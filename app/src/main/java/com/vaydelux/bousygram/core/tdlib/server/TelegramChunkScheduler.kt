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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
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
    private const val PLAYBACK_CHUNK_WAIT_MS = 12_000L
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permitsByFile = ConcurrentHashMap<Int, Semaphore>()
    private val activeRequests = ConcurrentHashMap<ChunkKey, kotlinx.coroutines.Deferred<TelegramChunkResult>>()
    private val activeCounts = ConcurrentHashMap<Int, AtomicInteger>()
    private val playbackBusyUntilMs = ConcurrentHashMap<Int, Long>()
    private val downloadWindows = ConcurrentHashMap<Int, DownloadWindow>()
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

    internal fun resetForTests() {
        playbackBusyUntilMs.clear()
        downloadWindows.clear()
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
            if (fresh &&
                offset >= existing!!.offset &&
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

        if (isPlaybackPurpose(request.purpose)) {
            touchPlayback(request.fileId)
        } else if (isPlaybackBusy(request.fileId)) {
            return readReadyChunk(getFile(request.fileId), request)
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
                            readReadyChunk(getFile(request.fileId), request)
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
        val playback = isPlaybackPurpose(request.purpose)
        if (playback) touchPlayback(request.fileId)
        val downloadLimit = if (playback) max(request.limit, PLAYBACK_WINDOW_BYTES) else request.limit
        val priority = if (playback) max(request.priority, PLAYBACK_PRIORITY) else request.priority
        val issuedLimit = prepareDownload(
            fileId = request.fileId,
            offset = request.offset,
            readLimit = request.limit,
            downloadLimit = downloadLimit,
            priority = priority,
            purpose = request.purpose
        )
        if (issuedLimit != null) {
            val started = startChunkDownload(
                fileId = request.fileId,
                offset = request.offset,
                limit = issuedLimit,
                priority = priority
            )
            started.failure?.let { failure ->
                clearDownloadWindow(request.fileId, request.offset)
                return failure
            }
        }

        val deadline = System.currentTimeMillis() +
            if (playback) PLAYBACK_CHUNK_WAIT_MS else BACKGROUND_CHUNK_WAIT_MS
        var file = getFile(request.fileId)
        while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
            readReadyChunk(file, request)?.let { return it }
            delay(READY_POLL_MS)
            file = getFile(request.fileId) ?: file
        }
        return TelegramChunkResult.Pending
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
        val enough = available >= limit || (limit > MIN_PARTIAL_BYTES && available >= MIN_PARTIAL_BYTES)
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
        priority: Int
    ): DownloadStart =
        when (val response = TelegramFileGatewayRegistry.current.downloadFile(fileId, priority, offset, limit)) {
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
            file.isDownloadingCompleted && diskLength >= remoteOffset + limit ->
                remoteOffset
            file.downloadOffset == 0L &&
                file.downloadedPrefixSize >= remoteOffset + limit &&
                diskLength >= remoteOffset + limit ->
                remoteOffset
            remoteOffset >= file.downloadOffset &&
                file.downloadedPrefixSize >= (remoteOffset - file.downloadOffset) + limit &&
                diskLength >= (remoteOffset - file.downloadOffset) + limit ->
                remoteOffset - file.downloadOffset
            else -> return null
        }

        return runCatching {
            RandomAccessFile(disk, "r").use { reader ->
                val bytes = ByteArray(limit)
                reader.seek(localOffset)
                val read = reader.read(bytes, 0, limit)
                if (read == limit) bytes else null
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed reading Telegram chunk path=$path offset=$localOffset limit=$limit: ${error.message}")
            null
        }
    }
}

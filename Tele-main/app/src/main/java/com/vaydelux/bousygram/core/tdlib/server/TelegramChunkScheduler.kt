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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
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
    private const val FAST_TIMEOUT_MS = 3_000L
    private const val SAFE_TIMEOUT_MS = 5_000L
    private const val SAFE_PRIORITY = 1
    private const val READY_POLL_MS = 50L
    private const val STATUS_TIMEOUT_MS = 500L
    private const val STREAMING_QUIET_MS = 1_500L

    private data class ChunkKey(
        val fileId: Int,
        val offset: Long,
        val limit: Int
    )

    private data class DownloadStart(
        val file: TelegramGatewayFile? = null,
        val failure: TelegramChunkResult? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permitsByFile = ConcurrentHashMap<Int, Semaphore>()
    private val activeRequests = ConcurrentHashMap<ChunkKey, kotlinx.coroutines.Deferred<TelegramChunkResult>>()
    private val activeCounts = ConcurrentHashMap<Int, AtomicInteger>()
    private val streamingActiveUntilMs = ConcurrentHashMap<Int, Long>()
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

    suspend fun fetchChunk(request: TelegramChunkRequest): TelegramChunkResult {
        if (request.fileId <= 0) return TelegramChunkResult.FatalFailure("Invalid Telegram file id")
        if (request.limit <= 0) {
            return TelegramChunkResult.Ready(ByteArray(0), TelegramChunkReadSource.DirectDisk, retryCount = 0)
        }

        if (request.purpose == TelegramChunkPurpose.Streaming ||
            request.purpose == TelegramChunkPurpose.Preflight
        ) {
            streamingActiveUntilMs[request.fileId] = System.currentTimeMillis() + STREAMING_QUIET_MS
        }

        val key = ChunkKey(request.fileId, request.offset.coerceAtLeast(0L), request.limit)
        return coroutineScope {
            val candidate = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val semaphore = semaphoreForFile(request.fileId)
                semaphore.withPermit {
                    val active = activeCountForFile(request.fileId)
                    active.incrementAndGet()
                    try {
                        waitForStreamingPriority(request)
                        fetchWithFallback(request.copy(offset = key.offset))
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

    private suspend fun waitForStreamingPriority(request: TelegramChunkRequest) {
        if (request.purpose != TelegramChunkPurpose.BackgroundFull &&
            request.purpose != TelegramChunkPurpose.CacheAhead
        ) {
            return
        }
        while (coroutineContext.isActive) {
            if (backgroundPaused.get()) {
                delay(1_000L)
                continue
            }
            val quietUntil = streamingActiveUntilMs[request.fileId] ?: return
            val waitMs = quietUntil - System.currentTimeMillis()
            if (waitMs <= 0L) return
            delay(min(waitMs, 250L))
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
        val fast = fetchChunkAttempt(
            request = request,
            priority = request.priority,
            timeoutMs = FAST_TIMEOUT_MS,
            retryCount = 0
        )
        if (fast is TelegramChunkResult.Ready || fast is TelegramChunkResult.FatalFailure) return fast

        Log.w(
            TAG,
            "Fast Telegram chunk fetch pending; fallback fileId=${request.fileId} " +
                "offset=${request.offset} limit=${request.limit} purpose=${request.purpose}"
        )
        PlaybackDiagnostics.recordProxyState(
            lastStalledOffset = request.offset,
            chunkRetryCount = 1,
            cacheAheadBytes = 0L,
            activeChunkCount = activeChunkCount(request.fileId),
            proxyStatus = "fallback",
            readSource = "pending"
        )

        val safe = fetchChunkAttempt(
            request = request,
            priority = SAFE_PRIORITY,
            timeoutMs = SAFE_TIMEOUT_MS,
            retryCount = 1
        )
        if (safe is TelegramChunkResult.Ready || safe is TelegramChunkResult.FatalFailure) return safe

        return TelegramChunkResult.RetryableFailure(
            "Telegram is still caching range fileId=${request.fileId} offset=${request.offset}"
        )
    }

    private suspend fun fetchChunkAttempt(
        request: TelegramChunkRequest,
        priority: Int,
        timeoutMs: Long,
        retryCount: Int
    ): TelegramChunkResult = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.IO) {
            val started = startChunkDownload(
                fileId = request.fileId,
                offset = request.offset,
                limit = request.limit,
                priority = priority
            )
            started.failure?.let { return@withContext it }
            var file = started.file

            while (isActive) {
                if (file != null) {
                    readExactChunkFromDisk(file, request.offset, request.limit)?.let { bytes ->
                        return@withContext TelegramChunkResult.Ready(
                            bytes = bytes,
                            source = TelegramChunkReadSource.DirectDisk,
                            retryCount = retryCount
                        )
                    }
                }

                val readyBytes = getDownloadedPrefixSize(request.fileId, request.offset)
                if (readyBytes >= request.limit) {
                    readChunkViaTdLib(request.fileId, request.offset, request.limit)?.let { bytes ->
                        return@withContext TelegramChunkResult.Ready(
                            bytes = bytes,
                            source = TelegramChunkReadSource.ReadFilePart,
                            retryCount = retryCount
                        )
                    }
                    file = getFileStatus(request.fileId) ?: file
                }
                delay(READY_POLL_MS)
            }

            TelegramChunkResult.Pending
        }
    } ?: TelegramChunkResult.Pending

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

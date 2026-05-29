package com.vaydelux.bousygram.core.tdlib.server

import android.util.Log
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
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
    const val MAX_CONCURRENT_CHUNKS_PER_FILE = 4

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
        val file: TdApi.File? = null,
        val failure: TelegramChunkResult? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permitsByFile = ConcurrentHashMap<Int, Semaphore>()
    private val activeRequests = ConcurrentHashMap<ChunkKey, kotlinx.coroutines.Deferred<TelegramChunkResult>>()
    private val activeCounts = ConcurrentHashMap<Int, AtomicInteger>()
    private val streamingActiveUntilMs = ConcurrentHashMap<Int, Long>()
    private val cacheStates = ConcurrentHashMap<Int, PlaybackCacheState>()

    fun cacheState(fileId: Int): PlaybackCacheState =
        cacheStates[fileId] ?: PlaybackCacheState.Preparing

    fun markState(fileId: Int, state: PlaybackCacheState) {
        if (fileId > 0) {
            cacheStates[fileId] = state
        }
    }

    fun activeChunkCount(fileId: Int): Int =
        activeCounts[fileId]?.get() ?: 0

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
                val semaphore = permitsByFile.computeIfAbsent(request.fileId) {
                    Semaphore(MAX_CONCURRENT_CHUNKS_PER_FILE)
                }
                semaphore.withPermit {
                    val active = activeCounts.computeIfAbsent(request.fileId) { AtomicInteger(0) }
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
        if (request.purpose != TelegramChunkPurpose.BackgroundFull) return
        while (coroutineContext.isActive) {
            val quietUntil = streamingActiveUntilMs[request.fileId] ?: return
            val waitMs = quietUntil - System.currentTimeMillis()
            if (waitMs <= 0L) return
            delay(min(waitMs, 250L))
        }
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
        suspendCancellableCoroutine { continuation ->
            TdLibClient.client.send(
                TdApi.DownloadFile(
                    fileId,
                    priority,
                    offset.coerceAtLeast(0L),
                    limit.toLong(),
                    false
                ),
                handler@{ response ->
                    if (!continuation.isActive) return@handler
                    when (response) {
                        is TdApi.File -> continuation.resume(DownloadStart(file = response))
                        is TdApi.Error -> {
                            TelegramLogWrapper.logError("DownloadFile", response)
                            val message = "DownloadFile failed fileId=$fileId offset=$offset " +
                                "limit=$limit priority=$priority code=${response.code} message=${response.message}"
                            Log.w(TAG, message)
                            PlaybackDiagnostics.recordStreamError(message)
                            val failure = if (response.code == 400 || response.code == 404) {
                                TelegramChunkResult.FatalFailure(message)
                            } else {
                                TelegramChunkResult.RetryableFailure(message)
                            }
                            continuation.resume(DownloadStart(failure = failure))
                        }
                        else -> continuation.resume(DownloadStart(failure = TelegramChunkResult.Pending))
                    }
                }
            )
        }

    private suspend fun getDownloadedPrefixSize(fileId: Int, offset: Long): Long =
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                TdLibClient.client.send(
                    TdApi.GetFileDownloadedPrefixSize(fileId, offset.coerceAtLeast(0L)),
                    handler@{ response ->
                        if (!continuation.isActive) return@handler
                        val size = when (response) {
                            is TdApi.FileDownloadedPrefixSize -> response.size.coerceAtLeast(0L)
                            is TdApi.Error -> {
                                TelegramLogWrapper.logError("GetFileDownloadedPrefixSize", response)
                                0L
                            }
                            else -> 0L
                        }
                        continuation.resume(size)
                    }
                )
            }
        } ?: 0L

    private suspend fun getFileStatus(fileId: Int): TdApi.File? =
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                TdLibClient.client.send(
                    TdApi.GetFile(fileId),
                    handler@{ response ->
                        if (!continuation.isActive) return@handler
                        continuation.resume(response as? TdApi.File)
                    }
                )
            }
        }

    private suspend fun readChunkViaTdLib(fileId: Int, offset: Long, limit: Int): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            TdLibClient.client.send(
                TdApi.ReadFilePart(fileId, offset.coerceAtLeast(0L), limit.toLong()),
                handler@{ response ->
                    if (!continuation.isActive) return@handler
                    when (response) {
                        is TdApi.Data -> {
                            val data = response.data ?: ByteArray(0)
                            continuation.resume(data.takeIf { it.size == limit })
                        }
                        is TdApi.Error -> {
                            TelegramLogWrapper.logError("ReadFilePart", response)
                            continuation.resume(null)
                        }
                        else -> continuation.resume(null)
                    }
                }
            )
        }

    private fun readExactChunkFromDisk(file: TdApi.File, remoteOffset: Long, limit: Int): ByteArray? {
        val local = file.local ?: return null
        val path = local.path?.takeIf { it.isNotBlank() } ?: return null
        val disk = File(path)
        if (!disk.exists()) return null

        val diskLength = disk.length()
        val localOffset = when {
            local.isDownloadingCompleted && diskLength >= remoteOffset + limit ->
                remoteOffset
            local.downloadOffset == 0L &&
                local.downloadedPrefixSize >= remoteOffset + limit &&
                diskLength >= remoteOffset + limit ->
                remoteOffset
            remoteOffset >= local.downloadOffset &&
                local.downloadedPrefixSize >= (remoteOffset - local.downloadOffset) + limit &&
                diskLength >= (remoteOffset - local.downloadOffset) + limit ->
                remoteOffset - local.downloadOffset
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

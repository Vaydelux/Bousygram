package com.vaydelux.bousygram.core.tdlib.prefetch

import android.util.Log
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.core.cache.StoragePressureMonitor
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkPurpose
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkRequest
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkResult
import com.vaydelux.bousygram.core.tdlib.server.TelegramLeaseReleaseReason
import com.vaydelux.bousygram.core.tdlib.server.TelegramStreamConsumer
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Manages D-pad focus-based video chunk prefetching on TV cards.
 *
 * Rules:
 * - When a video card has D-pad focus for >400 ms, download its first ~2 MB (and last ~2 MB for MP4).
 * - TDLib low priority (priority 1).
 * - Cancel when focus moves away or focus is cleared.
 * - Concurrency limit: at most 2 concurrent prefetch downloads.
 * - Invariant: NEVER prefetch while video playback is active.
 */
object FocusPrefetchManager {
    private const val TAG = "FocusPrefetchManager"
    const val FOCUS_DEBOUNCE_MS = 400L
    const val PREFETCH_CHUNK_BYTES = 2L * 1024L * 1024L // ~2 MB
    const val LOW_PRIORITY = 1 // TDLib lowest priority
    const val MAX_CONCURRENT_PREFETCH = 2
    private const val TS_POSTER_STEP_BYTES = 256 * 1024
    private const val TS_POSTER_HEAD_BUDGET_MS = 6_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_CONCURRENT_PREFETCH)

    @Volatile
    private var debounceJob: Job? = null

    @Volatile
    private var focusedItem: MediaItem? = null

    private val activePrefetchJobs = ConcurrentHashMap<Int, Job>()

    fun onCardFocused(item: MediaItem) {
        val fileId = item.fileId
        if (fileId == null || fileId <= 0) {
            onFocusCleared()
            return
        }

        // Low-storage TVs must never spend flash on a focused poster video.
        // Preserve the existing foreground-only streaming path.
        if (PlaybackSessionController.isVideoPlaying() || StoragePressureMonitor.isLow()) {
            onFocusCleared()
            return
        }

        val previous = focusedItem
        // Repeated focus events for the same card must not accumulate old
        // debounce jobs, which can race to download the same file.
        cancelCurrentDebounce()
        if (previous?.fileId != fileId) {
            previous?.fileId?.let { cancelPrefetch(it) }
        }

        focusedItem = item

        // Debounce: trigger prefetch only when focus is held for >400 ms
        debounceJob = scope.launch {
            delay(FOCUS_DEBOUNCE_MS)
            if (!isActive) return@launch

            if (PlaybackSessionController.isVideoPlaying() || StoragePressureMonitor.isLow()) {
                Log.d(TAG, "Playback or low storage; skipping focus prefetch for fileId=$fileId")
                return@launch
            }

            executePrefetch(item)
        }
    }

    fun onCardFocusLost(item: MediaItem) {
        if (focusedItem?.fileId == item.fileId) {
            onFocusCleared()
        }
    }

    fun onFocusCleared() {
        cancelCurrentDebounce()
        focusedItem?.fileId?.let { cancelPrefetch(it) }
        focusedItem = null
    }

    fun cancelAll() {
        cancelCurrentDebounce()
        focusedItem = null
        val activeIds = activePrefetchJobs.keys().toList()
        activeIds.forEach { cancelPrefetch(it) }
    }

    fun isPrefetching(fileId: Int): Boolean = activePrefetchJobs.containsKey(fileId)

    fun activePrefetchCount(): Int = activePrefetchJobs.size

    fun cancelPrefetch(fileId: Int) {
        val job = activePrefetchJobs.remove(fileId)
        if (job != null) {
            job.cancel()
            Log.d(TAG, "Cancelled focus prefetch job for fileId=$fileId")
        }
        TelegramChunkScheduler.cancelPrefetch(fileId)
    }

    private fun cancelCurrentDebounce() {
        debounceJob?.cancel()
        debounceJob = null
    }

    private fun executePrefetch(item: MediaItem) {
        val fileId = item.fileId ?: return
        if (activePrefetchJobs.containsKey(fileId)) return

        // LAZY + putIfAbsent avoids the completed-before-registration race.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val lease = TelegramChunkScheduler.acquireLease(
                fileId = fileId,
                offset = 0L,
                length = item.fileSizeBytes.coerceAtLeast(PREFETCH_CHUNK_BYTES),
                consumer = TelegramStreamConsumer.Prefetch,
                priority = LOW_PRIORITY
            )
            var releaseReason = TelegramLeaseReleaseReason.Completed
            try {
                semaphore.withPermit {
                    if (!isActive || PlaybackSessionController.isVideoPlaying() ||
                        StoragePressureMonitor.isLow()) return@withPermit
                    Log.i(TAG, "Starting focus prefetch")

                    // Track file access in offline cache manager
                    runCatching { OfflineCacheManager.markFileAccessed(fileId) }

                    // Resolve file size if not populated in item
                    val fileSize = if (item.fileSizeBytes > 0L) {
                        item.fileSizeBytes
                    } else {
                        TelegramChunkScheduler.fileSize(fileId)
                    }

                    // Prime only the sequential head. TDLib maintains one effective
                    // download window per file; immediately seeking to the MP4 tail
                    // after an early partial head response evicts useful startup bytes.
                    // The actual extractor will request moov/Cues ranges if necessary.
                    val headLimit = if (fileSize > 0L) min(PREFETCH_CHUNK_BYTES, fileSize) else PREFETCH_CHUNK_BYTES
                    downloadSegment(
                        fileId = fileId,
                        offset = 0L,
                        limit = headLimit.toInt(),
                        collectContiguousTsHead = VideoFormatSupport.isTransportStream(
                            item.fileName ?: item.title, item.mimeType
                        )
                    )
                    Log.i(TAG, "Completed bounded head-only focus prefetch for fileId=$fileId")
                }
            } catch (e: CancellationException) {
                releaseReason = TelegramLeaseReleaseReason.ConsumerClosed
                throw e
            } catch (e: Exception) {
                releaseReason = TelegramLeaseReleaseReason.Failed
                Log.d(TAG, "Focus prefetch interrupted for fileId=$fileId: ${e.message}")
            } finally {
                kotlin.coroutines.coroutineContext[Job]?.let { completed ->
                    activePrefetchJobs.remove(fileId, completed)
                }
                TelegramChunkScheduler.releaseLease(lease, releaseReason)
            }
        }
        if (activePrefetchJobs.putIfAbsent(fileId, job) != null) {
            job.cancel()
            return
        }
        job.start()
    }

    private suspend fun downloadSegment(
        fileId: Int,
        offset: Long,
        limit: Int,
        collectContiguousTsHead: Boolean
    ) {
        if (limit <= 0) return
        var cursor = offset
        val endExclusive = offset + limit
        val deadline = android.os.SystemClock.elapsedRealtime() + TS_POSTER_HEAD_BUDGET_MS
        do {
            if (!kotlin.coroutines.coroutineContext.isActive ||
                PlaybackSessionController.isVideoPlaying() ||
                StoragePressureMonitor.isLow()
            ) return
            val step = if (collectContiguousTsHead) {
                min(TS_POSTER_STEP_BYTES.toLong(), endExclusive - cursor).toInt()
            } else limit
            val result = TelegramChunkScheduler.fetchChunk(
                TelegramChunkRequest(
                    fileId = fileId,
                    offset = cursor,
                    limit = step,
                    priority = LOW_PRIORITY,
                    purpose = TelegramChunkPurpose.CacheAhead
                )
            )
            when (result) {
                is TelegramChunkResult.Ready -> {
                    val next = nextContiguousPrefetchOffset(cursor, result.bytes.size, endExclusive)
                        ?: return
                    cursor = next
                }
                is TelegramChunkResult.FatalFailure -> {
                    Log.w(TAG, "Focus prefetch failed for fileId=$fileId")
                    return
                }
                TelegramChunkResult.Pending, is TelegramChunkResult.RetryableFailure -> return
            }
            // MP4 and other formats retain the original one-request focus budget.
            // Only TS attempts to collect a decodable 2 MiB head for artwork,
            // without borrowing active playback bandwidth or downloading a full file.
        } while (collectContiguousTsHead && cursor < endExclusive &&
            android.os.SystemClock.elapsedRealtime() < deadline)
    }

    internal fun nextContiguousPrefetchOffset(
        offset: Long, readBytes: Int, endExclusive: Long
    ): Long? {
        if (readBytes <= 0 || offset < 0L || offset >= endExclusive) return null
        val next = offset + readBytes
        return if (next in (offset + 1)..endExclusive) next else null
    }

    fun isMp4Container(item: MediaItem): Boolean {
        val mime = item.mimeType?.lowercase()
        if (mime == "video/mp4" || mime == "video/quicktime" || mime == "video/x-m4v") return true
        val name = (item.fileName ?: item.title).lowercase()
        return name.endsWith(".mp4") || name.endsWith(".m4v") || name.endsWith(".mov")
    }

    internal fun resetForTests() {
        cancelAll()
        activePrefetchJobs.clear()
    }
}

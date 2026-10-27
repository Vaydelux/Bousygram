package com.vaydelux.bousygram.core.tdlib.prefetch

import android.util.Log
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkPurpose
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkRequest
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkResult
import com.vaydelux.bousygram.core.tdlib.server.TelegramLeaseReleaseReason
import com.vaydelux.bousygram.core.tdlib.server.TelegramStreamConsumer
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.CoroutineScope
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

        // Invariant: Never prefetch while video playback is active
        if (PlaybackSessionController.isVideoPlaying()) {
            onFocusCleared()
            return
        }

        val previous = focusedItem
        if (previous?.fileId != fileId) {
            cancelCurrentDebounce()
            previous?.fileId?.let { cancelPrefetch(it) }
        }

        focusedItem = item

        // Debounce: trigger prefetch only when focus is held for >400 ms
        debounceJob = scope.launch {
            delay(FOCUS_DEBOUNCE_MS)
            if (!isActive) return@launch

            if (PlaybackSessionController.isVideoPlaying()) {
                Log.d(TAG, "Playback active; skipping focus prefetch for fileId=$fileId")
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

        val job = scope.launch {
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
                    if (!isActive || PlaybackSessionController.isVideoPlaying()) return@withPermit
                    Log.i(TAG, "Starting focus prefetch for fileId=$fileId title='${item.title}'")

                    // Track file access in offline cache manager
                    runCatching { OfflineCacheManager.markFileAccessed(fileId) }

                    // Resolve file size if not populated in item
                    val fileSize = if (item.fileSizeBytes > 0L) {
                        item.fileSizeBytes
                    } else {
                        TelegramChunkScheduler.fileSize(fileId)
                    }

                    // 1. Download first ~2 MB at low priority (priority 1)
                    val headLimit = if (fileSize > 0L) min(PREFETCH_CHUNK_BYTES, fileSize) else PREFETCH_CHUNK_BYTES
                    downloadSegment(fileId, offset = 0L, limit = headLimit.toInt())

                    // 2. For MP4 containers, also download last ~2 MB at low priority
                    if (isActive && !PlaybackSessionController.isVideoPlaying() && isMp4Container(item) && fileSize > PREFETCH_CHUNK_BYTES) {
                        val tailOffset = (fileSize - PREFETCH_CHUNK_BYTES).coerceAtLeast(0L)
                        val tailLimit = min(PREFETCH_CHUNK_BYTES, fileSize - tailOffset).toInt()
                        downloadSegment(fileId, offset = tailOffset, limit = tailLimit)
                    }
                    Log.i(TAG, "Completed focus prefetch for fileId=$fileId")
                }
            } catch (e: CancellationException) {
                releaseReason = TelegramLeaseReleaseReason.ConsumerClosed
                throw e
            } catch (e: Exception) {
                releaseReason = TelegramLeaseReleaseReason.Failed
                Log.d(TAG, "Focus prefetch interrupted for fileId=$fileId: ${e.message}")
            } finally {
                activePrefetchJobs.remove(fileId)
                TelegramChunkScheduler.releaseLease(lease, releaseReason)
            }
        }
        activePrefetchJobs[fileId] = job
    }

    private suspend fun downloadSegment(fileId: Int, offset: Long, limit: Int) {
        if (limit <= 0) return
        when (
            TelegramChunkScheduler.fetchChunk(
                TelegramChunkRequest(
                    fileId = fileId,
                    offset = offset,
                    limit = limit,
                    priority = LOW_PRIORITY,
                    purpose = TelegramChunkPurpose.CacheAhead
                )
            )
        ) {
            is TelegramChunkResult.FatalFailure -> Log.w(TAG, "Focus prefetch failed for fileId=$fileId")
            else -> Unit
        }
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

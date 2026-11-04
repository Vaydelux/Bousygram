package com.vaydelux.bousygram.core.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import android.util.LruCache
import com.vaydelux.bousygram.core.account.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Select a small byte-based budget for low-memory / 32-bit devices. */
internal fun seekPreviewCacheBudgetKiB(heapMaxBytes: Long, isLowRam: Boolean): Int {
    val fraction = if (isLowRam) 96L else 48L
    val maxKiB = if (isLowRam) 2_048L else 6_144L
    return (heapMaxBytes.coerceAtLeast(0L) / fraction / 1024L)
        .coerceIn(1_024L, maxKiB).toInt()
}

/** Pick the closest actually decoded frame, not an index guessed from how many
 * extraction attempts happened to succeed. Missing frame buckets remain missing.
 */
internal fun nearestSeekPreviewIndex(
    timestampsMs: List<Long>, targetMs: Long, durationMs: Long
): Int? {
    if (durationMs <= 0L || timestampsMs.isEmpty()) return null
    val target = targetMs.coerceIn(0L, durationMs)
    var closestIndex = -1
    var distance = Long.MAX_VALUE
    timestampsMs.forEachIndexed { index, timestamp ->
        if (timestamp !in 0L..durationMs) return@forEachIndexed
        val gap = if (timestamp > target) timestamp - target else target - timestamp
        if (gap < distance) {
            distance = gap
            closestIndex = index
        }
    }
    // Eight coarse frames are useful as approximate previews, not as a
    // promise of frame accuracy at an arbitrary requested timestamp.
    val toleranceMs = (durationMs / 16L).coerceAtLeast(5_000L)
    return closestIndex.takeIf { it >= 0 && distance <= toleranceMs }
}

/** Cache up to eight timestamp-identified local video frames, not network URLs. */
object SeekThumbnailManager {
    private const val TAG = "SeekThumbnailManager"
    private const val SNAPSHOT_COUNT = 8
    private const val TARGET_WIDTH = 160
    private const val TARGET_HEIGHT = 90

    private data class CapturedFrame(val timestampMs: Long, val bitmap: Bitmap)

    // Count-based eviction lets full-resolution or unexpectedly large Bitmap
    // allocations consume substantially more RAM than expected. Limit by bytes.
    private val memoryCache by lazy {
        val lowRam = runCatching {
            PlayerBufferConfig.isLowRamDevice(
                com.vaydelux.bousygram.app.BousygramApp.instance
            )
        }.getOrDefault(true)
        object : LruCache<String, List<CapturedFrame>>(
            seekPreviewCacheBudgetKiB(Runtime.getRuntime().maxMemory(), lowRam)
        ) {
            override fun sizeOf(key: String, value: List<CapturedFrame>): Int {
                val bytes = value.sumOf { it.bitmap.byteCount.toLong().coerceAtLeast(0L) }
                return ((bytes + 1023L) / 1024L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            }
        }
    }

    fun invalidateAccountResources() = memoryCache.evictAll()

    fun trimMemory(critical: Boolean) {
        if (critical) memoryCache.evictAll()
        else memoryCache.trimToSize((memoryCache.maxSize() / 2).coerceAtLeast(1))
    }

    private fun scopedKey(key: String): String? = AccountSession.current()?.let { "${it.accountId}:${it.generation}:$key" }

    fun getCachedSnapshots(key: String): List<Bitmap>? =
        scopedKey(key)?.let(memoryCache::get)?.map { it.bitmap }

    fun getSnapshotAt(key: String, positionMs: Long, durationMs: Long): Bitmap? {
        val frames = scopedKey(key)?.let(memoryCache::get) ?: return null
        val index = nearestSeekPreviewIndex(frames.map { it.timestampMs }, positionMs, durationMs)
            ?: return null
        return frames.getOrNull(index)?.bitmap
    }

    suspend fun generateSnapshots(
        key: String,
        filePathOrUrl: String,
        durationMs: Long
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val owner = AccountSession.current() ?: return@withContext emptyList()
        val accountKey = "${owner.accountId}:${owner.generation}:$key"
        val cached = memoryCache.get(accountKey)
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached.takeIf { AccountSession.isCurrent(owner) }
                ?.map { it.bitmap } ?: emptyList()
        }

        if (filePathOrUrl.isBlank() || durationMs <= 0L) {
            return@withContext emptyList()
        }

        val retriever = MediaMetadataRetriever()
        try {
            // During playback, 8 HTTP frame probes can issue many random
            // TDLib range requests, create rebuffering, and retain short-lived
            // private loopback capability URLs. Only probe a readable local file.
            // Timestamp-only seek previews remain available for streamed media.
            val file = File(filePathOrUrl)
            if (!file.exists() || !file.isFile || !file.canRead()) {
                return@withContext emptyList()
            }
            retriever.setDataSource(file.absolutePath)

            val list = mutableListOf<CapturedFrame>()
            val durationUs = durationMs * 1000L
            val stepUs = durationUs / SNAPSHOT_COUNT

            for (i in 0 until SNAPSHOT_COUNT) {
                if (!AccountSession.isCurrent(owner)) return@withContext emptyList()
                val timeUs = (i * stepUs).coerceIn(0L, durationUs)
                val scaledFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            TARGET_WIDTH,
                            TARGET_HEIGHT
                        )
                    }.getOrNull()
                } else null
                val rawFrame = scaledFrame ?: runCatching {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { Bitmap.createScaledBitmap(it, TARGET_WIDTH, TARGET_HEIGHT, true) }
                }.getOrNull()

                if (rawFrame != null) {
                    list.add(CapturedFrame(timeUs / 1000L, rawFrame))
                }
            }

            if (!AccountSession.isCurrent(owner)) return@withContext emptyList()
            if (list.isNotEmpty()) {
                memoryCache.put(accountKey, list)
                Log.d(TAG, "Generated ${list.size} seek snapshots")
            }
            list.map { it.bitmap }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not generate seek previews (${e.javaClass.simpleName})")
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }
}

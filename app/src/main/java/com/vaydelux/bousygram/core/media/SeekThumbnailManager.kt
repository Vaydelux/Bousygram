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

/**
 * Generates and caches 8 frame preview snapshots per video file for frame-accurate seeking
 * on TV and mobile scrubber strips.
 */
object SeekThumbnailManager {
    private const val TAG = "SeekThumbnailManager"
    private const val SNAPSHOT_COUNT = 8
    private const val TARGET_WIDTH = 160
    private const val TARGET_HEIGHT = 90

    // Keep up to 24 video snapshot sets in memory (24 * 8 * 160 * 90 * 4B ≈ 11 MB RAM)
    private val memoryCache = object : LruCache<String, List<Bitmap>>(24) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: List<Bitmap>?, newValue: List<Bitmap>?) {
            // Memory is managed by Android GC
        }
    }

    fun invalidateAccountResources() = memoryCache.evictAll()

    private fun scopedKey(key: String): String? = AccountSession.current()?.let { "${it.accountId}:${it.generation}:$key" }

    fun getCachedSnapshots(key: String): List<Bitmap>? {
        return scopedKey(key)?.let(memoryCache::get)
    }

    fun getSnapshotAt(key: String, positionMs: Long, durationMs: Long): Bitmap? {
        if (durationMs <= 0L) return null
        val snapshots = getCachedSnapshots(key) ?: return null
        if (snapshots.isEmpty()) return null
        val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.999f)
        val index = (fraction * snapshots.size).toInt().coerceIn(0, snapshots.lastIndex)
        return snapshots.getOrNull(index)
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
            return@withContext cached.takeIf { AccountSession.isCurrent(owner) } ?: emptyList()
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

            val list = mutableListOf<Bitmap>()
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
                    list.add(rawFrame)
                }
            }

            if (!AccountSession.isCurrent(owner)) return@withContext emptyList()
            if (list.isNotEmpty()) {
                memoryCache.put(accountKey, list)
                Log.d(TAG, "Generated ${list.size} seek snapshots")
            }
            list
        } catch (e: Throwable) {
            Log.w(TAG, "Could not generate seek previews (${e.javaClass.simpleName})")
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }
}

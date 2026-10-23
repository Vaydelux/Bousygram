package com.vaydelux.bousygram.core.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
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

    fun getCachedSnapshots(key: String): List<Bitmap>? {
        return memoryCache.get(key)
    }

    fun getSnapshotAt(key: String, positionMs: Long, durationMs: Long): Bitmap? {
        if (durationMs <= 0L) return null
        val snapshots = memoryCache.get(key) ?: return null
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
        val cached = memoryCache.get(key)
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        if (filePathOrUrl.isBlank() || durationMs <= 0L) {
            return@withContext emptyList()
        }

        val retriever = MediaMetadataRetriever()
        try {
            val file = File(filePathOrUrl)
            if (file.exists() && file.canRead()) {
                retriever.setDataSource(file.absolutePath)
            } else if (filePathOrUrl.startsWith("http://127.0.0.1") || filePathOrUrl.startsWith("http://localhost")) {
                retriever.setDataSource(filePathOrUrl, emptyMap())
            } else {
                return@withContext emptyList()
            }

            val list = mutableListOf<Bitmap>()
            val durationUs = durationMs * 1000L
            val stepUs = durationUs / SNAPSHOT_COUNT

            for (i in 0 until SNAPSHOT_COUNT) {
                val timeUs = (i * stepUs).coerceIn(0L, durationUs)
                val rawFrame = runCatching {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        TARGET_WIDTH,
                        TARGET_HEIGHT
                    )
                }.getOrNull() ?: runCatching {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { Bitmap.createScaledBitmap(it, TARGET_WIDTH, TARGET_HEIGHT, true) }
                }.getOrNull()

                if (rawFrame != null) {
                    list.add(rawFrame)
                }
            }

            if (list.isNotEmpty()) {
                memoryCache.put(key, list)
                Log.i(TAG, "Generated ${list.size} seek snapshots for $key")
            }
            list
        } catch (e: Throwable) {
            Log.w(TAG, "Could not generate seek preview thumbnails for $key: ${e.message}")
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }
}

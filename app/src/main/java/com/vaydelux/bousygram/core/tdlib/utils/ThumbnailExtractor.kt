package com.vaydelux.bousygram.core.tdlib.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailExtractor {
    private const val TAG = "ThumbnailExtractor"
    private val memoryCache = android.util.LruCache<Int, Bitmap>(50) // Cache by fileId

    data class PreviewKey(val fileId: Int, val timeBucketMs: Long)
    private val previewCache = android.util.LruCache<PreviewKey, Bitmap>(100)

    fun trimMemory(critical: Boolean) {
        if (critical) {
            memoryCache.evictAll()
            previewCache.evictAll()
        } else {
            memoryCache.trimToSize(memoryCache.maxSize() / 2)
            previewCache.trimToSize(previewCache.maxSize() / 2)
        }
    }

    suspend fun getOrExtractThumbnail(
        context: Context,
        fileId: Int,
        streamUrl: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(fileId)?.let { return@withContext it }

        // Check if there is a cached file on disk
        val cacheDir = File(context.cacheDir, "video_thumbnails")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val diskFile = File(cacheDir, "thumb_$fileId.png")
        if (diskFile.exists()) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(fileId, bitmap)
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cached thumbnail from disk", e)
            }
        }

        if (!isHlsPreview(streamUrl)) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            Log.d(TAG, "Extracting frame for fileId=$fileId from $streamUrl")
            retriever.setDataSource(streamUrl, HashMap<String, String>())
            // Retrieve frame at 5 seconds (5000000 microseconds)
            val bitmap = retriever.getFrameAtTime(5000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                // Resize for efficiency on TV layout
                val scaled = bitmap.scale(320, 180, true)
                // Cache to disk
                FileOutputStream(diskFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                memoryCache.put(fileId, scaled)
                return@withContext scaled
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to extract thumbnail for fileId=$fileId: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return@withContext null
    }

    suspend fun getPreviewFrame(
        context: Context,
        fileId: Int,
        streamUrl: String,
        timeMs: Long,
        durationMs: Long = 0L
    ): Bitmap? = withContext(Dispatchers.IO) {
        val timeBucketMs = (timeMs / 10000L) * 10000L // 10-second buckets
        val key = PreviewKey(fileId, timeBucketMs)
        previewCache.get(key)?.let { return@withContext it }

        val cacheDir = File(context.cacheDir, "seek_previews")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val diskFile = File(cacheDir, "seek_${fileId}_$timeBucketMs.png")
        if (diskFile.exists()) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    previewCache.put(key, bitmap)
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading seek preview from disk", e)
            }
        }

        if (!isHlsPreview(streamUrl)) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            Log.d(TAG, "Extracting seek preview for fileId=$fileId at ${timeBucketMs}ms duration=$durationMs")
            retriever.setDataSource(streamUrl, HashMap<String, String>())
            val bitmap = retriever.getFrameAtTime(timeBucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val scaled = bitmap.scale(320, 180, true)
                FileOutputStream(diskFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 85, out)
                }
                previewCache.put(key, scaled)
                return@withContext scaled
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to extract seek preview for fileId=$fileId timeMs=$timeMs: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return@withContext null
    }

    private fun isHlsPreview(streamUrl: String): Boolean =
        streamUrl.contains(".m3u8", ignoreCase = true)
}

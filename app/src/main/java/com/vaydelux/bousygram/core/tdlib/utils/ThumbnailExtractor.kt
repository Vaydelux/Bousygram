package com.vaydelux.bousygram.core.tdlib.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.AtomicFile
import androidx.core.graphics.scale
import com.vaydelux.bousygram.core.account.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailExtractor {
    private const val TAG = "ThumbnailExtractor"

    // Dynamically sized based on device RAM and 32-bit / 64-bit architecture
    private val isLowRam: Boolean by lazy {
        try {
            val app = com.vaydelux.bousygram.app.BousygramApp.instance
            com.vaydelux.bousygram.core.media.PlayerBufferConfig.isLowRamDevice(app)
        } catch (_: Throwable) {
            false
        }
    }

    private val maxThumbnailCache = if (isLowRam) 16 else 48
    private val maxPreviewCache = if (isLowRam) 24 else 80

    private data class ThumbnailKey(val accountId: Long, val fileId: Int)
    private val memoryCache = android.util.LruCache<ThumbnailKey, Bitmap>(maxThumbnailCache)

    data class PreviewKey(val accountId: Long, val fileId: Int, val timeBucketMs: Long)
    private val previewCache = android.util.LruCache<PreviewKey, Bitmap>(maxPreviewCache)
    private val diskWriteLock = Any()

    fun invalidateAccountResources() { memoryCache.evictAll(); previewCache.evictAll() }

    internal fun accountCachePath(accountId: Long, kind: String): String {
        require(accountId > 0L)
        require(kind == "video_thumbnails" || kind == "seek_previews")
        return "accounts/$accountId/$kind"
    }

    fun trimMemory(critical: Boolean) {
        if (critical) {
            memoryCache.evictAll()
            previewCache.evictAll()
        } else {
            memoryCache.trimToSize((memoryCache.maxSize() / 2).coerceAtLeast(1))
            previewCache.trimToSize((previewCache.maxSize() / 2).coerceAtLeast(1))
        }
    }

    suspend fun getOrExtractThumbnail(
        context: Context,
        fileId: Int,
        streamUrl: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val owner = AccountSession.current() ?: return@withContext null
        val key = ThumbnailKey(owner.accountId, fileId)
        memoryCache.get(key)?.let { return@withContext it.takeIf { AccountSession.isCurrent(owner) } }

        // Check if there is a cached file on disk
        val cacheDir = File(context.cacheDir, accountCachePath(owner.accountId, "video_thumbnails"))
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val diskFile = File(cacheDir, "thumb_$fileId.png")
        if (diskFile.exists()) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null && AccountSession.isCurrent(owner)) {
                    memoryCache.put(key, bitmap)
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cached thumbnail from disk", e)
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            Log.d(TAG, "Extracting a frame from the authenticated local stream")
            retriever.setDataSource(streamUrl, HashMap<String, String>())
            // Retrieve frame at 5 seconds (5000000 microseconds)
            val bitmap = retriever.getFrameAtTime(5000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null && AccountSession.isCurrent(owner)) {
                // Resize for efficiency on TV layout
                val scaled = bitmap.scale(320, 180, true)
                // Cache to disk
                synchronized(diskWriteLock) {
                    if (!AccountSession.isCurrent(owner)) return@withContext null
                    val atomic = AtomicFile(diskFile)
                    val out = atomic.startWrite()
                    try {
                        check(scaled.compress(Bitmap.CompressFormat.PNG, 90, out))
                        atomic.finishWrite(out)
                    } catch (failure: Throwable) { atomic.failWrite(out); throw failure }
                }
                if (!AccountSession.isCurrent(owner)) return@withContext null
                memoryCache.put(key, scaled)
                return@withContext scaled
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not extract a media thumbnail (${e.javaClass.simpleName})")
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
        val owner = AccountSession.current() ?: return@withContext null
        val timeBucketMs = (timeMs / 10000L) * 10000L // 10-second buckets
        val key = PreviewKey(owner.accountId, fileId, timeBucketMs)
        previewCache.get(key)?.let { return@withContext it.takeIf { AccountSession.isCurrent(owner) } }

        val cacheDir = File(context.cacheDir, accountCachePath(owner.accountId, "seek_previews"))
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val diskFile = File(cacheDir, "seek_${fileId}_$timeBucketMs.png")
        if (diskFile.exists()) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null && AccountSession.isCurrent(owner)) {
                    previewCache.put(key, bitmap)
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading seek preview from disk", e)
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            Log.d(TAG, "Extracting seek preview for fileId=$fileId at ${timeBucketMs}ms duration=$durationMs")
            retriever.setDataSource(streamUrl, HashMap<String, String>())
            val bitmap = retriever.getFrameAtTime(timeBucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null && AccountSession.isCurrent(owner)) {
                val scaled = bitmap.scale(320, 180, true)
                synchronized(diskWriteLock) {
                    if (!AccountSession.isCurrent(owner)) return@withContext null
                    val atomic = AtomicFile(diskFile)
                    val out = atomic.startWrite()
                    try {
                        check(scaled.compress(Bitmap.CompressFormat.PNG, 85, out))
                        atomic.finishWrite(out)
                    } catch (failure: Throwable) { atomic.failWrite(out); throw failure }
                }
                if (!AccountSession.isCurrent(owner)) return@withContext null
                previewCache.put(key, scaled)
                return@withContext scaled
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not extract a seek preview (${e.javaClass.simpleName})")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return@withContext null
    }
}

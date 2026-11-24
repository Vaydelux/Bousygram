package com.vaydelux.bousygram.core.tdlib.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.graphics.scale
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.vaydelux.bousygram.core.tdlib.server.TelegramFileGatewayRegistry
import java.io.File
import java.io.FileOutputStream

/** Budget thumbnail/preview caches by actual allocation instead of entry count. */
internal fun thumbnailMemoryBudgetKiB(heapBytes: Long, lowRam: Boolean, preview: Boolean): Int {
    val divisor = if (preview) 96L else 64L
    val ceilingKiB = when {
        lowRam && preview -> 2_048L
        lowRam -> 3_072L
        preview -> 6_144L
        else -> 8_192L
    }
    return (heapBytes.coerceAtLeast(0L) / divisor / 1024L)
        .coerceIn(1_024L, ceilingKiB).toInt()
}

object ThumbnailExtractor {
    private const val TAG = "ThumbnailExtractor"
    private const val POSTER_WIDTH = 320
    private const val POSTER_HEIGHT = 180

    /**
     * Native scaled-frame API avoids materializing a 4K/8K bitmap on 32-bit TVs.
     * Only old devices or noncompliant codecs use the full-frame fallback.
     */
    private fun boundedFrameAtTime(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val scaled = runCatching {
                retriever.getScaledFrameAtTime(
                    timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    POSTER_WIDTH, POSTER_HEIGHT
                )
            }.getOrNull()
            if (scaled != null) return scaled
        }
        val original = retriever.getFrameAtTime(
            timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        ) ?: return null
        var resized: Bitmap? = null
        return try {
            resized = original.scale(POSTER_WIDTH, POSTER_HEIGHT, true)
            resized
        } finally {
            // The fallback can temporarily allocate a large native bitmap.
            // Release it immediately rather than waiting for GC after every card.
            if (resized !== original && !original.isRecycled) original.recycle()
        }
    }

    // Dynamically sized based on device RAM and 32-bit / 64-bit architecture
    private val isLowRam: Boolean by lazy {
        try {
            val app = com.vaydelux.bousygram.app.BousygramApp.instance
            !android.os.Process.is64Bit() ||
                com.vaydelux.bousygram.core.media.PlayerBufferConfig.isLowRamDevice(app)
        } catch (_: Throwable) {
            true
        }
    }

    // Byte-based budgets prevent full-resolution source thumbnails from silently
    // occupying a large fraction of a 32-bit TV's heap.
    private val thumbnailBudgetKiB = thumbnailMemoryBudgetKiB(
        Runtime.getRuntime().maxMemory(), isLowRam, preview = false
    )
    private val previewBudgetKiB = thumbnailMemoryBudgetKiB(
        Runtime.getRuntime().maxMemory(), isLowRam, preview = true
    )

    private data class ThumbnailKey(val accountId: Long, val fileId: Int)
    private val memoryCache = object : android.util.LruCache<ThumbnailKey, Bitmap>(thumbnailBudgetKiB) {
        override fun sizeOf(key: ThumbnailKey, value: Bitmap): Int =
            ((value.byteCount.toLong() + 1023L) / 1024L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    data class PreviewKey(val accountId: Long, val fileId: Int, val timeBucketMs: Long)
    private val previewCache = object : android.util.LruCache<PreviewKey, Bitmap>(previewBudgetKiB) {
        override fun sizeOf(key: PreviewKey, value: Bitmap): Int =
            ((value.byteCount.toLong() + 1023L) / 1024L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
    private val diskWriteLock = Any()
    private val diskMaintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val frameDispatcher = Dispatchers.IO.limitedParallelism(if (isLowRam) 1 else 2)
    private val localFramePermits = Semaphore(if (isLowRam) 1 else 2)

    /** Capture identity before dispatch: queued requests cannot adopt a new login. */
    private suspend fun withFrameOwner(
        block: suspend CoroutineScope.(AccountSnapshot) -> Bitmap?
    ): Bitmap? {
        val owner = AccountSession.current() ?: return null
        return withContext(frameDispatcher) {
            if (!AccountSession.isCurrent(owner)) return@withContext null
            val frame = block(owner)
            coroutineContext.ensureActive()
            frame?.takeIf { AccountSession.isCurrent(owner) }
        }
    }

    private fun <K> publishFrame(
        owner: AccountSnapshot, cache: android.util.LruCache<K, Bitmap>,
        key: K, frame: Bitmap
    ): Boolean {
        // Cache publication is atomic with account invalidation. An obsolete
        // request cannot repopulate a cache after logout clears it.
        val published = AccountSession.runIfCurrent(owner) { cache.put(key, frame) }
        if (!published && !frame.isRecycled) frame.recycle()
        return published
    }

    /** Reclaim old per-account previews on upgrade without delaying app launch. */
    fun pruneDiskCacheAsync(context: Context) {
        val cacheDir = context.applicationContext.cacheDir
        diskMaintenanceScope.launch {
            synchronized(diskWriteLock) { recycleGeneratedDiskFiles(cacheDir) }
        }
    }

    private fun recycleGeneratedDiskFiles(cacheDir: File, protectedFile: File? = null) {
        runCatching { ThumbnailDiskQuota.enforce(cacheDir, protectedFile) }
            .onSuccess { result ->
                if (result.deletedFiles > 0) {
                    Log.i(TAG, "Recycled ${result.deletedFiles} generated cache images; freed ${result.freedBytes} bytes")
                }
            }
            .onFailure { Log.w(TAG, "Could not recycle generated thumbnail cache", it) }
    }

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

    /**
     * Telegram often sends .ts video documents without a server thumbnail. Extract
     * only from bytes TDLib has ALREADY cached; never trigger a new video download
     * just to populate the poster grid. Extraction is bounded across visible cards.
     */
    suspend fun getCachedTransportStreamThumbnail(
        context: Context,
        fileId: Int
    ): Bitmap? = withFrameOwner { owner ->
        if (fileId <= 0) return@withFrameOwner null
        val key = ThumbnailKey(owner.accountId, fileId)
        memoryCache.get(key)?.let {
            return@withFrameOwner it.takeIf { AccountSession.isCurrent(owner) }
        }
        val dir = File(context.cacheDir, accountCachePath(owner.accountId, "video_thumbnails"))
        val disk = File(dir, "thumb_$fileId.png")
        if (disk.exists()) {
            val cached = runCatching {
                android.graphics.BitmapFactory.decodeFile(disk.absolutePath)
            }.getOrNull()
            if (cached != null && AccountSession.isCurrent(owner)) {
                runCatching { disk.setLastModified(System.currentTimeMillis()) }
                return@withFrameOwner cached.takeIf { publishFrame(owner, memoryCache, key, it) }
            }
        }
        localFramePermits.withPermit {
            if (!AccountSession.isCurrent(owner)) return@withPermit null
            memoryCache.get(key)?.let { return@withPermit it.takeIf { AccountSession.isCurrent(owner) } }
            val file = withTimeoutOrNull(2_000L) {
                TelegramFileGatewayRegistry.current.getFile(fileId)
            } ?: return@withPermit null
            if (!AccountSession.isCurrent(owner) || file.localPath.isBlank()) return@withPermit null
            val local = File(file.localPath)
            if (!local.isFile || !VideoFormatSupport.canProbeLocalTsFrame(
                    completed = file.isDownloadingCompleted,
                    downloadOffset = file.downloadOffset,
                    downloadedPrefixSize = file.downloadedPrefixSize,
                    localLength = local.length()
                )
            ) return@withPermit null
            // This local-only probe never schedules or waits for a full download.
            // MediaMetadataRetriever may still reject a partial TS; retain a poster
            // placeholder until Telegram has more contiguous bytes.
            val retriever = MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(local.absolutePath)
                sequenceOf(0L, 1_000_000L, 3_000_000L)
                    .mapNotNull { time ->
                        runCatching {
                            boundedFrameAtTime(retriever, time)
                        }.getOrNull()
                    }.firstOrNull()
            } catch (e: Exception) {
                Log.d(TAG, "No local transport-stream frame for fileId=$fileId: ${e.javaClass.simpleName}")
                null
            } finally {
                runCatching { retriever.release() }
            } ?: return@withPermit null
            if (!AccountSession.isCurrent(owner)) return@withPermit null
            val scaled = frame
            synchronized(diskWriteLock) {
                if (!AccountSession.isCurrent(owner)) return@withPermit null
                if (!dir.exists() && !dir.mkdirs()) return@withPermit null
                val atomic = AtomicFile(disk)
                val out = atomic.startWrite()
                try {
                    check(scaled.compress(Bitmap.CompressFormat.PNG, 90, out))
                    atomic.finishWrite(out)
                        recycleGeneratedDiskFiles(context.cacheDir, disk)
                } catch (failure: Throwable) {
                    atomic.failWrite(out)
                    throw failure
                }
            }
            scaled.takeIf { publishFrame(owner, memoryCache, key, it) }
        }
    }

    suspend fun getOrExtractThumbnail(
        context: Context,
        fileId: Int,
        streamUrl: String
    ): Bitmap? = withFrameOwner { owner ->
        if (fileId <= 0) return@withFrameOwner null
        val key = ThumbnailKey(owner.accountId, fileId)
        memoryCache.get(key)?.let { return@withFrameOwner it.takeIf { AccountSession.isCurrent(owner) } }

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
                    runCatching { diskFile.setLastModified(System.currentTimeMillis()) }
                    return@withFrameOwner bitmap.takeIf { publishFrame(owner, memoryCache, key, it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cached thumbnail (${e.javaClass.simpleName})")
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            Log.d(TAG, "Extracting a frame from the authenticated local stream")
            retriever.setDataSource(streamUrl, HashMap<String, String>())
            // Retrieve frame at 5 seconds (5000000 microseconds)
            val bitmap = boundedFrameAtTime(retriever, 5_000_000L)
            if (bitmap != null && AccountSession.isCurrent(owner)) {
                // Resize for efficiency on TV layout
                val scaled = bitmap
                // Cache to disk
                synchronized(diskWriteLock) {
                    if (!AccountSession.isCurrent(owner)) return@withFrameOwner null
                    val atomic = AtomicFile(diskFile)
                    val out = atomic.startWrite()
                    try {
                        check(scaled.compress(Bitmap.CompressFormat.PNG, 90, out))
                        atomic.finishWrite(out)
                        recycleGeneratedDiskFiles(context.cacheDir, diskFile)
                    } catch (failure: Throwable) { atomic.failWrite(out); throw failure }
                }
                return@withFrameOwner scaled.takeIf { publishFrame(owner, memoryCache, key, it) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Throwable) {
            Log.w(TAG, "Could not extract a media thumbnail (${e.javaClass.simpleName})")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return@withFrameOwner null
    }

    suspend fun getPreviewFrame(
        context: Context,
        fileId: Int,
        streamUrl: String,
        timeMs: Long,
        durationMs: Long = 0L
    ): Bitmap? = withFrameOwner { owner ->
        if (fileId <= 0) return@withFrameOwner null
        val timeBucketMs = boundedPreviewBucketMs(timeMs, durationMs)
        val key = PreviewKey(owner.accountId, fileId, timeBucketMs)
        previewCache.get(key)?.let { return@withFrameOwner it.takeIf { AccountSession.isCurrent(owner) } }

        val cacheDir = File(context.cacheDir, accountCachePath(owner.accountId, "seek_previews"))
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val diskFile = File(cacheDir, "seek_${fileId}_$timeBucketMs.png")
        if (diskFile.exists()) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null && AccountSession.isCurrent(owner)) {
                    runCatching { diskFile.setLastModified(System.currentTimeMillis()) }
                    return@withFrameOwner bitmap.takeIf { publishFrame(owner, previewCache, key, it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading seek preview (${e.javaClass.simpleName})")
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            Log.d(TAG, "Extracting seek preview for fileId=$fileId at ${timeBucketMs}ms duration=$durationMs")
            retriever.setDataSource(streamUrl, HashMap<String, String>())
            val bitmap = boundedFrameAtTime(retriever, timeBucketMs * 1000L)
            if (bitmap != null && AccountSession.isCurrent(owner)) {
                val scaled = bitmap
                synchronized(diskWriteLock) {
                    if (!AccountSession.isCurrent(owner)) return@withFrameOwner null
                    val atomic = AtomicFile(diskFile)
                    val out = atomic.startWrite()
                    try {
                        check(scaled.compress(Bitmap.CompressFormat.PNG, 85, out))
                        atomic.finishWrite(out)
                        recycleGeneratedDiskFiles(context.cacheDir, diskFile)
                    } catch (failure: Throwable) { atomic.failWrite(out); throw failure }
                }
                return@withFrameOwner scaled.takeIf { publishFrame(owner, previewCache, key, it) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Throwable) {
            Log.w(TAG, "Could not extract a seek preview (${e.javaClass.simpleName})")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return@withFrameOwner null
    }
}

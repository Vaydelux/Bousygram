package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import org.drinkless.tdlib.TdApi
import java.util.Locale
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private class ThumbnailDownloadJob(
    val repository: SavedMessagesRepository,
    val fileId: Int,
    val callbacks: MutableList<(String?) -> Unit> = mutableListOf()
)

private val thumbnailQueueLock = Any()
private val pendingThumbnailJobs = mutableMapOf<Int, ThumbnailDownloadJob>()
private val thumbnailQueue = ArrayDeque<ThumbnailDownloadJob>()
private var activeThumbnailJobs = 0
private const val MAX_CONCURRENT_THUMBNAIL_DOWNLOADS = 3
private const val THUMBNAIL_DOWNLOAD_PRIORITY = 1
private const val THUMBNAIL_DOWNLOAD_TIMEOUT_SECONDS = 12L
private val thumbnailTimeoutScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "bousy-thumbnail-timeouts").apply { isDaemon = true }
}

fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        // H:MM:SS — e.g. 1:05:21
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        // M:SS — e.g. 1:32 or 55:04
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

internal fun SavedMessagesRepository.deleteMessageInternal(
    chatId: Long,
    messageId: Long,
    onResult: (String?) -> Unit
) {
    client.send(TdApi.DeleteMessages(chatId, longArrayOf(messageId), true)) { result ->
        when (result) {
            is TdApi.Ok -> onResult(null)
            is TdApi.Error -> onResult(result.message)
            else -> onResult("Failed to delete message")
        }
    }
}

internal fun SavedMessagesRepository.fetchThumbnailPathInternal(
    fileId: Int,
    onResult: (String?) -> Unit
) {
    if (fileId <= 0) {
        onResult(null)
        return
    }

    val jobsToStart = mutableListOf<ThumbnailDownloadJob>()
    synchronized(thumbnailQueueLock) {
        val existing = pendingThumbnailJobs[fileId]
        if (existing != null) {
            // Coalesce requests for the same thumbnail and fan out the final result to every
            // card/view that needs it, instead of silently dropping duplicate callbacks.
            existing.callbacks.add(onResult)
            return
        }
        val job = ThumbnailDownloadJob(this, fileId).also { it.callbacks.add(onResult) }
        pendingThumbnailJobs[fileId] = job
        thumbnailQueue.addLast(job)
        while (activeThumbnailJobs < MAX_CONCURRENT_THUMBNAIL_DOWNLOADS && thumbnailQueue.isNotEmpty()) {
            jobsToStart += thumbnailQueue.removeFirst()
            activeThumbnailJobs++
        }
    }
    jobsToStart.forEach(::startThumbnailDownload)
}

private fun startThumbnailDownload(job: ThumbnailDownloadJob) {
    val fileId = job.fileId
    val completed = AtomicBoolean(false)
    var updateHandler: ((TdApi.Object?) -> Unit)? = null
    var timeoutFuture: ScheduledFuture<*>? = null

    fun finish(path: String?) {
        if (!completed.compareAndSet(false, true)) return
        timeoutFuture?.cancel(false)
        updateHandler?.let { handler -> TdLibClient.removeUpdateHandler(handler) }
        completeThumbnailDownload(job.fileId, path)
    }

    timeoutFuture = thumbnailTimeoutScheduler.schedule(
        { finish(null) },
        THUMBNAIL_DOWNLOAD_TIMEOUT_SECONDS,
        TimeUnit.SECONDS
    )

    job.repository.client.send(TdApi.GetFile(fileId)) { result ->
        // A timed-out GetFile callback can still arrive later. Do not start a download or
        // register an update handler after the queue slot has already been released.
        if (completed.get()) return@send
        when (result) {
            is TdApi.Error -> finish(null)
            is TdApi.File -> {
                val existingLocal = result.local
                val existingPath = existingLocal?.path?.takeIf {
                    it.isNotBlank() && existingLocal.isDownloadingCompleted
                }
                if (existingPath != null) {
                    finish(existingPath)
                    return@send
                }

                val handler: (TdApi.Object?) -> Unit = updateHandler@{ update ->
                    val fileUpdate = update as? TdApi.UpdateFile ?: return@updateHandler
                    if (fileUpdate.file.id != fileId) return@updateHandler
                    val local = fileUpdate.file.local
                    val path = local?.path?.takeIf { it.isNotBlank() }
                    if (path != null && local?.isDownloadingCompleted == true) finish(path)
                }
                updateHandler = handler
                TdLibClient.addUpdateHandler(handler)
                // Close the small timeout race between the earlier check and handler
                // registration. finish() may have fired while the handler was being added.
                if (completed.get()) {
                    TdLibClient.removeUpdateHandler(handler)
                    return@send
                }

                // Thumbnails are low-priority background work. The old code issued up to 30
                // synchronous, priority-32 requests at once, competing with metadata and video
                // playback. ExoPlayer's selected stream should always win over poster downloads.
                job.repository.client.send(
                    TdApi.DownloadFile(fileId, THUMBNAIL_DOWNLOAD_PRIORITY, 0, 0, false)
                ) { downloadResult ->
                    when (downloadResult) {
                        is TdApi.Error -> finish(null)
                        is TdApi.File -> {
                            val local = downloadResult.local
                            val path = local?.path?.takeIf { it.isNotBlank() }
                            if (path != null && local?.isDownloadingCompleted == true) finish(path)
                        }
                    }
                }
            }
            else -> finish(null)
        }
    }
}

private fun completeThumbnailDownload(fileId: Int, path: String?) {
    val callbacks: List<(String?) -> Unit>
    val jobsToStart = mutableListOf<ThumbnailDownloadJob>()
    synchronized(thumbnailQueueLock) {
        val completedJob = pendingThumbnailJobs.remove(fileId)
        callbacks = completedJob?.callbacks?.toList().orEmpty()
        activeThumbnailJobs = (activeThumbnailJobs - 1).coerceAtLeast(0)
        while (activeThumbnailJobs < MAX_CONCURRENT_THUMBNAIL_DOWNLOADS && thumbnailQueue.isNotEmpty()) {
            jobsToStart += thumbnailQueue.removeFirst()
            activeThumbnailJobs++
        }
    }
    callbacks.forEach { callback -> runCatching { callback(path) } }
    jobsToStart.forEach(::startThumbnailDownload)
}

package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import org.drinkless.tdlib.TdApi
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val pendingThumbnailDownloads = ConcurrentHashMap<Int, Boolean>()

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
    if (pendingThumbnailDownloads.putIfAbsent(fileId, true) != null) return
    client.send(TdApi.GetFile(fileId)) { result ->
        when (result) {
            is TdApi.Error -> {
                pendingThumbnailDownloads.remove(fileId)
                onResult(null)
                return@send
            }
        }
        val file = result as? TdApi.File
        if (file == null) {
            pendingThumbnailDownloads.remove(fileId)
            onResult(null)
            return@send
        }
        val local = file.local
        val existingPath = local?.path?.takeIf { it.isNotBlank() && local.isDownloadingCompleted }
        if (existingPath != null) {
            pendingThumbnailDownloads.remove(fileId)
            onResult(existingPath)
            return@send
        }
        val completed = AtomicBoolean(false)
        lateinit var updateHandler: (TdApi.Object?) -> Unit

        fun cleanup() {
            pendingThumbnailDownloads.remove(fileId)
            TdLibClient.removeUpdateHandler(updateHandler)
        }

        updateHandler = updateHandler@{ update ->
            if (completed.get()) return@updateHandler
            val updateFile = update as? TdApi.UpdateFile ?: return@updateHandler
            if (updateFile.file.id != fileId) return@updateHandler
            val updatedLocal = updateFile.file.local
            val path = updatedLocal?.path?.takeIf { it.isNotBlank() }
            val isReady = updatedLocal?.isDownloadingCompleted == true
            if (path != null && isReady && completed.compareAndSet(false, true)) {
                cleanup()
                onResult(path)
            }
        }
        TdLibClient.addUpdateHandler(updateHandler)
        client.send(TdApi.DownloadFile(fileId, 32, 0, 0, true)) { downloadResult ->
            when (downloadResult) {
                is TdApi.File -> {
                    val downloadedLocal = downloadResult.local
                    val downloadedPath = downloadedLocal?.path?.takeIf { it.isNotBlank() }
                    if (downloadedLocal?.isDownloadingCompleted == true && downloadedPath != null && completed.compareAndSet(false, true)) {
                        cleanup()
                        onResult(downloadedPath)
                    }
                }
                is TdApi.Error -> {
                    if (completed.compareAndSet(false, true)) {
                        cleanup()
                        onResult(null)
                    }
                }
            }
        }
    }
}

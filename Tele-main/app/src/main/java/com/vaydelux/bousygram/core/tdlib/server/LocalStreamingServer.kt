package com.vaydelux.bousygram.core.tdlib.server

import android.media.MediaMetadataRetriever
import android.util.Log
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import org.drinkless.tdlib.TdApi
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

internal data class StreamPreparation(
    val fileId: Int,
    val resolvedSize: Long,
    val url: String,
    val warmupStarted: Boolean
)

object LocalStreamingServer {
    private const val TAG = "LocalStreamingServer"
    private const val VERBOSE_STREAM_LOGS = false
    private const val QUICK_SIZE_WAIT_MS = 1_500L
    private const val QUICK_SIZE_POLL_MS = 150L
    private const val INITIAL_WARMUP_BYTES = 2L * 1024L * 1024L
    private const val STREAM_CHUNK_BYTES = 8L * 1024L * 1024L
    private const val PREFETCH_CHUNK_BYTES = 4L * 1024L * 1024L
    private const val STREAM_READ_BUFFER_BYTES = 256 * 1024
    private const val DOWNLOAD_HINT_BUCKET_BYTES = 512L * 1024L
    private const val DOWNLOAD_HINT_TTL_MS = 1_000L
    private const val STREAM_BYTE_WAIT_RETRIES = 1_200
    private const val STREAM_SAFE_FALLBACK_AFTER_MS = 3_000L
    private const val STREAM_STATUS_REFRESH_MS = 500L
    private const val STREAM_FAST_PRIORITY = 32
    private const val STREAM_SAFE_PRIORITY = 1
    private var serverSocket: ServerSocket? = null
    private var serverPort: Int = 0
    private val executor = Executors.newFixedThreadPool(max(6, Runtime.getRuntime().availableProcessors() * 2))
    private val fileStatusCache = ConcurrentHashMap<Int, CachedFileStatus>()
    private val recentDownloadHints = ConcurrentHashMap<String, Long>()
    private val recentWarmups = ConcurrentHashMap<Int, Long>()
    @Volatile
    private var isRunning = false

    private data class CachedFileStatus(
        val file: TdApi.File,
        val updatedAtMs: Long
    )

    private data class LocalReadWindow(
        val path: String,
        val localOffset: Long,
        val availableBytes: Long
    )

    init {
        TdLibClient.addUpdateHandler { update ->
            val file = (update as? TdApi.UpdateFile)?.file ?: return@addUpdateHandler
            fileStatusCache[file.id] = CachedFileStatus(file, System.currentTimeMillis())
        }
    }

    fun start() {
        if (isRunning) return
        try {
            val socket = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            serverPort = socket.localPort
            isRunning = true
            executor.execute {
                acceptConnections()
            }
            Log.i(TAG, "Local loopback streaming server started on port $serverPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LocalStreamingServer", e)
            EnterprisePreferences.setLastError("Streaming server failed: ${e.message.orEmpty()}")
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        executor.shutdownNow()
    }

    fun getStreamUrl(
        fileId: Int,
        size: Long,
        title: String,
        mimeType: String? = null,
        fileName: String? = null
    ): String {
        if (!isRunning) {
            start()
        }
        if (serverPort <= 0) {
            Log.e(TAG, "Streaming server failed to bind a port")
            EnterprisePreferences.setLastError("Streaming server has no bound port")
        }
        val resolvedSize = if (size > 0L) size else resolveFileSize(fileId)
        if (TelegramProxyManager.isRunning || !BuildConfig.LEGACY_STREAMING_FALLBACK_ENABLED) {
            if (!TelegramProxyManager.isRunning) {
                TelegramProxyManager.startProxy()
            }
            return TelegramProxyManager.getStreamUrl(fileId, resolvedSize, title, mimeType, fileName)
        }
        val label = VideoFormatSupport.streamLabel(fileName, title)
        val encodedTitle = java.net.URLEncoder.encode(label, "UTF-8")
        val contentType = VideoFormatSupport.httpContentType(mimeType, fileName, title)
        val encodedMime = java.net.URLEncoder.encode(contentType, "UTF-8")
        return "http://127.0.0.1:$serverPort/stream?fileId=$fileId&size=$resolvedSize&title=$encodedTitle&mime=$encodedMime"
    }

    internal fun prepareStream(
        fileId: Int,
        declaredSize: Long,
        title: String,
        mimeType: String? = null,
        fileName: String? = null
    ): StreamPreparation? {
        if (!isRunning) start()
        var size = declaredSize.takeIf { it > 0L } ?: resolveFileSizeQuick(fileId)
        if (size <= 0L) {
            triggerDownload(fileId, 0L, INITIAL_WARMUP_BYTES)
            val deadline = System.currentTimeMillis() + QUICK_SIZE_WAIT_MS
            while (size <= 0L && System.currentTimeMillis() < deadline) {
                Thread.sleep(QUICK_SIZE_POLL_MS)
                size = resolveFileSizeQuick(fileId)
            }
        }
        if (size <= 0L) return null
        warmUp(fileId, size)
        return StreamPreparation(
            fileId = fileId,
            resolvedSize = size,
            url = getStreamUrl(fileId, size, title, mimeType, fileName),
            warmupStarted = true
        )
    }

    fun resolveFileSize(fileId: Int): Long {
        return fileSizeFromStatus(getFileStatus(fileId))
    }

    private fun resolveFileSizeQuick(fileId: Int): Long {
        fileStatusCache[fileId]?.file?.let { cached ->
            fileSizeFromStatus(cached).takeIf { it > 0L }?.let { return it }
        }
        return fileSizeFromStatus(getFileStatus(fileId, waitMs = 750L))
    }

    private fun fileSizeFromStatus(file: TdApi.File?): Long {
        file ?: return 0L
        if (file.size > 0L) return file.size
        val local = file.local ?: return 0L
        if (local.isDownloadingCompleted && !local.path.isNullOrBlank()) {
            val diskLen = File(local.path).length()
            if (diskLen > 0L) return diskLen
        }
        return local.downloadedPrefixSize.coerceAtLeast(0L)
    }

    /**
     * Starts a quick first-window warm-up and returns size without blocking for container probing.
     */
    fun ensurePlaybackReady(fileId: Int, declaredSize: Long): Long {
        if (!isRunning) start()
        var size = declaredSize.takeIf { it > 0L } ?: resolveFileSizeQuick(fileId)
        if (size <= 0L) {
            triggerDownload(fileId, 0L, INITIAL_WARMUP_BYTES)
            val deadline = System.currentTimeMillis() + QUICK_SIZE_WAIT_MS
            while (size <= 0L && System.currentTimeMillis() < deadline) {
                Thread.sleep(QUICK_SIZE_POLL_MS)
                size = resolveFileSizeQuick(fileId)
            }
        }
        if (size <= 0L) return 0L
        warmUp(fileId, size)
        return size
    }

    /**
     * Reads duration from the Telegram local cache (full file or header/tail prefetch).
     */
    fun probeDurationFromLocalFile(fileId: Int, totalSize: Long): Long {
        var size = totalSize.takeIf { it > 0L } ?: resolveFileSize(fileId)
        if (size <= 0L) return 0L

        var file = getFileStatus(fileId)
        val local = file?.local
        val needsPrefetch = local == null ||
            (!local.isDownloadingCompleted &&
                local.downloadedPrefixSize < min(8L * 1024 * 1024, size))

        if (needsPrefetch) {
            val head = min(24L * 1024 * 1024, size)
            triggerDownload(fileId, 0L, head)
            val tailChunk = min(24L * 1024 * 1024, size)
            val tailStart = max(0L, size - tailChunk)
            if (tailStart > 0L) {
                triggerDownload(fileId, tailStart, size - tailStart)
            }
            repeat(120) {
                file = getFileStatus(fileId)
                val current = file?.local ?: return@repeat
                if (current.isDownloadingCompleted) return@repeat
                if (current.downloadOffset == 0L &&
                    current.downloadedPrefixSize >= min(2L * 1024 * 1024, size)
                ) {
                    return@repeat
                }
                Thread.sleep(200)
            }
        }

        file = getFileStatus(fileId) ?: return 0L
        val path = file.local.path
        if (path.isNullOrBlank()) return 0L
        val disk = File(path)
        if (!disk.exists()) return 0L

        // Do not trust metadata from partial Telegram cache files. MediaMetadataRetriever can
        // report a short progressive estimate for long movie documents.
        if (!file.local.isDownloadingCompleted) {
            return 0L
        }

        val durationMs = extractDurationMs(path)
        if (durationMs > 0L) {
            MediaDurationResolver.rememberDuration(fileId, durationMs)
        }
        return durationMs
    }

    private fun extractDurationMs(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not read duration from path=$path: ${e.message}")
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun hasBytesAvailable(fileId: Int, rangeStart: Long, rangeEnd: Long): Boolean {
        val file = getFileStatus(fileId) ?: return false
        val local = file.local
        if (local.isDownloadingCompleted && !local.path.isNullOrBlank()) {
            return File(local.path).exists()
        }
        val prefixStart = local.downloadOffset
        val prefixEnd = prefixStart + local.downloadedPrefixSize
        return rangeStart >= prefixStart && rangeEnd < prefixEnd
    }

    fun prefetchAtPosition(fileId: Int, totalSize: Long, positionMs: Long, durationMs: Long) {
        requestPrefetch(fileId, totalSize, positionMs, durationMs)
    }

    fun getSubtitleUrl(fileId: Int, title: String): String {
        if (!isRunning) {
            start()
        }
        if (serverPort <= 0) {
            EnterprisePreferences.setLastError("Subtitle server has no bound port")
        }
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        return "http://127.0.0.1:$serverPort/subtitle?fileId=$fileId&title=$encodedTitle"
    }

    fun warmUp(fileId: Int, totalSize: Long) {
        if (totalSize <= 0L) return
        val now = System.currentTimeMillis()
        val lastWarmup = recentWarmups[fileId]
        if (lastWarmup != null && now - lastWarmup < 15_000L) return
        if (recentWarmups.size > 512) recentWarmups.clear()
        recentWarmups[fileId] = now
        TelegramChunkScheduler.prefetchRange(
            fileId = fileId,
            offset = 0L,
            length = min(INITIAL_WARMUP_BYTES, totalSize),
            purpose = TelegramChunkPurpose.Priming,
            priority = STREAM_FAST_PRIORITY
        )
        // Many large Telegram movie documents keep MP4 metadata at the tail. Priming both
        // ends lets ExoPlayer validate the container without waiting for a full download.
        val tailSize = min(PREFETCH_CHUNK_BYTES, totalSize)
        val tailOffset = (totalSize - tailSize).coerceAtLeast(0L)
        if (tailOffset > 0L) {
            TelegramChunkScheduler.prefetchRange(
                fileId = fileId,
                offset = tailOffset,
                length = tailSize,
                purpose = TelegramChunkPurpose.CacheAhead,
                priority = STREAM_SAFE_PRIORITY
            )
        }
    }

    @Volatile
    private var currentPlaylist: String? = null

    fun setPlaylist(items: List<com.vaydelux.bousygram.domain.model.MediaItem>) {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        items.forEach { item ->
            if (item.fileId != null) {
                val url = getStreamUrl(
                    item.fileId,
                    item.fileSizeBytes,
                    item.title,
                    item.mimeType,
                    item.fileName
                )
                sb.append("#EXTINF:${item.durationSeconds},${item.title}\n")
                sb.append("$url\n")
            }
        }
        currentPlaylist = sb.toString()
        if (VERBOSE_STREAM_LOGS) Log.d(TAG, "Generated playlist with ${items.size} videos")
    }

    fun getPlaylistUrl(): String {
        if (!isRunning) {
            start()
        }
        return "http://127.0.0.1:$serverPort/playlist.m3u"
    }

    private fun acceptConnections() {
        while (isRunning) {
            try {
                val socket = serverSocket?.accept() ?: break
                executor.execute {
                    handleClient(socket)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Error accepting connection", e)
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.sendBufferSize = 1024 * 512
            socket.receiveBufferSize = 1024 * 128
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return
            if (VERBOSE_STREAM_LOGS) Log.d(TAG, "HTTP Request: $firstLine")

            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val pathWithQuery = parts[1]

            var rangeHeader: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter("Range:").trim()
                }
            }

            if (pathWithQuery.startsWith("/stream")) {
                handleStreamRequest(socket, pathWithQuery, rangeHeader)
            } else if (pathWithQuery.startsWith("/subtitle")) {
                handleSubtitleRequest(socket, pathWithQuery)
            } else if (pathWithQuery.startsWith("/playlist.m3u")) {
                handlePlaylistRequest(socket)
            } else {
                send404(socket)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleStreamRequest(socket: Socket, uri: String, rangeHeader: String?) {
        val query = uri.substringAfter("?", "")
        val params = parseQueryParams(query)
        val fileId = params["fileId"]?.toIntOrNull() ?: return send400(socket, "Missing fileId")
        var size = params["size"]?.toLongOrNull() ?: 0L
        if (size <= 0L) size = resolveFileSize(fileId)
        if (size <= 0L) {
            EnterprisePreferences.setLastError("Could not resolve stream size for fileId=$fileId")
            return send503(socket, "File is not ready for streaming")
        }
        val title = params["title"] ?: ""
        val mimeParam = params["mime"]?.takeIf { it.isNotBlank() }
        val contentType = mimeParam
            ?: VideoFormatSupport.httpContentType(null, title, title)

        if (VERBOSE_STREAM_LOGS) Log.i(TAG, "Streaming fileId=$fileId size=$size MIME=$contentType range=$rangeHeader")

        val (rangeStart, rangeEnd) = parseByteRange(rangeHeader, size)
        val contentLength = rangeEnd - rangeStart + 1
        val isFullFileResponse = rangeHeader == null && rangeStart == 0L && size > 0L && rangeEnd == size - 1

        val out = socket.getOutputStream()
        val headers = StringBuilder().apply {
            if (isFullFileResponse) {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $contentType\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: $size\r\n")
            } else {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Type: $contentType\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Range: bytes $rangeStart-$rangeEnd/$size\r\n")
                append("Content-Length: $contentLength\r\n")
            }
            append("Connection: close\r\n")
            append("\r\n")
        }.toString()

        out.write(headers.toByteArray(Charsets.UTF_8))
        out.flush()

        streamBytes(fileId, size, rangeStart, rangeEnd, out, socket)
    }

    private fun handleSubtitleRequest(socket: Socket, uri: String) {
        val query = uri.substringAfter("?", "")
        val params = parseQueryParams(query)
        val fileId = params["fileId"]?.toIntOrNull() ?: return send400(socket, "Missing fileId")

        if (VERBOSE_STREAM_LOGS) Log.i(TAG, "Serving subtitle fileId=$fileId")

        val out = socket.getOutputStream()
        val localFile = downloadFileFullyBlocking(fileId)
        if (localFile == null || localFile.local.path.isNullOrEmpty()) {
            send404(socket)
            return
        }

        val file = java.io.File(localFile.local.path)
        if (!file.exists()) {
            send404(socket)
            return
        }

        val fileBytes = file.readBytes()
        val headers = StringBuilder().apply {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/plain\r\n")
            append("Content-Length: ${fileBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toString()

        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(fileBytes)
        out.flush()
    }

    private fun handlePlaylistRequest(socket: Socket) {
        val playlist = currentPlaylist ?: "#EXTM3U\n"
        val out = socket.getOutputStream()
        val bytes = playlist.toByteArray(Charsets.UTF_8)
        val headers = StringBuilder().apply {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: audio/x-mpegurl\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toString()
        if (VERBOSE_STREAM_LOGS) Log.i(TAG, "Serving generated playlist containing ${bytes.size} bytes")
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun streamBytes(
        fileId: Int,
        totalSize: Long,
        startPos: Long,
        endPos: Long,
        out: OutputStream,
        socket: Socket
    ) {
        streamRangeToOutput(fileId, totalSize, startPos, endPos, out) { !socket.isClosed }
    }

    internal fun streamRangeToOutput(
        fileId: Int,
        totalSize: Long,
        startPos: Long,
        endPos: Long,
        out: OutputStream,
        clientOpen: () -> Boolean = { true }
    ) {
        var currentOffset = startPos
        val initialFile = getFileStatus(fileId)
        val initialPath = initialFile?.local?.path.orEmpty()
        if (initialPath.isNotBlank()) {
            val requestedLength = endPos - startPos + 1
            val completeOnDisk = initialFile?.local?.isDownloadingCompleted == true
            if (completeOnDisk) {
                if (VERBOSE_STREAM_LOGS) Log.d(TAG, "Direct disk streaming fileId=$fileId path=$initialPath range=$startPos-$endPos")
                streamDiskRangeToOutput(initialPath, 0L, startPos, requestedLength, out, clientOpen)
                return
            }
        }

        val buffer = ByteArray(STREAM_READ_BUFFER_BYTES)
        var lastTriggeredOffset = -1L
        var lastTriggeredLimit = -1L
        var knownPath = initialPath
        var knownDownloadOffset = initialFile?.local?.downloadOffset ?: 0L
        var lastStatusCheckMs = 0L
        var lastProgressMs = System.currentTimeMillis()
        var fallbackIssuedAtOffset = -1L

        var diskReader: RandomAccessFile? = null
        var diskReaderPath: String? = null
        try {
            while (currentOffset <= endPos && clientOpen()) {
                val needTrigger = lastTriggeredOffset == -1L ||
                    currentOffset < lastTriggeredOffset ||
                    currentOffset >= (lastTriggeredOffset + lastTriggeredLimit) ||
                    (lastTriggeredOffset + lastTriggeredLimit - currentOffset) < 2L * 1024L * 1024L

                if (needTrigger) {
                    val remainingBytes = totalSize - currentOffset
                    val chunkSize = min(STREAM_CHUNK_BYTES, remainingBytes)
                    if (chunkSize > 0L) {
                        triggerDownload(fileId, currentOffset, chunkSize)
                        lastTriggeredOffset = currentOffset
                        lastTriggeredLimit = chunkSize
                        fallbackIssuedAtOffset = -1L
                        lastProgressMs = System.currentTimeMillis()
                        if (VERBOSE_STREAM_LOGS) Log.d(TAG, "Triggered TDLib download for fileId=$fileId at offset=$currentOffset limit=$chunkSize")
                    }
                }

                var retryCount = 0
                while (retryCount < STREAM_BYTE_WAIT_RETRIES && clientOpen()) {
                    val now = System.currentTimeMillis()
                    if (knownPath.isBlank() || now - lastStatusCheckMs > STREAM_STATUS_REFRESH_MS) {
                        val file = getFileStatus(fileId, waitMs = 750L)
                        lastStatusCheckMs = now
                        if (file != null && !file.local.path.isNullOrEmpty()) {
                            knownPath = file.local.path
                            knownDownloadOffset = file.local.downloadOffset
                            if (file.local.isDownloadingCompleted) {
                                val remaining = endPos - currentOffset + 1
                                streamDiskRangeToOutput(knownPath, 0L, currentOffset, remaining, out, clientOpen)
                                return
                            }
                        }
                    }

                    if (now - lastProgressMs >= STREAM_SAFE_FALLBACK_AFTER_MS &&
                        fallbackIssuedAtOffset != currentOffset
                    ) {
                        val fallbackLimit = min(PREFETCH_CHUNK_BYTES, totalSize - currentOffset)
                        if (fallbackLimit > 0L) {
                            Log.w(TAG, "Fast Telegram range stalled; retrying safe priority fileId=$fileId offset=$currentOffset")
                            triggerDownload(
                                fileId = fileId,
                                offset = currentOffset,
                                limit = fallbackLimit,
                                priority = STREAM_SAFE_PRIORITY,
                                force = true
                            )
                            fallbackIssuedAtOffset = currentOffset
                        }
                    }

                    val readWindow = resolveLocalReadWindow(
                        fileId = fileId,
                        path = knownPath,
                        remoteOffset = currentOffset,
                        downloadOffset = knownDownloadOffset
                    )
                    if (readWindow != null) {
                        if (diskReaderPath != readWindow.path) {
                            diskReader?.close()
                            diskReader = RandomAccessFile(readWindow.path, "r")
                            diskReaderPath = readWindow.path
                        }
                        val bytesToRead = min(
                            min(readWindow.availableBytes, endPos - currentOffset + 1),
                            buffer.size.toLong()
                        ).toInt()
                        if (bytesToRead > 0) {
                            val reader = diskReader ?: return
                            val bytesRead = readBytesFromDisk(
                                reader,
                                readWindow.localOffset,
                                buffer,
                                bytesToRead
                            )
                            if (bytesRead > 0) {
                                try {
                                    out.write(buffer, 0, bytesRead)
                                    currentOffset += bytesRead
                                    lastProgressMs = System.currentTimeMillis()
                                    break
                                } catch (e: Exception) {
                                    Log.w(TAG, "Socket write error (client closed)", e)
                                    return
                                }
                            }
                        }
                    }
                    Thread.sleep(50)
                    retryCount++
                }

                if (retryCount >= STREAM_BYTE_WAIT_RETRIES) {
                    val message = "Telegram stream stalled while caching fileId=$fileId at offset=$currentOffset"
                    Log.e(TAG, message)
                    EnterprisePreferences.setLastError(message)
                    PlaybackDiagnostics.recordStreamError(message)
                    return
                }
            }
        } finally {
            try { diskReader?.close() } catch (_: Exception) {}
        }
    }

    private fun resolveLocalReadWindow(
        fileId: Int,
        path: String,
        remoteOffset: Long,
        downloadOffset: Long
    ): LocalReadWindow? {
        if (path.isBlank()) return null
        val readyBytes = getDownloadedPrefixSize(fileId, remoteOffset, waitMs = 250L)
        if (readyBytes <= 0L) return null

        val disk = File(path)
        if (!disk.exists()) return null
        val diskLength = disk.length()

        val absoluteAvailable = diskLength - remoteOffset
        if (absoluteAvailable > 0L) {
            return LocalReadWindow(
                path = path,
                localOffset = remoteOffset,
                availableBytes = min(readyBytes, absoluteAvailable)
            )
        }

        val relativeOffset = remoteOffset - downloadOffset
        val relativeAvailable = diskLength - relativeOffset
        if (relativeOffset >= 0L && relativeAvailable > 0L) {
            return LocalReadWindow(
                path = path,
                localOffset = relativeOffset,
                availableBytes = min(readyBytes, relativeAvailable)
            )
        }

        return null
    }

    private fun getDownloadedPrefixSize(fileId: Int, offset: Long, waitMs: Long): Long {
        if (fileId <= 0) return 0L
        var result = 0L
        val latch = CountDownLatch(1)
        TdLibClient.client.send(
            TdApi.GetFileDownloadedPrefixSize(fileId, offset.coerceAtLeast(0L)),
            { response ->
                when (response) {
                    is TdApi.FileDownloadedPrefixSize -> result = response.size.coerceAtLeast(0L)
                    is TdApi.Error -> Log.w(TAG, "GetFileDownloadedPrefixSize failed fileId=$fileId offset=$offset code=${response.code} message=${response.message}")
                }
                latch.countDown()
            }
        )
        return if (latch.await(waitMs, TimeUnit.MILLISECONDS)) result else 0L
    }

    private fun streamDiskRange(
        path: String,
        fileDownloadOffset: Long,
        startPos: Long,
        length: Long,
        out: OutputStream,
        socket: Socket
    ) {
        streamDiskRangeToOutput(path, fileDownloadOffset, startPos, length, out) { !socket.isClosed }
    }

    private fun streamDiskRangeToOutput(
        path: String,
        fileDownloadOffset: Long,
        startPos: Long,
        length: Long,
        out: OutputStream,
        clientOpen: () -> Boolean = { true }
    ) {
        val buffer = ByteArray(STREAM_READ_BUFFER_BYTES)
        var remaining = length
        val localSeek = startPos - fileDownloadOffset
        if (localSeek < 0L) {
            Log.e(TAG, "Invalid disk seek path=$path remoteOffset=$startPos downloadOffset=$fileDownloadOffset")
            return
        }
        RandomAccessFile(path, "r").use { file ->
            file.seek(localSeek)
            while (remaining > 0 && clientOpen()) {
                val read = file.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            out.flush()
        }
    }

    /**
     * Hints TDLib to download bytes near [timeMs] so seek-preview extraction can read them.
     */
    fun requestPrefetch(fileId: Int, totalSize: Long, timeMs: Long, durationMs: Long) {
        if (totalSize <= 0) return
        val offset = if (durationMs > 0) {
            ((timeMs.coerceAtLeast(0) * totalSize) / durationMs).coerceIn(0L, totalSize - 1)
        } else {
            min((timeMs / 1000L) * 512_000L, totalSize - 1).coerceAtLeast(0L)
        }
        val remaining = totalSize - offset
        val chunkSize = min(PREFETCH_CHUNK_BYTES, remaining)
        if (chunkSize > 0) {
            TelegramChunkScheduler.prefetchRange(
                fileId = fileId,
                offset = offset,
                length = chunkSize,
                purpose = TelegramChunkPurpose.CacheAhead,
                priority = STREAM_SAFE_PRIORITY
            )
            if (VERBOSE_STREAM_LOGS) Log.d(TAG, "Prefetch fileId=$fileId offset=$offset limit=$chunkSize (timeMs=$timeMs)")
        }
    }

    private fun triggerDownload(
        fileId: Int,
        offset: Long,
        limit: Long,
        priority: Int = STREAM_FAST_PRIORITY,
        force: Boolean = false
    ) {
        if (fileId <= 0 || limit <= 0L) return
        val safeOffset = offset.coerceAtLeast(0L)
        val bucket = safeOffset / DOWNLOAD_HINT_BUCKET_BYTES
        val limitBucket = max(1L, limit / DOWNLOAD_HINT_BUCKET_BYTES)
        val key = "$fileId:$bucket:$limitBucket:$priority"
        val now = System.currentTimeMillis()
        val last = recentDownloadHints[key]
        if (!force && last != null && now - last < DOWNLOAD_HINT_TTL_MS) return
        if (recentDownloadHints.size > 2048) recentDownloadHints.clear()
        recentDownloadHints[key] = now
        TdLibClient.client.send(
            TdApi.DownloadFile(fileId, priority, safeOffset, limit, false),
            { response ->
                if (response is TdApi.Error) {
                    val message = "DownloadFile failed fileId=$fileId offset=$safeOffset limit=$limit priority=$priority code=${response.code} message=${response.message}"
                    Log.w(TAG, message)
                    PlaybackDiagnostics.recordStreamError(message)
                }
            }
        )
    }

    private fun getFileStatus(fileId: Int, waitMs: Long = 2_500L): TdApi.File? {
        fileStatusCache[fileId]
            ?.takeIf { System.currentTimeMillis() - it.updatedAtMs < 1_500L }
            ?.let { return it.file }

        var result: TdApi.File? = null
        val lock = java.lang.Object()
        synchronized(lock) {
            TdLibClient.client.send(
                TdApi.GetFile(fileId),
                { res ->
                    synchronized(lock) {
                        result = res as? TdApi.File
                        result?.let {
                            fileStatusCache[fileId] = CachedFileStatus(it, System.currentTimeMillis())
                        }
                        lock.notifyAll()
                    }
                }
            )
            lock.wait(waitMs)
        }
        return result
    }

    private fun downloadFileFullyBlocking(fileId: Int): TdApi.File? {
        var file = getFileStatus(fileId)
        if (file != null && file.local.isDownloadingCompleted) {
            return file
        }

        TdLibClient.client.send(
            TdApi.DownloadFile(fileId, 32, 0, 0, false),
            { }
        )

        var retryCount = 0
        while (retryCount < 300) { // 30 seconds max timeout
            file = getFileStatus(fileId)
            if (file != null && file.local.isDownloadingCompleted) {
                return file
            }
            Thread.sleep(100)
            retryCount++
        }
        return file
    }

    private fun readBytesFromDisk(path: String, offset: Long, buffer: ByteArray, length: Int): Int {
        return try {
            RandomAccessFile(path, "r").use { file ->
                file.seek(offset)
                file.read(buffer, 0, length)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading bytes from path=$path offset=$offset", e)
            -1
        }
    }

    private fun readBytesFromDisk(file: RandomAccessFile, offset: Long, buffer: ByteArray, length: Int): Int {
        return try {
            file.seek(offset)
            file.read(buffer, 0, length)
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading bytes from reusable file handle at offset=$offset", e)
            -1
        }
    }

    private fun parseByteRange(rangeHeader: String?, fileSize: Long): Pair<Long, Long> {
        if (fileSize <= 0L) return 0L to 0L
        var rangeStart = 0L
        var rangeEnd = fileSize - 1
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return rangeStart to rangeEnd
        }
        val spec = rangeHeader.substringAfter("bytes=").trim()
        val dashIndex = spec.indexOf('-')
        if (dashIndex < 0) return rangeStart to rangeEnd
        val startStr = spec.substring(0, dashIndex).trim()
        val endStr = spec.substring(dashIndex + 1).trim()
        when {
            startStr.isEmpty() && endStr.isNotEmpty() -> {
                val suffixLen = endStr.toLongOrNull() ?: 0L
                rangeStart = (fileSize - suffixLen).coerceAtLeast(0L)
                rangeEnd = fileSize - 1
            }
            startStr.isNotEmpty() && endStr.isEmpty() -> {
                rangeStart = startStr.toLongOrNull()?.coerceIn(0L, fileSize - 1) ?: 0L
                rangeEnd = fileSize - 1
            }
            startStr.isNotEmpty() && endStr.isNotEmpty() -> {
                rangeStart = startStr.toLongOrNull()?.coerceIn(0L, fileSize - 1) ?: 0L
                rangeEnd = endStr.toLongOrNull()?.coerceIn(rangeStart, fileSize - 1) ?: rangeStart
            }
        }
        if (rangeEnd < rangeStart) rangeEnd = rangeStart
        return rangeStart to rangeEnd
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isBlank()) return result
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                result[key] = runCatching {
                    java.net.URLDecoder.decode(value, "UTF-8")
                }.getOrElse {
                    EnterprisePreferences.setLastError("Malformed stream query parameter: $key")
                    value
                }
            }
        }
        return result
    }

    private fun send400(socket: Socket, message: String) {
        try {
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain\r\n\r\n$message".toByteArray())
            out.flush()
        } catch (_: Exception) {}
    }

    private fun send404(socket: Socket) {
        try {
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\nFile Not Found".toByteArray())
            out.flush()
        } catch (_: Exception) {}
    }

    private fun send503(socket: Socket, message: String) {
        try {
            PlaybackDiagnostics.recordStreamError(message)
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Type: text/plain\r\n\r\n$message".toByteArray())
            out.flush()
        } catch (_: Exception) {}
    }
}

package com.vaydelux.bousygram.core.tdlib.server

import android.media.MediaMetadataRetriever
import android.util.Log
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.domain.model.MediaItem
import org.drinkless.tdlib.TdApi
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
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
    private const val QUICK_SIZE_WAIT_MS = 4_000L
    private const val INITIAL_WARMUP_BYTES = 2L * 1024L * 1024L
    private const val STREAM_READ_BUFFER_BYTES = 256 * 1024
    private const val STREAM_FAST_PRIORITY = 32
    private const val MAX_HTTP_LINE_CHARS = 8 * 1024
    private const val MAX_HTTP_HEADER_CHARS = 32 * 1024
    private const val MAX_HTTP_HEADERS = 64
    private var serverSocket: ServerSocket? = null
    private var serverPort: Int = 0

    val port: Int get() = serverPort
    val actualPort: Int get() = serverPort
    @Volatile
    private var executor: ExecutorService = createExecutor()
    private val fileStatusCache = ConcurrentHashMap<Int, CachedFileStatus>()
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

    @Synchronized
    fun start() {
        if (isRunning) return
        if (executor.isShutdown || executor.isTerminated) executor = createExecutor()
        try {
            val listeningSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = listeningSocket
            serverPort = listeningSocket.localPort
            isRunning = true
            val listenerExecutor = executor
            listenerExecutor.execute { acceptConnections(listeningSocket, listenerExecutor) }
            Log.i(TAG, "Local loopback streaming server started on port $serverPort")
        } catch (e: Exception) {
            isRunning = false
            runCatching { serverSocket?.close() }
            serverSocket = null
            serverPort = 0
            Log.e(TAG, "Failed to start LocalStreamingServer", e)
            EnterprisePreferences.setLastError("Streaming server failed: ${e.message.orEmpty()}")
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        val socket = serverSocket
        serverSocket = null
        serverPort = 0
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        executor.shutdownNow()
    }

    /** Remaining time until [deadlineAtMs], capped at [cap]; never negative. */
    internal fun remainingWaitMs(deadlineAtMs: Long, cap: Long, nowMs: Long = System.currentTimeMillis()): Long =
        min(cap, (deadlineAtMs - nowMs).coerceAtLeast(0L))

    private fun createExecutor(): ExecutorService =
        Executors.newFixedThreadPool(max(6, Runtime.getRuntime().availableProcessors() * 2))

    fun getStreamUrl(
        fileId: Int,
        size: Long,
        title: String,
        mimeType: String? = null,
        fileName: String? = null
    ): String {
        if (!isRunning) start()
        val resolvedSize = if (size > 0L) size else resolveFileSize(fileId)
        check(isRunning && serverPort > 0) {
            EnterprisePreferences.setLastError("Streaming server has no bound port")
            "The loopback streaming server could not start; no valid stream URL can be issued."
        }
        val label = VideoFormatSupport.streamLabel(fileName, title)
        val encodedTitle = URLEncoder.encode(label, "UTF-8")
        val contentType = VideoFormatSupport.httpContentType(mimeType, fileName, title)
        val encodedMime = URLEncoder.encode(contentType, "UTF-8")
        val token = StreamAccessRegistry.issueFile(fileId)
        return "http://127.0.0.1:$serverPort/stream?fileId=$fileId&token=$token&size=$resolvedSize&title=$encodedTitle&mime=$encodedMime"
    }

    internal fun prepareStream(
        fileId: Int,
        declaredSize: Long,
        title: String,
        mimeType: String? = null,
        fileName: String? = null,
        chatId: Long = 0L,
        messageId: Long = 0L,
        deadlineAtMs: Long = System.currentTimeMillis() + QUICK_SIZE_WAIT_MS
    ): StreamPreparation? {
        if (chatId != 0L && messageId != 0L) {
            TelegramChunkScheduler.noteFileContext(fileId, chatId, messageId)
        }
        val size = resolveSizeWithWarmup(fileId, declaredSize, deadlineAtMs) ?: return null
        // A launch that already timed out must not mint an access token nobody will use.
        if (System.currentTimeMillis() >= deadlineAtMs) return null
        val contentType = VideoFormatSupport.httpContentType(mimeType, fileName, title)
        val isMp4 = contentType.contains("mp4") || fileName?.endsWith(".mp4", ignoreCase = true) == true
        TelegramChunkScheduler.openPlayback(fileId, size, isMp4 = isMp4)
        return StreamPreparation(
            fileId = fileId,
            resolvedSize = size,
            url = getStreamUrl(fileId, size, title, mimeType, fileName),
            warmupStarted = true
        )
    }

    fun resolveFileSize(fileId: Int): Long {
        fileStatusCache[fileId]?.file?.let { cached ->
            fileSizeFromStatus(cached).takeIf { it > 0L }?.let { return it }
        }
        return fileSizeFromStatus(getFileStatus(fileId))
    }

    /** Size lookup whose TDLib wait never exceeds [waitMs]; returns 0 when unknown. */
    fun resolveFileSizeWithin(fileId: Int, waitMs: Long): Long {
        fileStatusCache[fileId]?.file?.let { cached ->
            fileSizeFromStatus(cached).takeIf { it > 0L }?.let { return it }
        }
        if (waitMs <= 0L) return 0L
        return fileSizeFromStatus(getFileStatus(fileId, waitMs = waitMs))
    }

    private fun resolveFileSizeQuick(fileId: Int, deadlineAtMs: Long = Long.MAX_VALUE): Long =
        resolveFileSizeWithin(fileId, remainingWaitMs(deadlineAtMs, 750L))

    private fun fileSizeFromStatus(file: TdApi.File?): Long {
        file ?: return 0L
        if (file.size > 0L) return file.size
        if (file.expectedSize > 0L) return file.expectedSize
        val local = file.local ?: return 0L
        if (local.isDownloadingCompleted && !local.path.isNullOrBlank()) {
            val diskLen = File(local.path).length()
            if (diskLen > 0L) return diskLen
        }
        return 0L
    }

    /**
     * Starts a quick first-window warm-up and returns size without blocking for container probing.
     */
    fun ensurePlaybackReady(fileId: Int, declaredSize: Long): Long {
        return resolveSizeWithWarmup(fileId, declaredSize, System.currentTimeMillis() + QUICK_SIZE_WAIT_MS) ?: 0L
    }

    /**
     * Shared size-resolution path used by [prepareStream] and [ensurePlaybackReady]. Returns the
     * real Telegram file size and does not start a second download when that size is already known.
     *
     * @return the resolved size in bytes, or null if no positive size could be determined.
     */
    private fun resolveSizeWithWarmup(fileId: Int, declaredSize: Long, deadlineAtMs: Long): Long? {
        if (!isRunning) start()
        var size = declaredSize.takeIf { it > 0L } ?: resolveFileSizeQuick(fileId, deadlineAtMs)
        if (size <= 0L) {
            TelegramChunkScheduler.prefetchRange(
                fileId = fileId,
                offset = 0L,
                length = INITIAL_WARMUP_BYTES,
                purpose = TelegramChunkPurpose.Preflight,
                priority = STREAM_FAST_PRIORITY
            )
            val waitMs = remainingWaitMs(deadlineAtMs, QUICK_SIZE_WAIT_MS)
            val metadata = kotlinx.coroutines.runBlocking {
                TelegramChunkScheduler.awaitFileState(fileId, waitMs) { it.size > 0L || it.expectedSize > 0L }
            }
            size = metadata?.let { if (it.size > 0L) it.size else it.expectedSize } ?: 0L
        }
        if (size <= 0L) return null
        TelegramChunkScheduler.noteFileSize(fileId, size)
        return size
    }

    /**
     * Reads duration from the Telegram local cache (full file or header/tail prefetch).
     */
    fun probeDurationFromLocalFile(fileId: Int, totalSize: Long): Long {
        var size = totalSize.takeIf { it > 0L } ?: resolveFileSize(fileId)
        if (size <= 0L) return 0L
        if (TelegramChunkScheduler.isPlaybackBusy(fileId)) return 0L

        var file = getFileStatus(fileId)
        val local = file?.local
        if (local?.isDownloadingCompleted != true) return 0L
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

    fun prefetchAtPosition(fileId: Int, totalSize: Long, positionMs: Long, durationMs: Long) {
        requestPrefetch(fileId, totalSize, positionMs, durationMs)
    }

    fun getSubtitleUrl(fileId: Int, title: String): String {
        if (!isRunning) start()
        check(isRunning && serverPort in 1..65535) {
            EnterprisePreferences.setLastError("Subtitle server has no bound port")
            "The loopback streaming server could not start; no valid subtitle URL can be issued."
        }
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val token = StreamAccessRegistry.issueFile(fileId)
        return "http://127.0.0.1:$serverPort/subtitle?fileId=$fileId&token=$token&title=$encodedTitle"
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
            purpose = TelegramChunkPurpose.Preflight,
            priority = STREAM_FAST_PRIORITY
        )
    }

    @Volatile
    private var currentPlaylist: String? = null

    fun setPlaylist(items: List<MediaItem>) {
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
        if (!isRunning) start()
        check(isRunning && serverPort > 0) {
            "The loopback streaming server could not start; no valid playlist URL can be issued."
        }
        val token = StreamAccessRegistry.issuePlaylist()
        return "http://127.0.0.1:$serverPort/playlist.m3u?token=$token"
    }

    private fun acceptConnections(listeningSocket: ServerSocket, listenerExecutor: ExecutorService) {
        while (isRunning && serverSocket === listeningSocket) {
            try {
                val clientSocket = listeningSocket.accept()
                try {
                    listenerExecutor.execute { handleClient(clientSocket) }
                } catch (rejected: java.util.concurrent.RejectedExecutionException) {
                    runCatching { clientSocket.close() }
                    if (isRunning && serverSocket === listeningSocket) {
                        Log.w(TAG, "Rejected loopback client while streaming server is shutting down")
                    }
                }
            } catch (e: Exception) {
                if (isRunning && serverSocket === listeningSocket) {
                    Log.e(TAG, "Error accepting connection", e)
                }
                break
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 3_000
            socket.tcpNoDelay = true
            socket.sendBufferSize = 1024 * 512
            socket.receiveBufferSize = 1024 * 128
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val firstLine = readBoundedHttpLine(reader, MAX_HTTP_LINE_CHARS)
                ?: return send400(socket, "Invalid or oversized HTTP request line")
            if (VERBOSE_STREAM_LOGS) Log.d(TAG, "HTTP Request: $firstLine")

            val parts = firstLine.split(' ')
            if (parts.size != 3 || parts[1].isBlank() ||
                !(parts[2] == "HTTP/1.1" || parts[2] == "HTTP/1.0")
            ) return send400(socket, "Malformed HTTP request line")
            val method = parts[0]
            if (method != "GET" && method != "HEAD") return send405(socket)
            val isHeadRequest = (method == "HEAD")

            var rangeHeader: String? = null
            var headerCharacters = 0
            var reachedHeaderEnd = false
            for (headerIndex in 0 until MAX_HTTP_HEADERS) {
                val line = readBoundedHttpLine(reader, MAX_HTTP_LINE_CHARS)
                    ?: return send400(socket, "Invalid or oversized HTTP header")
                headerCharacters += line.length + 2
                if (headerCharacters > MAX_HTTP_HEADER_CHARS) return send400(socket, "HTTP headers are too large")
                if (line.isEmpty()) {
                    reachedHeaderEnd = true
                    break
                }
                val separator = line.indexOf(':')
                if (separator <= 0) return send400(socket, "Malformed HTTP header")
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                if (name.equals("Range", ignoreCase = true)) {
                    if (rangeHeader != null) return send400(socket, "Multiple Range headers are not supported")
                    rangeHeader = value
                }
            }
            if (!reachedHeaderEnd) return send400(socket, "Too many HTTP headers")

            val uri = parts[1]
            when (uri.substringBefore('?')) {
                "/stream" -> handleStreamRequest(socket, uri, rangeHeader, isHead = isHeadRequest)
                "/subtitle" -> if (!isHeadRequest) handleSubtitleRequest(socket, uri) else handleHeadSubtitleRequest(socket, uri)
                "/playlist.m3u" -> if (!isHeadRequest) handlePlaylistRequest(socket, uri) else handleHeadPlaylistRequest(socket, uri)
                else -> send404(socket)
            }
        } catch (e: java.net.SocketTimeoutException) {
            runCatching { send400(socket, "HTTP request timed out") }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readBoundedHttpLine(reader: BufferedReader, maxCharacters: Int): String? {
        val line = StringBuilder(min(128, maxCharacters))
        while (true) {
            val codePoint = reader.read()
            if (codePoint < 0) return line.takeIf { it.isNotEmpty() }?.toString()
            if (codePoint == '\n'.code) return line.toString().removeSuffix("\r")
            if (line.length >= maxCharacters) return null
            line.append(codePoint.toChar())
        }
    }

    private fun handleStreamRequest(socket: Socket, uri: String, rangeHeader: String?, isHead: Boolean = false) {
        val query = uri.substringAfter("?", "")
        val params = parseQueryParams(query)
        val fileId = params["fileId"]?.toIntOrNull() ?: return send400(socket, "Missing fileId")
        if (!StreamAccessRegistry.authorizesFile(params["token"], fileId)) {
            return send403(socket, "Invalid or expired stream token")
        }
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

        if (VERBOSE_STREAM_LOGS) Log.i(TAG, "Streaming fileId=$fileId size=$size MIME=$contentType range=$rangeHeader head=$isHead")

        val parsedRange = HttpRangeParser.parse(rangeHeader, size)
        val range = (parsedRange as? HttpRangeParser.Result.Valid)?.range
            ?: return send416(socket, size)
        val rangeStart = range.start
        val rangeEnd = range.endInclusive
        val contentLength = rangeEnd - rangeStart + 1
        val isFullFileResponse = !range.isPartial

        val out = socket.getOutputStream()
        val headers = if (isFullFileResponse) {
            buildStreamOkHeaders(contentType, size)
        } else {
            buildPartialContentHeaders(contentType, rangeStart, rangeEnd, size, contentLength)
        }

        writeHeaders(out, headers)
        if (!isHead) {
            streamBytes(fileId, size, rangeStart, rangeEnd, out, socket)
        }
    }

    private fun writeHeaders(out: OutputStream, headers: String) {
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** Full-file (200 OK) response headers for a streamable body of [size] bytes. */
    private fun buildStreamOkHeaders(contentType: String, size: Long): String =
        "HTTP/1.1 200 OK\r\n" +
            "Content-Type: $contentType\r\n" +
            "Accept-Ranges: bytes\r\n" +
            "Content-Length: $size\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Cache-Control: private, no-store\r\n" +
            "Connection: close\r\n\r\n"

    /** Range-requested (206 Partial Content) response headers. */
    private fun buildPartialContentHeaders(
        contentType: String,
        rangeStart: Long,
        rangeEnd: Long,
        size: Long,
        contentLength: Long
    ): String =
        "HTTP/1.1 206 Partial Content\r\n" +
            "Content-Type: $contentType\r\n" +
            "Accept-Ranges: bytes\r\n" +
            "Content-Range: bytes $rangeStart-$rangeEnd/$size\r\n" +
            "Content-Length: $contentLength\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Cache-Control: private, no-store\r\n" +
            "Connection: close\r\n\r\n"

    /** Plain 200 OK headers for an in-memory body of [contentLength] bytes. */
    private fun buildBodyHeaders(contentType: String, contentLength: Int): String =
        "HTTP/1.1 200 OK\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: $contentLength\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Cache-Control: private, no-store\r\n" +
            "Connection: close\r\n\r\n"

    private fun handleHeadSubtitleRequest(socket: Socket, uri: String) {
        val query = uri.substringAfter("?", "")
        val params = parseQueryParams(query)
        val fileId = params["fileId"]?.toIntOrNull() ?: return send400(socket, "Missing fileId")
        if (!StreamAccessRegistry.authorizesFile(params["token"], fileId)) {
            return send403(socket, "Invalid or expired subtitle token")
        }
        val out = socket.getOutputStream()
        val localFile = getFileStatus(fileId)
        val length = localFile?.size ?: 0L
        writeHeaders(out, buildStreamOkHeaders("text/plain", length))
    }

    private fun handleHeadPlaylistRequest(socket: Socket, uri: String) {
        val params = parseQueryParams(uri.substringAfter("?", ""))
        if (!StreamAccessRegistry.authorizesPlaylist(params["token"])) {
            return send403(socket, "Invalid or expired playlist token")
        }
        val playlist = currentPlaylist ?: "#EXTM3U\n"
        val out = socket.getOutputStream()
        val bytes = playlist.toByteArray(Charsets.UTF_8)
        writeHeaders(out, buildBodyHeaders("audio/x-mpegurl", bytes.size))
    }

    private fun handleSubtitleRequest(socket: Socket, uri: String) {
        val query = uri.substringAfter("?", "")
        val params = parseQueryParams(query)
        val fileId = params["fileId"]?.toIntOrNull() ?: return send400(socket, "Missing fileId")
        if (!StreamAccessRegistry.authorizesFile(params["token"], fileId)) {
            return send403(socket, "Invalid or expired subtitle token")
        }

        if (VERBOSE_STREAM_LOGS) Log.i(TAG, "Serving subtitle fileId=$fileId")

        val out = socket.getOutputStream()
        val localFile = downloadFileFullyBlocking(fileId)
        if (localFile == null || localFile.local.path.isNullOrEmpty()) {
            send404(socket)
            return
        }

        val file = File(localFile.local.path)
        if (!file.exists()) {
            send404(socket)
            return
        }

        val fileBytes = file.readBytes()
        writeHeaders(out, buildBodyHeaders("text/plain", fileBytes.size))
        out.write(fileBytes)
        out.flush()
    }

    private fun handlePlaylistRequest(socket: Socket, uri: String) {
        val params = parseQueryParams(uri.substringAfter("?", ""))
        if (!StreamAccessRegistry.authorizesPlaylist(params["token"])) {
            return send403(socket, "Invalid or expired playlist token")
        }
        val playlist = currentPlaylist ?: "#EXTM3U\n"
        val out = socket.getOutputStream()
        val bytes = playlist.toByteArray(Charsets.UTF_8)
        val headers = buildBodyHeaders("audio/x-mpegurl", bytes.size)
        if (VERBOSE_STREAM_LOGS) Log.i(TAG, "Serving generated playlist containing ${bytes.size} bytes")
        writeHeaders(out, headers)
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
        val lease = TelegramChunkScheduler.acquireLease(
            fileId = fileId,
            offset = startPos,
            length = (endPos - startPos + 1L).coerceAtLeast(0L),
            consumer = TelegramStreamConsumer.LocalHttp,
            priority = TelegramChunkScheduler.PLAYBACK_PRIORITY
        )
        var releaseReason = TelegramLeaseReleaseReason.Completed
        try {
            while (currentOffset <= endPos && clientOpen()) {
                val readSize = min(
                    STREAM_READ_BUFFER_BYTES.toLong(),
                    min(endPos - currentOffset + 1L, totalSize - currentOffset)
                ).toInt()
                if (readSize <= 0) break
                val result = kotlinx.coroutines.runBlocking {
                    TelegramChunkScheduler.fetchChunk(
                        TelegramChunkRequest(
                            fileId = fileId,
                            offset = currentOffset,
                            limit = readSize,
                            priority = TelegramChunkScheduler.PLAYBACK_PRIORITY,
                            purpose = TelegramChunkPurpose.Streaming
                        )
                    )
                }
                when (result) {
                    is TelegramChunkResult.Ready -> {
                        if (result.bytes.isEmpty()) break
                        try {
                            PlaybackTelemetryRecorder.notifyFirstByte(fileId)
                            out.write(result.bytes)
                            currentOffset += result.bytes.size
                        } catch (e: Exception) {
                            releaseReason = TelegramLeaseReleaseReason.ConsumerClosed
                            Log.w(TAG, "Socket write error (client closed): ${e.message}")
                            return
                        }
                    }
                    is TelegramChunkResult.FatalFailure -> {
                        releaseReason = TelegramLeaseReleaseReason.Failed
                        PlaybackDiagnostics.recordStreamError(result.message)
                        return
                    }
                    is TelegramChunkResult.RetryableFailure,
                    TelegramChunkResult.Pending -> {
                        releaseReason = TelegramLeaseReleaseReason.TimedOut
                        val message = "Telegram stream stalled while caching fileId=$fileId at offset=$currentOffset"
                        EnterprisePreferences.setLastError(message)
                        PlaybackDiagnostics.recordStreamError(message)
                        return
                    }
                }
            }
        } finally {
            if (!clientOpen()) releaseReason = TelegramLeaseReleaseReason.ConsumerClosed
            TelegramChunkScheduler.releaseLease(lease, releaseReason, currentOffset)
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

    private fun streamDiskRangeToOutput(
        path: String,
        fileDownloadOffset: Long,
        startPos: Long,
        length: Long,
        out: OutputStream,
        clientOpen: () -> Boolean = { true },
        fileId: Int = 0
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
                if (fileId > 0) PlaybackTelemetryRecorder.notifyFirstByte(fileId)
                try {
                    out.write(buffer, 0, read)
                } catch (e: Exception) {
                    Log.w(TAG, "Socket write error during disk streaming (client closed)", e)
                    if (fileId > 0) TelegramChunkScheduler.onClientDisconnected(fileId, startPos + length - remaining)
                    return
                }
                remaining -= read
            }
            try { out.flush() } catch (_: Exception) {}
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
        val bitrateBps = if (durationMs > 0) {
            (totalSize * 1000L) / durationMs
        } else {
            2_000_000L // default ~2 MB/s for 1080p
        }
        val adaptiveRunway = max(8L * 1024L * 1024L, min(48L * 1024L * 1024L, bitrateBps * 25L))
        val chunkSize = min(adaptiveRunway, remaining)
        if (chunkSize > 0) {
            TelegramChunkScheduler.prefetchRange(
                fileId = fileId,
                offset = offset,
                length = chunkSize,
                purpose = TelegramChunkPurpose.CacheAhead,
                priority = TelegramChunkScheduler.PREFETCH_PRIORITY
            )
            if (VERBOSE_STREAM_LOGS) Log.d(TAG, "Adaptive prefetch fileId=$fileId offset=$offset limit=$chunkSize (bitrate=${bitrateBps * 8 / 1_000_000}Mbps, timeMs=$timeMs)")
        }
    }

    private fun getFileStatus(fileId: Int, waitMs: Long = 2_500L): TdApi.File? {
        fileStatusCache[fileId]
            ?.takeIf { System.currentTimeMillis() - it.updatedAtMs < 1_500L }
            ?.let { return it.file }

        var result: TdApi.File? = null
        val latch = CountDownLatch(1)
        TdLibClient.client.send(
            TdApi.GetFile(fileId)
        ) { res ->
            result = res as? TdApi.File
            result?.let {
                fileStatusCache[fileId] = CachedFileStatus(it, System.currentTimeMillis())
            }
            latch.countDown()
        }
        latch.await(waitMs, TimeUnit.MILLISECONDS)
        return result
    }

    private fun downloadFileFullyBlocking(fileId: Int): TdApi.File? {
        var file = getFileStatus(fileId)
        if (file != null && file.local.isDownloadingCompleted) {
            return file
        }

        val completed = kotlinx.coroutines.runBlocking {
            TelegramChunkScheduler.downloadEntireFile(fileId, timeoutMs = 30_000L)
        } ?: return file
        return getFileStatus(completed.fileId)
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
                    URLDecoder.decode(value, "UTF-8")
                }.getOrElse {
                    EnterprisePreferences.setLastError("Malformed stream query parameter: $key")
                    value
                }
            }
        }
        return result
    }

    private fun send405(socket: Socket) {
        runCatching {
            val out = socket.getOutputStream()
            val body = "Only GET is supported"
            val headers = "HTTP/1.1 405 Method Not Allowed\r\nAllow: GET\r\nContent-Type: text/plain\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
            out.write(headers.toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    private fun send403(socket: Socket, message: String) {
        runCatching {
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\nContent-Length: ${message.toByteArray().size}\r\nConnection: close\r\n\r\n$message".toByteArray())
            out.flush()
        }
    }

    private fun send416(socket: Socket, fileSize: Long) {
        runCatching {
            val out = socket.getOutputStream()
            val body = "Unsatisfiable byte range"
            val headers = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Type: text/plain\r\nAccept-Ranges: bytes\r\nContent-Range: bytes */$fileSize\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
            out.write(headers.toByteArray(Charsets.UTF_8))
            out.flush()
        }
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

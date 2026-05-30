package com.vaydelux.bousygram.core.tdlib.server

import android.util.Log
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.min

internal data class HttpByteRange(
    val start: Long,
    val end: Long
)

data class VideoResolutionOption(
    val quality: Int,
    val fileId: Int
)

object TelegramProxyManager {
    private const val TAG = "TelegramProxyManager"
    private const val HOST = "127.0.0.1"
    private const val PORT = 8080
    private const val LOCAL_STREAM_BASE_URL = "http://$HOST:$PORT/stream"
    private const val LOCAL_ADAPTIVE_STREAM_BASE_URL = "http://$HOST:$PORT/adaptive-stream"
    private const val PARALLEL_WORKER_COUNT = TelegramChunkScheduler.MAX_CONCURRENT_CHUNKS_PER_FILE
    // Keep chunks at exactly 1MB so parallel prefetch still feeds ExoPlayer ordered,
    // interleaved media bytes and avoids audio/video drift.
    private const val CHUNK_SIZE_BYTES = TelegramChunkScheduler.CHUNK_SIZE_BYTES
    private const val STREAM_PRIORITY = 32
    private const val PRE_FLIGHT_RETRY_AFTER_SECONDS = "1"
    private const val PRE_FLIGHT_CHUNK_ATTEMPTS = 1

    @Volatile
    private var server: NettyApplicationEngine? = null

    private data class DownloadedChunk(
        val offset: Long,
        val bytes: ByteArray
    )

    val isRunning: Boolean
        get() = server != null

    fun startProxy() {
        if (server != null) return
        try {
            server = embeddedServer(Netty, host = HOST, port = PORT) {
                routing {
                    get("/health") {
                        call.respond(HttpStatusCode.OK, "ok")
                    }
                    get("/stream") {
                        handleStream(call)
                    }
                    get("/adaptive-stream") {
                        handleAdaptiveStream(call)
                    }
                }
            }.start(wait = false)
            Log.i(TAG, "Ktor Telegram proxy started on http://$HOST:$PORT")
        } catch (e: Exception) {
            server = null
            val message = "Ktor proxy could not bind $HOST:$PORT; using fallback proxy. ${e.message.orEmpty()}"
            Log.w(TAG, message, e)
            EnterprisePreferences.setLastError(message)
            PlaybackDiagnostics.recordStreamError(message)
        }
    }

    fun stopProxy() {
        val running = server ?: return
        server = null
        try {
            running.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
            Log.i(TAG, "Ktor Telegram proxy stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Ktor Telegram proxy", e)
        }
    }

    fun getStreamUrl(
        fileId: Int,
        size: Long,
        title: String,
        mimeType: String? = null,
        fileName: String? = null
    ): String {
        val label = VideoFormatSupport.streamLabel(fileName, title)
        val encodedTitle = URLEncoder.encode(label, "UTF-8")
        val contentType = VideoFormatSupport.httpContentType(mimeType, fileName, title)
        val encodedMime = URLEncoder.encode(contentType, "UTF-8")
        return "${baseStreamUrl(fileId)}&size=$size&title=$encodedTitle&mime=$encodedMime"
    }

    fun baseStreamUrl(fileId: Int): String = "$LOCAL_STREAM_BASE_URL?fileId=$fileId"

    fun adaptiveStreamUrl(options: List<VideoResolutionOption>): String {
        val encodedOptions = options
            .filter { it.fileId > 0 }
            .joinToString(",") { "${it.quality}:${it.fileId}" }
        val query = URLEncoder.encode(encodedOptions, "UTF-8")
        return "$LOCAL_ADAPTIVE_STREAM_BASE_URL?options=$query"
    }

    private suspend fun handleStream(call: ApplicationCall) {
        val fileId = call.request.queryParameters["fileId"]?.toIntOrNull()
        if (fileId == null || fileId <= 0) {
            call.respond(HttpStatusCode.BadRequest, "Missing fileId")
            return
        }

        var size = call.request.queryParameters["size"]?.toLongOrNull() ?: 0L
        if (size <= 0L) size = LocalStreamingServer.resolveFileSize(fileId)
        if (size <= 0L) {
            call.respond(HttpStatusCode.ServiceUnavailable, "File size is not ready")
            return
        }

        LocalStreamingServer.warmUp(fileId, size)

        val title = call.request.queryParameters["title"].orEmpty()
        val contentTypeValue = call.request.queryParameters["mime"]
            ?.takeIf { it.isNotBlank() }
            ?: VideoFormatSupport.httpContentType(null, title, title)
        val contentType = runCatching { ContentType.parse(contentTypeValue) }
            .getOrElse { ContentType.Application.OctetStream }

        val rangeHeader = call.request.header(HttpHeaders.Range)
        val range = parseByteRange(rangeHeader, size)
        val contentLength = range.end - range.start + 1
        val isPartial = rangeHeader != null
        val preflightLimit = min(CHUNK_SIZE_BYTES.toLong(), contentLength).toInt()
        val preflight = fetchReadyChunkForStream(
            fileId = fileId,
            offset = range.start,
            limit = preflightLimit,
            purpose = TelegramChunkPurpose.Preflight
        )
        if (preflight == null) {
            respondRangeNotReady(call, fileId, range.start)
            return
        }

        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ContentLength, contentLength.toString())
        if (isPartial) {
            call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.end}/$size")
        }
        call.response.header(HttpHeaders.Connection, "close")

        val status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Ktor stream fileId=$fileId range=${range.start}-${range.end}/$size")
        }
        call.respondBytesWriter(contentType = contentType, status = status) {
            streamTelegramFileInParallel(fileId, range, this, firstChunk = preflight)
        }
    }

    private suspend fun handleAdaptiveStream(call: ApplicationCall) {
        val options = parseResolutionOptions(call.request.queryParameters["options"])
        if (options.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Missing adaptive stream options")
            return
        }

        val startingOption = options.first()
        val size = LocalStreamingServer.resolveFileSize(startingOption.fileId)
        if (size <= 0L) {
            call.respond(HttpStatusCode.ServiceUnavailable, "File size is not ready")
            return
        }

        val rangeHeader = call.request.header(HttpHeaders.Range)
        val range = parseByteRange(rangeHeader, size)
        val contentLength = range.end - range.start + 1
        val isPartial = rangeHeader != null
        val preflightLimit = min(CHUNK_SIZE_BYTES.toLong(), contentLength).toInt()
        val preflight = fetchReadyChunkForStream(
            fileId = startingOption.fileId,
            offset = range.start,
            limit = preflightLimit,
            purpose = TelegramChunkPurpose.Preflight
        )
        if (preflight == null) {
            respondRangeNotReady(call, startingOption.fileId, range.start)
            return
        }

        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ContentLength, contentLength.toString())
        if (isPartial) {
            call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.end}/$size")
        }
        call.response.header(HttpHeaders.Connection, "close")

        val status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK
        call.respondBytesWriter(contentType = ContentType.Application.OctetStream, status = status) {
            streamTelegramFileInParallel(startingOption.fileId, range, this, firstChunk = preflight)
        }
    }

    private suspend fun streamTelegramFileInParallel(
        fileId: Int,
        range: HttpByteRange,
        channel: ByteWriteChannel,
        firstChunk: ByteArray? = null
    ) = coroutineScope {
        var expectedOffset = range.start
        if (firstChunk != null) {
            channel.writeFully(firstChunk, 0, firstChunk.size)
            channel.flush()
            expectedOffset += firstChunk.size
            if (expectedOffset > range.end) return@coroutineScope
        }

        val nextOffset = AtomicLong(expectedOffset)
        val results = Channel<DownloadedChunk>(capacity = PARALLEL_WORKER_COUNT)
        val workers = List(PARALLEL_WORKER_COUNT) {
            launch(Dispatchers.IO) {
                while (isActive) {
                    val offset = nextOffset.getAndAdd(CHUNK_SIZE_BYTES.toLong())
                    if (offset > range.end) break
                    val limit = min(CHUNK_SIZE_BYTES.toLong(), range.end - offset + 1L).toInt()
                    if (limit <= 0) break
                    val chunk = fetchReadyChunkForStream(
                        fileId = fileId,
                        offset = offset,
                        limit = limit,
                        purpose = TelegramChunkPurpose.Streaming
                    ) ?: error("Telegram chunk unavailable fileId=$fileId offset=$offset limit=$limit")
                    results.send(DownloadedChunk(offset, chunk))
                }
            }
        }
        val closer = launch {
            workers.joinAll()
            results.close()
        }

        val pending = TreeMap<Long, ByteArray>()
        try {
            for (chunk in results) {
                coroutineContext.ensureActive()
                pending[chunk.offset] = chunk.bytes
                while (true) {
                    val next = pending.remove(expectedOffset) ?: break
                    channel.writeFully(next, 0, next.size)
                    channel.flush()
                    expectedOffset += next.size
                    if (expectedOffset > range.end) {
                        workers.forEach { it.cancel() }
                        return@coroutineScope
                    }
                }
            }
            if (expectedOffset <= range.end) {
                error("Telegram stream ended early fileId=$fileId expectedOffset=$expectedOffset end=${range.end}")
            }
        } finally {
            closer.cancel()
            workers.forEach { it.cancel() }
            results.close()
        }
    }

    private suspend fun fetchReadyChunkForStream(
        fileId: Int,
        offset: Long,
        limit: Int,
        purpose: TelegramChunkPurpose
    ): ByteArray? {
        var lastMessage = ""
        var attempt = 0
        while (coroutineContext.isActive) {
            val result = TelegramChunkScheduler.fetchChunk(
                TelegramChunkRequest(
                    fileId = fileId,
                    offset = offset,
                    limit = limit,
                    priority = STREAM_PRIORITY,
                    purpose = purpose
                )
            )
            when (result) {
                is TelegramChunkResult.Ready -> {
                    PlaybackDiagnostics.recordProxyState(
                        lastStalledOffset = offset,
                        chunkRetryCount = result.retryCount + attempt,
                        cacheAheadBytes = 0L,
                        activeChunkCount = TelegramChunkScheduler.activeChunkCount(fileId),
                        proxyStatus = purpose.name,
                        readSource = result.source.name
                    )
                    return result.bytes
                }
                is TelegramChunkResult.FatalFailure -> {
                    PlaybackDiagnostics.recordStreamError(result.message)
                    return null
                }
                is TelegramChunkResult.RetryableFailure -> lastMessage = result.message
                TelegramChunkResult.Pending -> lastMessage = "Telegram range pending fileId=$fileId offset=$offset"
            }
            TelegramChunkScheduler.markState(fileId, PlaybackCacheState.Reconnecting)
            if (purpose == TelegramChunkPurpose.Preflight && attempt + 1 >= PRE_FLIGHT_CHUNK_ATTEMPTS) break
            attempt++
            delay((350L * attempt).coerceAtMost(2_000L))
        }
        val message = lastMessage.ifBlank { "Telegram is still caching range fileId=$fileId offset=$offset" }
        Log.w(TAG, message)
        PlaybackDiagnostics.recordStreamError(message)
        return null
    }

    private suspend fun respondRangeNotReady(call: ApplicationCall, fileId: Int, offset: Long) {
        val message = "Telegram is still caching range fileId=$fileId offset=$offset"
        TelegramChunkScheduler.markState(fileId, PlaybackCacheState.Reconnecting)
        PlaybackDiagnostics.recordStreamError(message)
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.RetryAfter, PRE_FLIGHT_RETRY_AFTER_SECONDS)
        call.response.header(HttpHeaders.Connection, "close")
        call.respond(HttpStatusCode.ServiceUnavailable, message)
    }

    private fun parseByteRange(rangeHeader: String?, fileSize: Long): HttpByteRange {
        if (fileSize <= 0L) return HttpByteRange(0L, 0L)
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return HttpByteRange(0L, fileSize - 1L)
        }

        val spec = rangeHeader.substringAfter("bytes=").trim()
        val dashIndex = spec.indexOf('-')
        if (dashIndex < 0) return HttpByteRange(0L, fileSize - 1L)

        val startText = spec.substring(0, dashIndex).trim()
        val endText = spec.substring(dashIndex + 1).trim()
        var start = 0L
        var end = fileSize - 1L

        when {
            startText.isEmpty() && endText.isNotEmpty() -> {
                val suffixLength = endText.toLongOrNull() ?: 0L
                start = (fileSize - suffixLength).coerceAtLeast(0L)
            }
            startText.isNotEmpty() && endText.isEmpty() -> {
                start = startText.toLongOrNull()?.coerceIn(0L, fileSize - 1L) ?: 0L
            }
            startText.isNotEmpty() && endText.isNotEmpty() -> {
                start = startText.toLongOrNull()?.coerceIn(0L, fileSize - 1L) ?: 0L
                end = endText.toLongOrNull()?.coerceIn(start, fileSize - 1L) ?: start
            }
        }

        if (end < start) end = start
        return HttpByteRange(start, min(end, fileSize - 1L))
    }

    private fun parseResolutionOptions(rawOptions: String?): List<VideoResolutionOption> {
        if (rawOptions.isNullOrBlank()) return emptyList()
        return rawOptions
            .split(",")
            .mapNotNull { entry ->
                val quality = entry.substringBefore(":", "").toIntOrNull() ?: return@mapNotNull null
                val fileId = entry.substringAfter(":", "").toIntOrNull() ?: return@mapNotNull null
                if (quality <= 0 || fileId <= 0) null else VideoResolutionOption(quality, fileId)
            }
            .distinctBy { it.fileId }
            .sortedByDescending { it.quality }
    }
}

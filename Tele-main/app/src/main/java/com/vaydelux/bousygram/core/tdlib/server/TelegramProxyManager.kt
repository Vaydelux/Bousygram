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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext
import kotlin.math.min

internal data class HttpByteRange(
    val start: Long,
    val end: Long
)

data class VideoResolutionOption(
    val quality: Int,
    val fileId: Int,
    val token: String? = null
)

object TelegramProxyManager {
    private const val TAG = "TelegramProxyManager"
    private const val HOST = "127.0.0.1"
    private const val PORT = 8080
    private const val LOCAL_STREAM_BASE_URL = "http://$HOST:$PORT/stream"
    private const val LOCAL_ADAPTIVE_STREAM_BASE_URL = "http://$HOST:$PORT/adaptive-stream"
    // Live playback stays sequential because TDLib has one active byte-window per file.
    private const val CHUNK_SIZE_BYTES = TelegramChunkScheduler.CHUNK_SIZE_BYTES
    private const val FIRST_CHUNK_BYTES = 64 * 1024
    private const val STREAM_PRIORITY = 32
    private const val PRE_FLIGHT_RETRY_AFTER_SECONDS = "1"
    private const val PRE_FLIGHT_WAIT_MS = 90_000L
    private const val STREAM_CHUNK_WAIT_MS = 120_000L

    @Volatile
    private var server: NettyApplicationEngine? = null

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

    fun baseStreamUrl(fileId: Int): String {
        val token = StreamAccessRegistry.issueFile(fileId)
        return "$LOCAL_STREAM_BASE_URL?fileId=$fileId&token=$token"
    }

    fun adaptiveStreamUrl(options: List<VideoResolutionOption>): String {
        val encodedOptions = options
            .filter { it.fileId > 0 && it.quality > 0 }
            .joinToString(",") { option ->
                val token = option.token ?: StreamAccessRegistry.issueFile(option.fileId)
                "${option.quality}:${option.fileId}:$token"
            }
        val query = URLEncoder.encode(encodedOptions, "UTF-8")
        return "$LOCAL_ADAPTIVE_STREAM_BASE_URL?options=$query"
    }

    private suspend fun handleStream(call: ApplicationCall) {
        val fileId = call.request.queryParameters["fileId"]?.toIntOrNull()
        if (fileId == null || fileId <= 0) {
            call.respond(HttpStatusCode.BadRequest, "Missing fileId")
            return
        }
        if (!StreamAccessRegistry.authorizesFile(call.request.queryParameters["token"], fileId)) {
            call.respond(HttpStatusCode.Forbidden, "Invalid or expired stream token")
            return
        }

        var size = call.request.queryParameters["size"]?.toLongOrNull() ?: 0L
        if (size <= 0L) size = LocalStreamingServer.resolveFileSize(fileId)
        if (size <= 0L) {
            call.respond(HttpStatusCode.ServiceUnavailable, "File size is not ready")
            return
        }

        val title = call.request.queryParameters["title"].orEmpty()
        val contentTypeValue = call.request.queryParameters["mime"]
            ?.takeIf { it.isNotBlank() }
            ?: VideoFormatSupport.httpContentType(null, title, title)
        val contentType = runCatching { ContentType.parse(contentTypeValue) }
            .getOrElse { ContentType.Application.OctetStream }

        val rangeHeader = call.request.header(HttpHeaders.Range)
        val range = when (val parsed = HttpRangeParser.parse(rangeHeader, size)) {
            is HttpRangeParser.Result.Valid -> parsed.range
            HttpRangeParser.Result.Unsatisfiable -> {
                call.response.header(HttpHeaders.ContentRange, "bytes */$size")
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Unsatisfiable byte range")
                return
            }
        }
        if (range.start == 0L) {
            LocalStreamingServer.warmUp(fileId, size)
        }
        val contentLength = range.endInclusive - range.start + 1
        val isPartial = range.isPartial
        val preflightLimit = min(FIRST_CHUNK_BYTES.toLong(), contentLength).toInt()
        val preflight = fetchReadyChunkForStream(
            fileId = fileId,
            offset = range.start,
            limit = preflightLimit,
            purpose = TelegramChunkPurpose.Preflight,
            maxWaitMs = PRE_FLIGHT_WAIT_MS
        )
        if (preflight == null) {
            respondRangeNotReady(call, fileId, range.start)
            return
        }

        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ContentLength, contentLength.toString())
        if (isPartial) {
            call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.endInclusive}/$size")
        }
        call.response.header(HttpHeaders.Connection, "close")

        val status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Ktor stream fileId=$fileId range=${range.start}-${range.endInclusive}/$size")
        }
        call.respondBytesWriter(contentType = contentType, status = status) {
            streamTelegramFileSequentially(
                fileId,
                HttpByteRange(range.start, range.endInclusive),
                this,
                firstChunk = preflight
            )
        }
    }

    private suspend fun handleAdaptiveStream(call: ApplicationCall) {
        val options = parseResolutionOptions(call.request.queryParameters["options"])
        if (options.isEmpty()) {
            call.respond(HttpStatusCode.Forbidden, "Missing or invalid adaptive stream authorization")
            return
        }

        val startingOption = options.first()
        val size = LocalStreamingServer.resolveFileSize(startingOption.fileId)
        if (size <= 0L) {
            call.respond(HttpStatusCode.ServiceUnavailable, "File size is not ready")
            return
        }

        val rangeHeader = call.request.header(HttpHeaders.Range)
        val range = when (val parsed = HttpRangeParser.parse(rangeHeader, size)) {
            is HttpRangeParser.Result.Valid -> parsed.range
            HttpRangeParser.Result.Unsatisfiable -> {
                call.response.header(HttpHeaders.ContentRange, "bytes */$size")
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Unsatisfiable byte range")
                return
            }
        }
        val contentLength = range.endInclusive - range.start + 1
        val isPartial = range.isPartial
        val preflightLimit = min(FIRST_CHUNK_BYTES.toLong(), contentLength).toInt()
        val preflight = fetchReadyChunkForStream(
            fileId = startingOption.fileId,
            offset = range.start,
            limit = preflightLimit,
            purpose = TelegramChunkPurpose.Preflight,
            maxWaitMs = PRE_FLIGHT_WAIT_MS
        )
        if (preflight == null) {
            respondRangeNotReady(call, startingOption.fileId, range.start)
            return
        }

        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ContentLength, contentLength.toString())
        if (isPartial) {
            call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.endInclusive}/$size")
        }
        call.response.header(HttpHeaders.Connection, "close")

        val status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK
        call.respondBytesWriter(contentType = ContentType.Application.OctetStream, status = status) {
            streamTelegramFileSequentially(
                startingOption.fileId,
                HttpByteRange(range.start, range.endInclusive),
                this,
                firstChunk = preflight
            )
        }
    }

    private suspend fun streamTelegramFileSequentially(
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

        while (expectedOffset <= range.end) {
            coroutineContext.ensureActive()
            val limit = min(CHUNK_SIZE_BYTES.toLong(), range.end - expectedOffset + 1L).toInt()
            if (limit <= 0) break
            val chunk = fetchReadyChunkForStream(
                fileId = fileId,
                offset = expectedOffset,
                limit = limit,
                purpose = TelegramChunkPurpose.Streaming,
                maxWaitMs = STREAM_CHUNK_WAIT_MS
            ) ?: error("Telegram chunk unavailable fileId=$fileId offset=$expectedOffset limit=$limit")
            channel.writeFully(chunk, 0, chunk.size)
            channel.flush()
            expectedOffset += chunk.size
        }
    }

    private suspend fun fetchReadyChunkForStream(
        fileId: Int,
        offset: Long,
        limit: Int,
        purpose: TelegramChunkPurpose,
        maxWaitMs: Long
    ): ByteArray? {
        var lastMessage = ""
        var attempt = 0
        val deadlineMs = System.currentTimeMillis() + maxWaitMs
        while (coroutineContext.isActive && System.currentTimeMillis() < deadlineMs) {
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

    private fun parseResolutionOptions(rawOptions: String?): List<VideoResolutionOption> {
        if (rawOptions.isNullOrBlank()) return emptyList()
        return rawOptions.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) return@mapNotNull null
            val quality = parts[0].toIntOrNull() ?: return@mapNotNull null
            val fileId = parts[1].toIntOrNull() ?: return@mapNotNull null
            val token = parts[2]
            if (quality <= 0 || fileId <= 0 || !StreamAccessRegistry.authorizesFile(token, fileId)) {
                null
            } else {
                VideoResolutionOption(quality, fileId, token)
            }
        }.distinctBy { it.fileId }.sortedByDescending { it.quality }
    }
}

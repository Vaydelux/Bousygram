package com.vaydelux.mediaforge.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.Request

/**
 * Bounded subtitle import over MediaForge's existing guarded transport.
 * No WebView cookies, credential headers, local URLs or unbounded body reads.
 * The resulting bytes are NOT a receiver URL and are not published here.
 */
object CastSubtitleImporter {
    private val client by lazy {
        MediaForgePlaybackTransport.client.newBuilder()
            .followRedirects(false)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fromInspectedSrt(media: InspectedMedia, caption: CaptionCandidate): ByteArray? {
        if (caption.format != CaptionFormat.SRT || caption !in media.captions ||
            media.responseCode !in 200..299 || MediaForgeUrlPolicy.safeHttpsUrl(caption.url) == null) return null
        return try {
            val request = Request.Builder().url(caption.url)
                .header("Accept", "application/x-subrip, text/plain, application/octet-stream")
                .get().build()
            readCancellableProbeResponse(client.newCall(request)) { response ->
                if (!response.isSuccessful || response.header("Content-Type").orEmpty()
                        .contains("text/html", ignoreCase = true)) return@readCancellableProbeResponse null
                val body = response.body ?: return@readCancellableProbeResponse null
                if (body.contentLength() > SrtWebVtt.MAX_INPUT_BYTES) return@readCancellableProbeResponse null
                body.byteStream().use(::fromSrtStream)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
    }

    /** Caller closes the input; import from a user-picked document uses the same bound. */
    fun fromSrtStream(input: InputStream): ByteArray? {
        val maximum = SrtWebVtt.MAX_INPUT_BYTES
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maximum + 1 - output.size()))
            if (count == -1) break
            if (count == 0 || count > maximum - output.size()) return null
            output.write(buffer, 0, count)
        }
        return SrtWebVtt.convert(output.toByteArray())
    }
}

package com.vaydelux.mediaforge.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class InspectedMedia(
    val stream: StreamCandidate,
    val captions: List<CaptionCandidate>,
    val responseCode: Int
)

sealed interface InspectionResult {
    data class Playable(val media: InspectedMedia) : InspectionResult
    data class Unavailable(val reason: String) : InspectionResult
}

/**
 * Bounded probe without cookies or third-party session headers.
 *
 * This is a candidate validator, not proof that a video can be fully decoded or
 * that each manifest segment can be downloaded. Redirects remain HTTPS-only.
 */
class MediaForgeResolver {
    suspend fun inspect(
        stream: StreamCandidate,
        observedCaptions: List<CaptionCandidate>
    ): InspectionResult = withContext(Dispatchers.IO) {
        try {
            var currentUrl = stream.url
            for (hop in 0..4) {
                coroutineContext.ensureActive()
                val uri = MediaForgeUrlPolicy.safeHttpsUrl(currentUrl)
                    ?: return@withContext InspectionResult.Unavailable("Unsafe or non-HTTPS source")
                rejectNonPublicAddresses(uri.host)
                val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("Range", "bytes=0-65535")
                    setRequestProperty("Accept", "application/vnd.apple.mpegurl, application/dash+xml, video/*, */*")
                }
                var redirect: String? = null
                try {
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val location = connection.getHeaderField("Location")
                            ?: return@withContext InspectionResult.Unavailable("Missing redirect location")
                        redirect = uri.resolve(location).toString()
                    } else {
                        if (code !in 200..299)
                            return@withContext InspectionResult.Unavailable("Media returned HTTP $code")
                        val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
                        if ("text/html" in contentType)
                            return@withContext InspectionResult.Unavailable("Media URL returned HTML")

                        val body = connection.inputStream.use { readBounded(it, 65_536) }
                        if (body.isEmpty())
                            return@withContext InspectionResult.Unavailable("Empty response")
                        val text = body.toString(Charsets.UTF_8)
                        if (stream.format == StreamFormat.HLS && !text.trimStart().startsWith("#EXTM3U"))
                            return@withContext InspectionResult.Unavailable("Invalid HLS manifest")
                        if (stream.format == StreamFormat.DASH && !text.contains("<MPD"))
                            return@withContext InspectionResult.Unavailable("Invalid DASH manifest")
                        if (stream.format !in setOf(StreamFormat.HLS, StreamFormat.DASH) &&
                            (text.trimStart().startsWith("<!DOCTYPE", true) || text.trimStart().startsWith("<html", true))
                        ) return@withContext InspectionResult.Unavailable("Unexpected HTML response")

                        val captions = observedCaptions.filter {
                            MediaForgeUrlPolicy.safeHttpsUrl(it.url) != null
                        }.take(30).toMutableList()
                        if (stream.format == StreamFormat.HLS) {
                            captions += MediaForgeHlsCaptions.parse(text, currentUrl)
                        }
                        return@withContext InspectionResult.Playable(
                            InspectedMedia(
                                stream.copy(url = currentUrl, host = uri.host),
                                captions.distinctBy { it.url }.take(30),
                                code
                            )
                        )
                    }
                } finally {
                    connection.disconnect()
                }
                currentUrl = redirect
                    ?: return@withContext InspectionResult.Unavailable("Unusable redirect")
            }
            InspectionResult.Unavailable("Too many redirects")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            InspectionResult.Unavailable("Unable to verify source; continue in WebView")
        }
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (bytes.size() < maximum) {
            val count = input.read(buffer, 0, minOf(buffer.size, maximum - bytes.size()))
            if (count == -1) break
            bytes.write(buffer, 0, count)
        }
        return bytes.toByteArray()
    }

    private fun rejectNonPublicAddresses(host: String) {
        // A second DNS resolution by HttpURLConnection creates residual rebinding
        // risk; production allowlists/proxy transport should further constrain hosts.
        val addresses = InetAddress.getAllByName(host)
        if (addresses.isEmpty() || addresses.any { address ->
            if (address.isAnyLocalAddress || address.isLoopbackAddress ||
                address.isLinkLocalAddress || address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) return@any true
            if (address is Inet4Address) {
                val octets = address.address.map { it.toInt() and 255 }
                val a = octets[0]; val b = octets[1]; val c = octets[2]
                a == 0 || a >= 224 || (a == 100 && b in 64..127) ||
                    (a == 192 && b == 0 && c == 0) ||
                    (a == 192 && b == 0 && c == 2) ||
                    (a == 198 && b in 18..19) ||
                    (a == 198 && b == 51 && c == 100) ||
                    (a == 203 && b == 0 && c == 113)
            } else false
        }) throw IOException("Private/reserved network destination rejected")
    }
}

object MediaForgeHlsCaptions {
    /**
     * Only explicit external .srt/.vtt/.ttml captions are returned. Subtitle HLS
     * rendition playlists are handled by Media3's built-in HLS track parser.
     */
    fun parse(playlist: String, base: String): List<CaptionCandidate> =
        playlist.lineSequence().take(1500).mapNotNull { line ->
            if (!line.startsWith("#EXT-X-MEDIA:") || !line.contains("TYPE=SUBTITLES"))
                return@mapNotNull null
            val marker = "URI=\""
            val start = line.indexOf(marker)
            if (start < 0) return@mapNotNull null
            val from = start + marker.length
            val end = line.indexOf('"', from)
            if (end <= from || end - from > 2048) return@mapNotNull null
            val resolved = runCatching {
                URI(base).resolve(line.substring(from, end)).toString()
            }.getOrNull() ?: return@mapNotNull null
            MediaForgeClassifier.caption(resolved)
        }.distinctBy { it.url }.take(30).toList()
}

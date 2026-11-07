package com.vaydelux.mediaforge.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.Locale
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

/** Controlled probes only; never forwards WebView cookies or Telegram credentials. */
class MediaForgeResolver {
    suspend fun inspect(stream: StreamCandidate, observedCaptions: List<CaptionCandidate>): InspectionResult =
        withContext(Dispatchers.IO) {
            try {
                if (MediaForgeUrlPolicy.safeHttpsUrl(stream.url) == null)
                    return@withContext InspectionResult.Unavailable("Unsafe or non-HTTPS media URL")
                val captions = observedCaptions.take(30).filter {
                    MediaForgeUrlPolicy.safeHttpsUrl(it.url) != null
                }.toMutableList()
                var url = stream.url
                var status = 0
                var content: String? = null
                var validated = false
                repeat(5) {
                    coroutineContext.ensureActive()
                    val uri = MediaForgeUrlPolicy.safeHttpsUrl(url)
                        ?: return@withContext InspectionResult.Unavailable("Unsafe redirect destination")
                    checkPublicAddress(uri.host)
                    val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        instanceFollowRedirects = false
                        requestMethod = "GET"
                        setRequestProperty("Range", "bytes=0-65535")
                        setRequestProperty("Accept", "application/vnd.apple.mpegurl, application/dash+xml, video/*, */*")
                    }
                    try {
                        status = connection.responseCode
                        if (status in 300..399) {
                            val location = connection.getHeaderField("Location")
                                ?: return@withContext InspectionResult.Unavailable("Redirect without location")
                            url = uri.resolve(location).toString()
                            return@repeat
                        }
                        if (status !in 200..299)
                            return@withContext InspectionResult.Unavailable("Source unavailable (HTTP " + status + ")")
                        val header = connection.contentType.orEmpty().lowercase(Locale.ROOT)
                        if (header.contains("text/html"))
                            return@withContext InspectionResult.Unavailable("Source returned an HTML page")
                        if (stream.format == StreamFormat.HLS || stream.format == StreamFormat.DASH) {
                            val bytes = connection.inputStream.use { it.readNBytes(65_536) }
                            content = bytes.toString(Charsets.UTF_8)
                            val body = content.orEmpty().trimStart()
                            if (stream.format == StreamFormat.HLS && !body.startsWith("#EXTM3U"))
                                return@withContext InspectionResult.Unavailable("Invalid HLS manifest")
                            if (stream.format == StreamFormat.DASH && !body.contains("<MPD"))
                                return@withContext InspectionResult.Unavailable("Invalid DASH manifest")
                        } else {
                            // A 2xx alone cannot prove all segments or codecs will be playable.
                            // Reject obvious HTML masquerading as a media URL.
                            val first = connection.inputStream.use { it.readNBytes(32) }
                            if (first.toString(Charsets.UTF_8).trimStart().startsWith("<!DOCTYPE", true))
                                return@withContext InspectionResult.Unavailable("Unexpected HTML response")
                        }
                        validated = true
                        return@repeat
                    } finally {
                        connection.disconnect()
                    }
                }
                if (!validated)
                    return@withContext InspectionResult.Unavailable("Too many redirects")
                if (stream.format == StreamFormat.HLS) {
                    captions += MediaForgeHlsCaptions.parse(content.orEmpty(), url)
                }
                InspectionResult.Playable(
                    InspectedMedia(stream.copy(url = url), captions.distinctBy { it.url }.take(30), status)
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                InspectionResult.Unavailable("Unable to verify stream; continue in WebView")
            }
        }

    private fun checkPublicAddress(host: String) {
        // Reduces SSRF/rebinding exposure for untrusted redirects. This does not
        // replace an OS/network firewall or a trusted provider allowlist.
        val addresses = InetAddress.getAllByName(host)
        if (addresses.isEmpty() || addresses.any {
            it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                it.isSiteLocalAddress || it.isMulticastAddress
        }) throw IOException("Private network address rejected")
    }
}

object MediaForgeHlsCaptions {
    /** Parse bounded EXT-X-MEDIA subtitle URIs. Media3 handles embedded renditions itself. */
    fun parse(playlist: String, base: String): List<CaptionCandidate> =
        playlist.lineSequence().take(1500)
            .filter { it.startsWith("#EXT-X-MEDIA:") && it.contains("TYPE=SUBTITLES") }
            .mapNotNull { line ->
                val uri = Regex("""(?:^|,)URI="([^"]{1,2048})" """.trim())
                    .find(line)?.groupValues?.get(1)
                    ?: Regex("""(?:^|,)URI="([^"]{1,2048})" """.trim())
                        .find(line.replace(",URI=", ",URI="))?.groupValues?.get(1)
                uri?.let { entry ->
                    val resolved = runCatching { URI(base).resolve(entry).toString() }.getOrNull()
                    resolved?.let { MediaForgeClassifier.caption(it) }
                }
            }.distinctBy { it.url }.take(30).toList()
}

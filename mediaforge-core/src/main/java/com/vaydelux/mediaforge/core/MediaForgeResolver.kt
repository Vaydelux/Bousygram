package com.vaydelux.mediaforge.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

                // OkHttp's validated DNS result is the result the socket itself
                // uses, preventing a second, unchecked DNS resolution.
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("Range", "bytes=0-65535")
                    .header(
                        "Accept",
                        "application/vnd.apple.mpegurl, application/dash+xml, video/*, */*"
                    )
                    .get()
                    .build()
                val call = PublicMediaTransport.client.newCall(request)
                val response = suspendCancellableCoroutine<Response> { continuation ->
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, error: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            if (continuation.isActive) continuation.resume(response)
                            else response.close()
                        }
                    })
                }
                response.use { response ->
                        val status = response.code
                        if (status in 300..399) {
                            val location = response.header("Location")
                                ?: return@withContext InspectionResult.Unavailable("Missing redirect location")
                            currentUrl = uri.resolve(location).toString()
                        } else {
                            if (status !in 200..299) {
                                return@withContext InspectionResult.Unavailable(
                                    "Media returned HTTP $status"
                                )
                            }
                            val contentType = response.header("Content-Type")
                                .orEmpty().lowercase(Locale.ROOT)
                            if ("text/html" in contentType) {
                                return@withContext InspectionResult.Unavailable("Media URL returned HTML")
                            }
                            val body = response.body?.byteStream()?.use { readBounded(it, 65_536) }
                                ?: return@withContext InspectionResult.Unavailable("Empty response")
                            if (body.isEmpty()) {
                                return@withContext InspectionResult.Unavailable("Empty response")
                            }
                            val text = body.toString(Charsets.UTF_8).trimStart('\uFEFF')
                            if (stream.format == StreamFormat.HLS &&
                                !text.trimStart().startsWith("#EXTM3U")
                            ) return@withContext InspectionResult.Unavailable("Invalid HLS manifest")
                            if (stream.format == StreamFormat.DASH && !text.contains("<MPD")) {
                                return@withContext InspectionResult.Unavailable("Invalid DASH manifest")
                            }
                            if (stream.format !in setOf(StreamFormat.HLS, StreamFormat.DASH) &&
                                !MediaForgeSignature.isPlausible(stream.format, body)
                            ) {
                                return@withContext InspectionResult.Unavailable(
                                    "Media response did not match the expected container format"
                                )
                            }

                            val captions = observedCaptions
                                .filter { MediaForgeUrlPolicy.safeHttpsUrl(it.url) != null }
                                .take(30).toMutableList()
                            if (stream.format == StreamFormat.HLS) {
                                captions += MediaForgeHlsCaptions.parse(text, currentUrl)
                            }
                            return@withContext InspectionResult.Playable(
                                InspectedMedia(
                                    stream.copy(url = currentUrl, host = uri.host),
                                    captions.distinctBy { it.url }.take(30),
                                    status
                                )
                            )
                        }
                    }
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
}

/**
 * A successful HTTP 2xx is not sufficient proof of a direct media stream.
 * Check container signatures without downloading the entire resource.
 * This is a sanity check, not a guarantee that every frame can be decoded.
 */
internal object MediaForgeSignature {
    fun isPlausible(format: StreamFormat, bytes: ByteArray): Boolean {
        when (format) {
            StreamFormat.MP4 -> {
                if (bytes.size < 8) return false
                val marker = bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII)
                return marker in setOf("ftyp", "styp", "moov", "mdat", "free", "wide")
            }
            StreamFormat.WEBM -> {
                return bytes.size >= 4 && (bytes[0].toInt() and 0xff) == 0x1a &&
                    (bytes[1].toInt() and 0xff) == 0x45 &&
                    (bytes[2].toInt() and 0xff) == 0xdf &&
                    (bytes[3].toInt() and 0xff) == 0xa3
            }
            StreamFormat.MPEG_TS -> return bytes.isNotEmpty() &&
                (bytes[0].toInt() and 0xff) == 0x47
            StreamFormat.HLS, StreamFormat.DASH -> return true
        }
    }
}

/**
 * Trust boundary for DNS used by stream probes. All resolved addresses must
 * be public. OkHttp connects to these checked InetAddress values directly,
 * retaining normal TLS hostname verification for the original DNS name.
 */
internal object PublicNetworkPolicy {
    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return false
        if (address is Inet4Address) {
            val octets = address.address.map { it.toInt() and 255 }
            val a = octets[0]
            val b = octets[1]
            val c = octets[2]
            return !(a == 0 || a >= 224 || (a == 100 && b in 64..127) ||
                (a == 192 && b == 0 && c == 0) || (a == 192 && b == 0 && c == 2) ||
                (a == 198 && b in 18..19) || (a == 198 && b == 51 && c == 100) ||
                (a == 203 && b == 0 && c == 113))
        }
        if (address is Inet6Address) {
            val bytes = address.address
            val first = bytes[0].toInt() and 255
            val second = bytes[1].toInt() and 255
            val third = bytes[2].toInt() and 255
            val fourth = bytes[3].toInt() and 255

            // Allow normal global IPv6 unicast (2000::/3) only. This also
            // rules out IPv4-embedded translation prefixes (64:ff9b::/96),
            // ULA, link-local and other special-use destinations.
            if (first and 0xe0 != 0x20) return false
            // 6to4 (2002::/16) and Teredo (2001:0000::/32) carry an IPv4
            // address inside IPv6; don't allow a private IPv4 bypass via
            // a tunnel endpoint. 2001:db8::/32 is documentation-only.
            if (first == 0x20 && second == 0x02) return false
            if (first == 0x20 && second == 0x01 &&
                ((third == 0x00 && fourth == 0x00) ||
                 (third == 0x0d && fourth == 0xb8))
            ) return false
        }
        return true
    }
}

/**
 * Every native media request, including redirected HLS/DASH segment and
 * subtitle traffic, must be HTTPS and must not carry browser/Telegram secrets.
 * Signed URLs are permitted but must stay in-memory; never log request URLs.
 */
internal object MediaForgeOutboundRequestPolicy {
    private val forbiddenHeaders = setOf(
        "cookie", "cookie2", "authorization", "proxy-authorization",
        "x-api-key", "x-telegram-token"
    )

    fun allows(request: Request): Boolean =
        MediaForgeUrlPolicy.safeHttpsUrl(request.url.toString()) != null &&
            request.headers.names().none { it.lowercase(Locale.ROOT) in forbiddenHeaders }
}

private object PublicMediaTransport {
    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val results = Dns.SYSTEM.lookup(hostname)
                if (results.isEmpty() || results.any { !PublicNetworkPolicy.isPublicAddress(it) }) {
                    throw UnknownHostException("Private or reserved network destination blocked")
                }
                return results
            }
        })
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            // Network interceptors are run for each actual HTTP request,
            // including each hop of a redirected media/CDN request.
            if (!MediaForgeOutboundRequestPolicy.allows(chain.request())) {
                throw IOException("Unsafe or credential-bearing media request blocked")
            }
            chain.proceed(chain.request())
        }
        .build()
}

/**
 * A guarded HTTP client for **all** Media3 Extras resources: the first media
 * URL, HLS/DASH manifests, segments, encryption key URLs and subtitles.
 *
 * Each HTTPS destination is checked immediately before network transmission.
 * The underlying socket uses PublicMediaTransport's guarded DNS resolver,
 * which rejects private and reserved addresses on every new lookup.
 *
 * Never transfer WebView cookies, Authorization headers or Telegram tokens.
 * Do not use this client as a generic cross-origin credentialed HTTP client.
 *
 * LibVLC has its own native network stack and is NOT guarded by this client.
 */
object MediaForgePlaybackTransport {
    val client: OkHttpClient = PublicMediaTransport.client.newBuilder()
        // Avoid a device-supplied proxy that could skip DNS address checks.
        .proxy(Proxy.NO_PROXY)
        .followRedirects(true)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        // Inherits the common native network interceptor above; do not
        // maintain a weaker independent policy for player segment requests.
        .build()
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
            val subtitleUri = quotedAttribute(line, "URI", 2048) ?: return@mapNotNull null
            val resolved = runCatching {
                URI(base).resolve(subtitleUri).toString()
            }.getOrNull() ?: return@mapNotNull null
            val language = quotedAttribute(line, "LANGUAGE", 35)
                ?.takeIf { it.matches(Regex("[A-Za-z0-9-]{2,35}")) }
            val label = quotedAttribute(line, "NAME", 80)
            MediaForgeClassifier.caption(resolved)?.copy(language = language, label = label)
        }.distinctBy { it.url }.take(30).toList()

    private fun quotedAttribute(line: String, attribute: String, maxLength: Int): String? {
        val marker = "$attribute=\""
        val start = line.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = line.indexOf('"', from)
        if (end <= from || end - from > maxLength) return null
        return line.substring(from, end)
    }
}

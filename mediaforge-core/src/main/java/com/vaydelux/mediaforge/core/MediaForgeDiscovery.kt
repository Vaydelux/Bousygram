package com.vaydelux.mediaforge.core

import java.net.URI
import java.util.Locale

/** Source URLs remain in-memory only: they can include expiring bearer tokens. */
enum class StreamFormat { MP4, HLS, DASH, WEBM, MPEG_TS }
enum class CaptionFormat { SRT, VTT, TTML }

data class StreamCandidate(val url: String, val format: StreamFormat, val host: String)
data class CaptionCandidate(
    val url: String,
    val format: CaptionFormat,
    val host: String,
    val language: String? = null,
    val label: String? = null
)
data class MediaSnapshot(
    val streams: List<StreamCandidate>,
    val captions: List<CaptionCandidate>
)

/** No DNS lookup here; network probes use a separate validated transport. */
object MediaForgeUrlPolicy {
    fun safeHttpsUrl(value: String): URI? {
        if (value.length > 8192 || value.any { it.code < 32 }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host.length > 253 || !host.contains('.') || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".localhost") ||
            host.endsWith(".test") || host.endsWith(".home.arpa") ||
            host == "localhost" || host.startsWith("127.") ||
            host.matches(Regex("[0-9.]+")) || host.contains(':') ||
            host.contains('%') || uri.port !in listOf(-1, 443)) return null
        return uri
    }
}

object MediaForgeClassifier {
    fun stream(url: String): StreamCandidate? {
        val uri = MediaForgeUrlPolicy.safeHttpsUrl(url) ?: return null
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val kind = when {
            path.endsWith(".m3u8") -> StreamFormat.HLS
            path.endsWith(".mpd") -> StreamFormat.DASH
            path.endsWith(".mp4") || path.endsWith(".m4v") -> StreamFormat.MP4
            path.endsWith(".webm") -> StreamFormat.WEBM
            path.endsWith(".ts") -> StreamFormat.MPEG_TS
            else -> return null
        }
        return StreamCandidate(url, kind, uri.host)
    }

    fun caption(url: String): CaptionCandidate? {
        val uri = MediaForgeUrlPolicy.safeHttpsUrl(url) ?: return null
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val kind = when {
            path.endsWith(".vtt") -> CaptionFormat.VTT
            path.endsWith(".srt") -> CaptionFormat.SRT
            path.endsWith(".ttml") || path.endsWith(".dfxp") -> CaptionFormat.TTML
            else -> return null
        }
        return CaptionCandidate(url, kind, uri.host)
    }
}

/** Bounded thread-safe registry; does not sniff DRM, decode media, or capture HTTP bodies. */
class MediaForgeDiscovery(private val maxStreams: Int = 20, private val maxCaptions: Int = 30) {
    private val streams = LinkedHashMap<String, StreamCandidate>()
    private val captions = LinkedHashMap<String, CaptionCandidate>()

    @Synchronized
    fun observe(url: String): Boolean {
        val stream = MediaForgeClassifier.stream(url)
        if (stream != null) {
            if (streams.containsKey(url)) return false
            if (streams.size >= maxStreams) streams.remove(streams.keys.first())
            streams[url] = stream
            return true
        }
        val caption = MediaForgeClassifier.caption(url) ?: return false
        if (captions.containsKey(url)) return false
        if (captions.size >= maxCaptions) captions.remove(captions.keys.first())
        captions[url] = caption
        return true
    }

    @Synchronized
    fun snapshot(): MediaSnapshot = MediaSnapshot(streams.values.toList(), captions.values.toList())

    @Synchronized
    fun clear() {
        streams.clear()
        captions.clear()
    }
}

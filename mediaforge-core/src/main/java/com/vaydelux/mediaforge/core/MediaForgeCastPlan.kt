package com.vaydelux.mediaforge.core

import java.util.Locale

/**
 * Receiver-side HTTPS fetches do not inherit MediaForge's guarded DNS,
 * redirects, or HTTP transport. Only explicitly qualified origins may be
 * handed to a default receiver. An empty allowlist intentionally denies all.
 * The operator must qualify child manifests, segments, redirects and CORS;
 * this policy alone does not verify those resources.
 */
class MediaForgeCastPolicy(qualifiedHosts: Set<String>) {
    private val hosts = qualifiedHosts.map { it.lowercase(Locale.ROOT) }.toSet()

    private fun permitted(url: String): Boolean {
        val uri = MediaForgeUrlPolicy.safeHttpsUrl(url) ?: return false
        return uri.host.lowercase(Locale.ROOT) in hosts &&
            uri.rawQuery == null && uri.rawFragment == null
    }

    fun plan(
        media: InspectedMedia,
        title: String,
        positionMs: Long = 0L,
        playRequested: Boolean = true,
        live: Boolean = false,
        selectedCaption: CaptionCandidate? = null
    ): MediaForgeCastPlan? {
        val stream = media.stream
        if (media.responseCode !in 200..299 || !permitted(stream.url)) return null
        val mime = when (stream.format) {
            StreamFormat.HLS -> "application/vnd.apple.mpegurl"
            StreamFormat.DASH -> "application/dash+xml"
            StreamFormat.MP4 -> "video/mp4"
            else -> return null
        }
        if (live && stream.format == StreamFormat.MP4) return null
        if (positionMs < 0L || (live && positionMs != 0L)) return null
        if (title.isBlank() || title.length > 256 || title.any { it.isISOControl() }) return null
        if (selectedCaption != null && selectedCaption !in media.captions) return null
        // Standalone SRT requires conversion and secure receiver-readable
        // delivery. Do not falsely label an SRT URL as text/vtt.
        val captions = media.captions.take(30).distinctBy { it.url }
            .filter { permitted(it.url) && it.format in setOf(CaptionFormat.VTT, CaptionFormat.TTML) }
            .mapIndexed { index, caption ->
                CastCaptionTrack(
                    id = index.toLong() + 1L,
                    url = caption.url,
                    mimeType = if (caption.format == CaptionFormat.VTT) "text/vtt" else "application/ttml+xml",
                    language = caption.language?.take(35),
                    label = (caption.label ?: caption.language ?: caption.host).take(120)
                )
            }
        val selected = selectedCaption?.let { candidate ->
            captions.firstOrNull { it.url == candidate.url }?.id ?: return null
        }
        return MediaForgeCastPlan(
            stream.url, mime, title, if (live) null else positionMs,
            playRequested, live, captions, selected
        )
    }
}

/** In-memory only. Never serialize or log provider URLs or user titles. */
class MediaForgeCastPlan internal constructor(
    val url: String,
    val mimeType: String,
    val title: String,
    val positionMs: Long?,
    val playRequested: Boolean,
    val live: Boolean,
    val captions: List<CastCaptionTrack>,
    val selectedTrackId: Long?
)

class CastCaptionTrack internal constructor(
    val id: Long,
    val url: String,
    val mimeType: String,
    val language: String?,
    val label: String
)

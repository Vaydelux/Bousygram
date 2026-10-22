package com.vaydelux.bousygram.core.media

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Central mapping for Telegram video documents and ExoPlayer progressive streaming.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoFormatSupport {

    val VIDEO_EXTENSIONS: Set<String> = setOf(
        ".3g2", ".3gp", ".asf", ".avi", ".divx", ".f4v", ".flv", ".fmp4", ".m2ts", ".m4v",
        ".mkv", ".mov", ".mp4", ".mpeg", ".mpg", ".mts", ".mxf", ".nut", ".ogg", ".ogv",
        ".rm", ".rmvb", ".ts", ".vob", ".webm", ".wmv", ".m3u8"
    )

    private val VIDEO_MIME_PREFIXES = listOf(
        "video/",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl"
    )

    private val KNOWN_VIDEO_MIMES = setOf(
        "application/mp4",
        "application/x-matroska",
        "application/vnd.ms-sstr+xml",
        "application/octet-stream"
    )

    fun hasVideoExtension(fileName: String): Boolean {
        val lower = fileName.lowercase().trim()
        return VIDEO_EXTENSIONS.any { lower.endsWith(it) }
    }

    fun isPlayableVideo(mimeType: String?, fileName: String?): Boolean {
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        val name = fileName?.lowercase()?.trim().orEmpty()
        if (mime.isNotEmpty()) {
            if (VIDEO_MIME_PREFIXES.any { mime.startsWith(it) }) return true
            if (mime in KNOWN_VIDEO_MIMES && (name.isEmpty() || hasVideoExtension(name))) return true
            when (mime) {
                "application/x-matroska",
                "video/x-matroska",
                "video/mp2t",
                "video/vnd.dlna.mpeg-tts",
                "video/x-msvideo",
                "video/x-flv",
                "video/quicktime",
                "video/3gpp",
                "video/3gpp2",
                "video/webm",
                "video/ogg",
                "video/x-m4v" -> return true
            }
        }
        return hasVideoExtension(name)
    }

    /** Prefer Telegram file name for stream URL / MIME sniffing. */
    fun streamLabel(fileName: String?, title: String): String =
        fileName?.takeIf { it.isNotBlank() } ?: title

    fun httpContentType(mimeType: String?, fileName: String?, title: String): String {
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        if (mime.startsWith("video/") || mime == "application/x-matroska" || mime == "application/mp4") {
            return mime
        }
        return when (extensionOf(streamLabel(fileName, title))) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            "flv", "f4v" -> "video/x-flv"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "3g2" -> "video/3gpp2"
            "mpeg", "mpg" -> "video/mpeg"
            "wmv", "asf" -> "video/x-ms-wmv"
            "ogg", "ogv" -> "video/ogg"
            "fmp4", "m4v", "mp4" -> "video/mp4"
            "m3u8" -> "application/vnd.apple.mpegurl"
            else -> "application/octet-stream"
        }
    }

    /** ExoPlayer [MimeTypes] constant for progressive playback. */
    fun exoMimeType(mimeType: String?, fileName: String?, title: String): String {
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        if (mime.isNotBlank()) {
            when {
                mime == MimeTypes.APPLICATION_M3U8 ||
                    mime == "application/vnd.apple.mpegurl" ||
                    mime == "application/x-mpegurl" -> return MimeTypes.APPLICATION_M3U8
                mime == MimeTypes.VIDEO_MP4 || mime == "application/mp4" -> return MimeTypes.VIDEO_MP4
                mime == MimeTypes.VIDEO_MATROSKA || mime == "application/x-matroska" -> return MimeTypes.VIDEO_MATROSKA
                mime == MimeTypes.VIDEO_WEBM -> return MimeTypes.VIDEO_WEBM
                mime == MimeTypes.VIDEO_MP2T || mime.contains("mp2t") -> return MimeTypes.VIDEO_MP2T
                mime == MimeTypes.VIDEO_AVI || mime == "video/x-msvideo" -> return MimeTypes.VIDEO_AVI
                mime == MimeTypes.VIDEO_MPEG -> return MimeTypes.VIDEO_MPEG
                mime == MimeTypes.VIDEO_FLV -> return MimeTypes.VIDEO_FLV
                mime.startsWith("video/") -> return mime
            }
        }
        return when (extensionOf(streamLabel(fileName, title))) {
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            "webm" -> MimeTypes.VIDEO_WEBM
            "ts", "m2ts", "mts" -> MimeTypes.VIDEO_MP2T
            "avi" -> MimeTypes.VIDEO_AVI
            "flv", "f4v" -> MimeTypes.VIDEO_FLV
            "mov" -> "video/quicktime"
            "mpeg", "mpg" -> MimeTypes.VIDEO_MPEG
            "ogg", "ogv" -> "video/ogg"
            "fmp4", "m4v", "mp4", "3gp", "3g2" -> MimeTypes.VIDEO_MP4
            "m3u8" -> MimeTypes.APPLICATION_M3U8
            else -> MimeTypes.VIDEO_MP4
        }
    }

    private fun extensionOf(label: String): String? {
        val dot = label.lastIndexOf('.')
        if (dot < 0 || dot == label.lastIndex) return null
        return label.substring(dot + 1).lowercase().trim().takeIf { it.isNotEmpty() }
    }
}

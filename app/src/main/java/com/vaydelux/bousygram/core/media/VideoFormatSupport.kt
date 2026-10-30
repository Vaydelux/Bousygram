package com.vaydelux.bousygram.core.media

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Central mapping for Telegram video documents and ExoPlayer progressive streaming.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoFormatSupport {

    val VIDEO_EXTENSIONS: Set<String> = setOf(
        ".3g2", ".3gp", ".asf", ".avi", ".cmaf", ".divx", ".f4v", ".flv", ".fmp4", ".m2ts", ".m4s", ".m4v",
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

    /** MPEG-TS documents frequently have no Telegram-generated thumbnail. */
    fun isTransportStream(fileName: String?, mimeType: String?): Boolean {
        val file = fileName?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)
        return file.endsWith(".ts") || file.endsWith(".m2ts") || file.endsWith(".mts") ||
            mime == "video/mp2t"
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
            "fmp4", "m4s", "cmaf", "m4v", "mp4" -> "video/mp4"
            "m3u8" -> "application/vnd.apple.mpegurl"
            else -> "application/octet-stream"
        }
    }

    /** ExoPlayer [MimeTypes] constant for progressive playback. */
    fun exoMimeType(mimeType: String?, fileName: String?, title: String): String? {
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        if (mime.isNotBlank()) {
            when {
                mime == MimeTypes.APPLICATION_M3U8 ||
                    mime == "application/vnd.apple.mpegurl" ||
                    mime == "application/x-mpegurl" -> return MimeTypes.APPLICATION_M3U8
                mime == MimeTypes.VIDEO_MP4 || mime == "application/mp4" -> return MimeTypes.VIDEO_MP4
                mime == MimeTypes.VIDEO_MATROSKA || mime == "application/x-matroska" -> return MimeTypes.VIDEO_MATROSKA
                mime == MimeTypes.VIDEO_WEBM -> return MimeTypes.VIDEO_WEBM
                mime == MimeTypes.VIDEO_MP2T || mime == "video/mpegts" ||
                    mime == "video/vnd.dlna.mpeg-tts" || mime == "video/x-mpegts" ->
                    return MimeTypes.VIDEO_MP2T
            }
        }
        return when (extensionOf(streamLabel(fileName, title))) {
            "m3u8" -> MimeTypes.APPLICATION_M3U8
            "mp4", "m4v", "fmp4", "3gp", "3g2" -> MimeTypes.VIDEO_MP4
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            "webm" -> MimeTypes.VIDEO_WEBM
            "ts", "m2ts", "mts" -> MimeTypes.VIDEO_MP2T
            "avi" -> MimeTypes.VIDEO_AVI
            "flv", "f4v" -> MimeTypes.VIDEO_FLV
            "mpeg", "mpg" -> MimeTypes.VIDEO_MPEG
            else -> null
        }
    }

    fun resolveContainer(fileName: String?, mimeType: String?, title: String = ""): String {
        val ext = extensionOf(streamLabel(fileName, title))
        if (!ext.isNullOrBlank()) return ext
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        return when {
            mime.contains("matroska") -> "mkv"
            mime.contains("mp4") -> "mp4"
            mime.contains("webm") -> "webm"
            mime.contains("mp2t") -> "ts"
            mime.contains("quicktime") -> "mov"
            mime.contains("msvideo") || mime.contains("avi") -> "avi"
            mime.contains("flv") -> "flv"
            mime.contains("mpegurl") -> "m3u8"
            mime.contains("3gpp") -> "3gp"
            mime.startsWith("video/") -> mime.removePrefix("video/").substringBefore(';').trim()
            else -> "unknown"
        }
    }

    private fun extensionOf(label: String): String? {
        val dot = label.lastIndexOf('.')
        if (dot < 0 || dot == label.lastIndex) return null
        return label.substring(dot + 1).lowercase().trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Determines whether an error is caused by video/audio decoder initialization, capability, or decoding failures.
     */
    fun isDecoderError(error: Throwable?, message: String?): Boolean {
        if (error is androidx.media3.common.PlaybackException) {
            when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> return true
            }
        }
        val text = buildString {
            if (!message.isNullOrBlank()) append(message).append(" ")
            if (error != null) {
                append(error::class.java.name).append(" ")
                append(error.message.orEmpty()).append(" ")
                error.cause?.let {
                    append(it::class.java.name).append(" ")
                    append(it.message.orEmpty())
                }
            }
        }.lowercase()
        return text.contains("decoder") ||
            text.contains("mediacodec") ||
            text.contains("codec_") ||
            text.contains("codecinit") ||
            text.contains("unsupported_codec")
    }

    /**
     * Determines whether an error is caused by container demuxing, stream parsing, or malformed headers.
     */
    fun isContainerError(error: Throwable?, message: String?): Boolean {
        if (error is androidx.media3.common.PlaybackException) {
            when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> return true
            }
        }
        val text = buildString {
            if (!message.isNullOrBlank()) append(message).append(" ")
            if (error != null) {
                append(error::class.java.name).append(" ")
                append(error.message.orEmpty()).append(" ")
                error.cause?.let {
                    append(it::class.java.name).append(" ")
                    append(it.message.orEmpty())
                }
            }
        }.lowercase()
        return text.contains("container") ||
            text.contains("parser") ||
            text.contains("malformed") ||
            text.contains("extractor") ||
            text.contains("demux")
    }

    /**
     * Determines whether an error is caused by unsupported stream or track formats.
     */
    fun isUnsupportedFormatError(error: Throwable?, message: String?): Boolean {
        if (error is androidx.media3.common.PlaybackException) {
            when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> return true
            }
        }
        val text = buildString {
            if (!message.isNullOrBlank()) append(message).append(" ")
            if (error != null) {
                append(error::class.java.name).append(" ")
                append(error.message.orEmpty()).append(" ")
                error.cause?.let {
                    append(it::class.java.name).append(" ")
                    append(it.message.orEmpty())
                }
            }
        }.lowercase()
        return text.contains("unsupported") ||
            text.contains("unrecognized") ||
            text.contains("none of the available extractors") ||
            text.contains("format not supported")
    }

    /**
     * Identifies whether the given error warrants an automatic fallback to libmpv.
     * Triggers strictly on decoder, container, or unsupported-format errors.
     */
    fun isExoPlayerFallbackCandidate(error: Throwable?, message: String?): Boolean =
        isDecoderError(error, message) || isContainerError(error, message) || isUnsupportedFormatError(error, message)

    /**
     * Classifies the specific fallback reason for telemetry reporting.
     */
    fun classifyFallbackReason(error: Throwable?, message: String?): String {
        val detail = message?.takeIf { it.isNotBlank() } ?: error?.message?.takeIf { it.isNotBlank() } ?: "Unknown cause"
        return when {
            isDecoderError(error, message) -> "Decoder error: $detail"
            isContainerError(error, message) -> "Container/parser error: $detail"
            isUnsupportedFormatError(error, message) -> "Unsupported format error: $detail"
            else -> "Format error: $detail"
        }
    }
}

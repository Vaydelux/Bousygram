package com.vaydelux.bousygram.core.cast

import java.util.Locale

/** Conservative container admission, not receiver codec certification. */
internal object TelegramCastCompatibility {
    fun rejection(mime: String?, fileName: String?): String? {
        val name = fileName.orEmpty().lowercase(Locale.ROOT)
        val type = mime.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        if (name.endsWith(".mkv") || type in setOf("video/x-matroska", "video/matroska")) {
            return "MKV Cast is unavailable: container conversion is not implemented. Play on Bousygram instead."
        }
        if (name.endsWith(".ts") || name.endsWith(".m2ts") || type == "video/mp2t") {
            return "TS Cast is unavailable: compatible HLS packaging is not implemented. Play on Bousygram instead."
        }
        if (type == "video/mp4" || name.endsWith(".mp4") || name.endsWith(".m4v")) return null
        return "This container is not supported for direct Telegram Cast. Try an MP4 with H.264/AAC."
    }
}

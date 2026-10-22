package com.vaydelux.bousygram.core.tdlib.fastlink

import java.net.URI

/** Strict validation for URLs returned by the configured Fast-Link bot. */
internal object FastLinkUrlValidator {
    /** Return a normalized HTTPS URL, or null when the bot response is not safe to use. */
    fun validate(candidate: String?): String? {
        val value = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.any { it.isWhitespace() || it.isISOControl() }) return null

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        if (uri.rawFragment != null) return null

        return runCatching { uri.normalize().toASCIIString() }.getOrNull()
    }
}

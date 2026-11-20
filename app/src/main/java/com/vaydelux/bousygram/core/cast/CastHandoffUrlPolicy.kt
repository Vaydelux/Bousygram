package com.vaydelux.bousygram.core.cast

import java.net.URI

/**
 * Cast can only load the exact resource offered by this session's private-LAN
 * Ktor server. Reject arbitrary WebView URLs, public IPs, token changes and
 * alternate paths. Never resolve DNS to validate an untrusted URL.
 *
 * This is authorization hardening, NOT encryption: production private Cast
 * remains disabled until receiver-trusted TLS is qualified on real devices.
 */
internal object CastHandoffUrlPolicy {
    private val resourcePath = Regex("""^/cast/media/[A-Za-z0-9_-]{20,64}$""")
    private val capability = Regex("""^token=[A-Za-z0-9_-]{40,64}$""")

    fun permits(requested: String?, active: String?): Boolean {
        if (requested.isNullOrEmpty() || active.isNullOrEmpty() || requested != active) return false
        return try {
            val uri = URI(requested)
            if (uri.scheme != "http" || uri.userInfo != null ||
                uri.fragment != null || uri.port !in 1..65535) return false
            if (!isPrivateIpv4(uri.host)) return false
            if (!resourcePath.matches(uri.rawPath ?: "")) return false
            capability.matches(uri.rawQuery ?: "")
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateIpv4(host: String?): Boolean {
        val parts = host?.split('.') ?: return false
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return false
            val value = part.toIntOrNull() ?: return false
            if (value !in 0..255) return false
            value
        }
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }
}

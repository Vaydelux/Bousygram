package com.vaydelux.bousygram.core.tdlib.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived capability URLs for loopback stream endpoints. A token authorizes exactly one
 * resource (one TDLib file ID or the generated playlist), never arbitrary file IDs.
 */
internal object StreamAccessRegistry {
    private const val TOKEN_BYTES = 32
    private const val TOKEN_TTL_MS = 6 * 60 * 60 * 1000L
    // Queue URLs are created lazily, so this is a safety ceiling rather than a normal-use limit.
    // Never evict a still-valid grant just to admit a new one: that can break an active seek/read.
    private const val MAX_TOKENS = 8192
    private const val PLAYLIST_RESOURCE = "playlist"
    private const val HEX = "0123456789abcdef"

    private val random = SecureRandom()
    private val grants = ConcurrentHashMap<String, Grant>()

    private data class Grant(val resource: String, val expiresAtMs: Long)

    fun issueFile(fileId: Int): String {
        require(fileId > 0) { "fileId must be positive" }
        return issue("file:$fileId")
    }

    fun issuePlaylist(): String = issue(PLAYLIST_RESOURCE)

    fun authorizesFile(token: String?, fileId: Int): Boolean =
        authorize(token, "file:$fileId")

    fun authorizesPlaylist(token: String?): Boolean = authorize(token, PLAYLIST_RESOURCE)

    private fun issue(resource: String): String = synchronized(grants) {
        val now = System.currentTimeMillis()
        grants.entries.removeIf { (_, grant) -> grant.expiresAtMs <= now }
        check(grants.size < MAX_TOKENS) {
            "The active stream authorization limit was reached; refusing to invalidate existing stream URLs"
        }

        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        // Hex encoding avoids java.util.Base64's API 26 requirement on the app's API 23 minimum.
        val token = buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                append(HEX[(byte.toInt() ushr 4) and 0x0f])
                append(HEX[byte.toInt() and 0x0f])
            }
        }
        grants[token] = Grant(resource, now + TOKEN_TTL_MS)
        token
    }

    /** Revoke one capability URL when its owner no longer needs it. */
    fun revoke(token: String?): Boolean {
        if (token.isNullOrBlank() || token.length > 128) return false
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        val matchedKey = grants.keys.firstOrNull { key ->
            MessageDigest.isEqual(key.toByteArray(Charsets.UTF_8), tokenBytes)
        } ?: return false
        return grants.remove(matchedKey) != null
    }

    /** Extract and revoke this app's short capability from a locally generated URL. */
    fun revokeFromUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        // Do not interpret an arbitrary remote URL as a local capability URL.
        if (!url.startsWith("http://127.0.0.1:") && !url.startsWith("http://localhost:")) return false
        val query = url.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return false
        val token = query.split('&')
            .firstOrNull { it.startsWith("token=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return revoke(token)
    }

    /** Remove expired entries eagerly without invalidating any still-valid grant. */
    fun purgeExpired(): Int = synchronized(grants) {
        val before = grants.size
        val now = System.currentTimeMillis()
        grants.entries.removeIf { (_, grant) -> grant.expiresAtMs <= now }
        before - grants.size
    }

    /**
     * Rotates all current stream tokens on network transition.
     * Existing tokens are given a brief 15-second grace window to let in-flight reads finish,
     * while expiring stale tokens so they cannot survive the handoff.
     */
    fun rotateTokens() = synchronized(grants) {
        val graceExpiry = System.currentTimeMillis() + 15_000L
        for ((token, grant) in grants) {
            if (grant.expiresAtMs > graceExpiry) {
                grants[token] = grant.copy(expiresAtMs = graceExpiry)
            }
        }
        android.util.Log.i("StreamAccessRegistry", "Rotated ${grants.size} active stream tokens with 15s grace window")
    }

    private fun authorize(token: String?, expectedResource: String): Boolean {
        if (token.isNullOrBlank() || token.length > 128) return false
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        val matchedKey = grants.keys.firstOrNull { key ->
            MessageDigest.isEqual(key.toByteArray(Charsets.UTF_8), tokenBytes)
        } ?: return false

        val grant = grants[matchedKey] ?: return false
        if (grant.expiresAtMs <= System.currentTimeMillis()) {
            grants.remove(matchedKey, grant)
            return false
        }
        return grant.resource == expectedResource
    }
}

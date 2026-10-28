package com.vaydelux.bousygram.core.cast

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Short lived media capabilities scoped to one Cast session and one Telegram file. */
internal object CastAccessRegistry {
    private const val TOKEN_BYTES = 32
    private const val MAX_GRANTS = 8
    // Capabilities are session-scoped and fail closed after two hours even if teardown is missed.
    private const val SESSION_TTL_MS = 2L * 60L * 60L * 1000L
    private val random = SecureRandom()
    private val grants = ConcurrentHashMap<String, Grant>()
    private data class Grant(val fileId: Int, val token: String, val expiresAtMs: Long)
    class Capability internal constructor(val resourceId: String, val token: String, val fileId: Int)

    @Synchronized
    fun issue(fileId: Int): Capability {
        require(fileId > 0)
        purgeExpired()
        check(grants.size < MAX_GRANTS) { "Cast stream resource limit reached" }
        val resourceId = randomToken(18)
        val token = randomToken(TOKEN_BYTES)
        grants[resourceId] = Grant(fileId, token, System.currentTimeMillis() + SESSION_TTL_MS)
        return Capability(resourceId, token, fileId)
    }

    fun authorize(resourceId: String?, token: String?): Int? {
        if (resourceId.isNullOrBlank() || resourceId.length > 64 || token.isNullOrBlank() || token.length > 64) return null
        val grant = grants[resourceId] ?: return null
        if (grant.expiresAtMs <= System.currentTimeMillis()) {
            grants.remove(resourceId, grant)
            return null
        }
        return grant.fileId.takeIf {
            MessageDigest.isEqual(grant.token.toByteArray(Charsets.US_ASCII), token.toByteArray(Charsets.US_ASCII))
        }
    }

    fun revoke(resourceId: String?) {
        if (!resourceId.isNullOrBlank()) grants.remove(resourceId)
    }

    @Synchronized
    fun revokeAll() = grants.clear()

    @Synchronized
    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        grants.entries.removeIf { it.value.expiresAtMs <= now }
    }

    private fun randomToken(bytes: Int): String = ByteArray(bytes).also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}

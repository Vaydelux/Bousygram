package com.vaydelux.bousygram.core.cast

/**
 * Main-thread scoped ownership of one explicitly selected receiver and load.
 * A callback from a previous load, session or disposed screen cannot authorize
 * commands. Receiver content identity must also match; do not take over a
 * different sender's media just because the route is still connected.
 */
internal class ExtrasCastLease {
    private var generation = 0L
    private var sessionId: String? = null
    private var contentId: String? = null
    private var acknowledged = false

    fun claim(newSessionId: String, explicitlyChosen: Boolean, existingMedia: Boolean): Boolean {
        if (!explicitlyChosen || existingMedia || newSessionId.isBlank() || sessionId != null) return false
        sessionId = newSessionId
        generation++
        return true
    }

    fun beginLoad(currentSessionId: String, url: String): Long? {
        if (currentSessionId != sessionId || contentId != null || url.isBlank()) return null
        contentId = url
        acknowledged = false
        return ++generation
    }

    fun acknowledge(token: Long, currentSessionId: String, success: Boolean): Boolean {
        if (token != generation || currentSessionId != sessionId || contentId == null) return false
        if (!success) {
            revoke()
            return false
        }
        acknowledged = true
        return true
    }

    fun controls(currentSessionId: String?, currentContentId: String?): Boolean =
        acknowledged && currentSessionId != null && currentSessionId == sessionId &&
            currentContentId != null && currentContentId == contentId

    fun owns(currentSessionId: String?): Boolean =
        currentSessionId != null && currentSessionId == sessionId

    fun mayEnd(currentSessionId: String?, currentContentId: String?): Boolean =
        owns(currentSessionId) && (currentContentId == null || currentContentId == contentId)

    fun revoke() {
        generation++
        sessionId = null
        contentId = null
        acknowledged = false
    }
}

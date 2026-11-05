package com.vaydelux.bousygram.core.tdlib.auth

/**
 * Tracks the one GetMe identity check allowed to activate an account.
 *
 * A network stall or TDLib client replacement may abandon a native callback.
 * After a timeout/retry, the abandoned response must never be allowed to
 * activate an account or overwrite a newer auth error. This gate deliberately
 * holds no Android or TDLib references so the race can be tested on the JVM.
 */
internal class IdentityVerificationAttemptGate {
    data class Attempt(val generation: Long, val serial: Long)
    private enum class Phase { AWAITING_RESPONSE, ACTIVATING }

    private val lock = Any()
    private var serial = 0L
    private var active: Pair<Attempt, Phase>? = null

    fun begin(generation: Long): Attempt? = synchronized(lock) {
        if (active != null) return@synchronized null
        Attempt(generation, ++serial).also { active = it to Phase.AWAITING_RESPONSE }
    }

    /** Only one callback may claim the pending result, even if TDLib duplicates it. */
    fun acceptResponse(attempt: Attempt): Boolean = synchronized(lock) {
        if (active != (attempt to Phase.AWAITING_RESPONSE)) return@synchronized false
        active = attempt to Phase.ACTIVATING
        true
    }

    /** Timeout is allowed only while TDLib has not answered, not during store activation. */
    fun expire(attempt: Attempt): Boolean = synchronized(lock) {
        if (active != (attempt to Phase.AWAITING_RESPONSE)) return@synchronized false
        active = null
        true
    }

    /** Finishing an earlier attempt must not clear a newer retry. */
    fun finish(attempt: Attempt): Boolean = synchronized(lock) {
        if (active?.first != attempt) return@synchronized false
        active = null
        true
    }

    /** Invoked on logout, native close/restart, retry, or manager disposal. */
    fun reset() = synchronized(lock) { active = null }
}

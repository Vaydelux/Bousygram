package com.vaydelux.bousygram.core.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A verified Telegram identity and the lifetime in which its data may be used. */
data class AccountSnapshot(val accountId: Long, val generation: Long)

/**
 * No private application store is available before GetMe verifies the account. Every asynchronous
 * operation captures a snapshot, rather than looking up whichever account is active on completion.
 */
open class AccountSessionController {
    private val lock = Any()
    // Serializes complete invalidation transitions without holding the short-lived state
    // lock while a media owner must synchronously release a player on the main thread.
    private val invalidationLock = Any()
    @Volatile private var epoch = 0L
    private var cleanupComplete = true
    private val invalidationHandlers = mutableListOf<() -> Unit>()
    private val _state = MutableStateFlow<AccountSnapshot?>(null)
    val state: StateFlow<AccountSnapshot?> = _state.asStateFlow()

    fun current(): AccountSnapshot? = _state.value
    fun generation(): Long = epoch
    fun isCurrent(snapshot: AccountSnapshot): Boolean =
        _state.value == snapshot && epoch == snapshot.generation

    /**
     * Mark the session invalid under the short-lived state lock, then perform synchronous
     * cleanup without holding that lock. Playback teardown can post to Android's main thread;
     * that thread must still be able to ask isCurrent/runIfCurrent (and get false) without
     * blocking behind cleanup. The separate invalidationLock serializes concurrent logouts.
     * No new account can activate until every handler has succeeded.
     */
    fun invalidate(): Long = synchronized(invalidationLock) {
        val (newGeneration, handlers) = synchronized(lock) {
            epoch += 1L
            _state.value = null
            cleanupComplete = false
            epoch to invalidationHandlers.toList()
        }
        var failure: Throwable? = null
        handlers.forEach { handler ->
            try { handler() } catch (error: Exception) { if (failure == null) failure = error }
        }
        synchronized(lock) { cleanupComplete = failure == null }
        check(failure == null) { "Private account resources could not be invalidated" }
        newGeneration
    }

    fun activate(accountId: Long, expectedGeneration: Long): AccountSnapshot? = synchronized(lock) {
        require(accountId > 0L) { "Telegram account identity must be positive" }
        if (epoch != expectedGeneration || !cleanupComplete) return@synchronized null
        val existing = _state.value
        if (existing != null) return@synchronized existing.takeIf { it.accountId == accountId }
        AccountSnapshot(accountId, epoch).also { _state.value = it }
    }

    fun registerInvalidationHandler(handler: () -> Unit) = synchronized(lock) {
        invalidationHandlers += handler
    }

    /** A short publication/commit whose final account check cannot race invalidation. */
    fun runIfCurrent(snapshot: AccountSnapshot, action: () -> Unit): Boolean = synchronized(lock) {
        if (!isCurrent(snapshot)) return@synchronized false
        action()
        true
    }

    fun runIfGeneration(expectedGeneration: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (epoch != expectedGeneration || !cleanupComplete) return@synchronized false
        action()
        true
    }
}

object AccountSession : AccountSessionController()

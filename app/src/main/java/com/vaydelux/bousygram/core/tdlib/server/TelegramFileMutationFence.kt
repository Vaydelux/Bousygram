package com.vaydelux.bousygram.core.tdlib.server

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared bounded command serialization for gateway starts and cancellations.
 * Foreground/legacy commands always take priority over a qualification producer.
 * Fixed stripes bound lock memory; unrelated colliding IDs may serialize briefly.
 * Raw native logout teardown is separate and must never acquire a suspending lock.
 */
internal class TelegramFileMutationFence(private val current: (AccountSnapshot) -> Boolean) {
    private class Stripe {
        val lock = Mutex()
        @Volatile var claim: Claim? = null
        // A timed-out legacy command may still be applied in TDLib.
        var uncertainOwner: AccountSnapshot? = null
    }

    class Claim internal constructor(val owner: AccountSnapshot, val fileId: Int, internal val lock: Mutex)

    private val stripes = Array(64) { Stripe() }
    private fun stripe(fileId: Int): Stripe = stripes[(fileId and Int.MAX_VALUE) % stripes.size]

    suspend fun acquire(owner: AccountSnapshot, fileId: Int, conflicts: () -> Boolean): Claim? {
        require(fileId > 0)
        val slot = stripe(fileId)
        return slot.lock.withLock {
            val existing = slot.claim
            if (!current(owner) || conflicts() || slot.uncertainOwner == owner ||
                (existing != null && current(existing.owner))) return@withLock null
            Claim(owner, fileId, slot.lock).also { slot.claim = it }
        }
    }

    fun isCurrent(claim: Claim): Boolean = current(claim.owner) && stripe(claim.fileId).claim === claim

    /** A stale close must never remove a later producer's claim. */
    suspend fun release(claim: Claim) {
        val slot = stripe(claim.fileId)
        slot.lock.withLock { if (slot.claim === claim) slot.claim = null }
    }

    /**
     * Explicitly retain the captured account through the mutex suspension.
     * Once inside the fence, revoke any same-file producer before dispatch.
     * Caller supplies an account-fenced native request, not an implicit account lookup.
     */
    suspend fun <T> legacy(owner: AccountSnapshot, fileId: Int, action: suspend () -> T): T {
        require(fileId > 0)
        val slot = stripe(fileId)
        return slot.lock.withLock {
            if (!current(owner)) throw CancellationException("Telegram account changed before file mutation")
            if (slot.claim?.fileId == fileId) slot.claim = null
            try {
                val result = action()
                if (!current(owner)) throw CancellationException("Telegram account changed during file mutation")
                result
            } catch (error: Exception) {
                slot.uncertainOwner = owner
                throw error
            }
        }
    }
}

internal object SharedTelegramFileMutationFence {
    val instance = TelegramFileMutationFence(AccountSession::isCurrent)
}

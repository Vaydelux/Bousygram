package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSessionController
import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CancellationException

/** Hold receiver account identity from acceptance through final publication. */
internal class TelegramTvAccountHandoff(
    private val accounts: AccountSessionController = AccountSession,
    private val resolveFile: suspend (AccountSnapshot, TelegramTvHandoff) -> Int? =
        TelegramHandoffAuthorization::resolve
) {
    internal class Prepared internal constructor(
        internal val owner: AccountSnapshot,
        internal val media: ResolvedTelegramTvHandoff
    ) {
        internal var consumed = false
    }

    suspend fun prepare(request: TelegramTvHandoff, acceptedOnTv: Boolean): Prepared? {
        if (!acceptedOnTv || !request.isValid()) return null
        val owner = accounts.current() ?: return null
        val resolved = try {
            TelegramTvHandoffPolicy.resolve(request, true) { _, _ ->
                if (!accounts.isCurrent(owner)) null else resolveFile(owner, request)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null } ?: return null
        return if (accounts.isCurrent(owner)) Prepared(owner, resolved) else null
    }

    /** Only publish short account-scoped state here; no blocking I/O or player creation. */
    fun publish(prepared: Prepared, publishPending: (ResolvedTelegramTvHandoff) -> Unit): Boolean {
        var published = false
        accounts.runIfCurrent(prepared.owner) {
            if (!prepared.consumed) {
                prepared.consumed = true
                publishPending(prepared.media)
                published = true
            }
        }
        return published
    }
}

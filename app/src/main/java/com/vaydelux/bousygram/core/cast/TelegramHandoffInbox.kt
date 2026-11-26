package com.vaydelux.bousygram.core.cast

import android.os.SystemClock
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSessionController
import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process only; no exported intent or persistence. */
internal class TelegramHandoffInbox(
    private val accounts: AccountSessionController,
    private val now: () -> Long
) {
    class Entry internal constructor(val owner: AccountSnapshot,
                                     val media: ResolvedTelegramTvHandoff, val expires: Long)
    private val pending = MutableStateFlow<Entry?>(null)
    val state = pending.asStateFlow()

    fun offer(owner: AccountSnapshot, media: ResolvedTelegramTvHandoff): Entry? {
        var entry: Entry? = null
        accounts.runIfCurrent(owner) {
            if (!media.request.isValid() || media.localFileId <= 0) return@runIfCurrent
            val previous = pending.value
            if (previous != null && now() >= previous.expires) pending.compareAndSet(previous, null)
            val candidate = Entry(owner, media, now() + 30_000L)
            if (pending.compareAndSet(null, candidate)) entry = candidate
        }
        return entry
    }
    fun take(entry: Entry): ResolvedTelegramTvHandoff? {
        if (!pending.compareAndSet(entry, null)) return null
        return entry.media.takeIf { accounts.isCurrent(entry.owner) && now() < entry.expires }
    }
    fun discard(entry: Entry) { pending.compareAndSet(entry, null) }
    fun clear() { pending.value = null }
}

internal object TelegramHandoffNavigation {
    val inbox = TelegramHandoffInbox(AccountSession, SystemClock::elapsedRealtime)
}

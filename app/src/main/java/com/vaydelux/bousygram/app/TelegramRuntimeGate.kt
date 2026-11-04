package com.vaydelux.bousygram.app

import com.vaydelux.bousygram.presentation.shell.BousygramMode

/**
 * Keeps the security/privacy process barrier independent of heavier Telegram services.
 * The first Telegram entry (including a Cast launch) initializes them once per process.
 * A failed attempt is retryable; never publish ready before successful initialization.
 */
internal class TelegramRuntimeGate(private val initialize: () -> Unit) {
    @Volatile
    var isReady: Boolean = false
        private set

    @Synchronized
    fun ensureStarted() {
        if (isReady) return
        initialize()
        isReady = true
    }
}

internal object TelegramRuntimePolicy {
    fun needsTelegramRuntime(mode: BousygramMode): Boolean = mode == BousygramMode.TELEGRAM

    /** A validated Cast payload must enter Telegram mode; Extras never consumes it. */
    fun routeForPendingCast(mode: BousygramMode, hasPendingCast: Boolean): BousygramMode =
        if (hasPendingCast) BousygramMode.TELEGRAM else mode
}

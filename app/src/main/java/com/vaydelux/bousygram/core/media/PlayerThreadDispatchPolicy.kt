package com.vaydelux.bousygram.core.media

import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A timed-out engine owner handoff must not run later from the main queue
 * after the calling thread already disposed the proposed replacement.
 *
 * FutureTask.cancel(false) prevents a queued/unstarted action from running.
 * An action already executing may complete and cannot be safely interrupted;
 * owner state must still enforce its own closed/account checks.
 */
internal fun dispatchPlayerThreadAction(
    enqueue: (Runnable) -> Boolean,
    action: () -> Unit,
    timeoutMs: Long
) {
    require(timeoutMs > 0L)
    val task = FutureTask<Unit> { action() }
    if (!enqueue(task)) {
        task.cancel(false)
        throw IllegalStateException("The playback owner is unavailable")
    }
    try {
        task.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (timeout: TimeoutException) {
        task.cancel(false)
        throw timeout
    } catch (interrupted: InterruptedException) {
        task.cancel(false)
        Thread.currentThread().interrupt()
        throw interrupted
    }
}

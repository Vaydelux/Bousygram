package com.vaydelux.bousygram.core.media

/** Platform lock failures are independent. Always attempt both actions. */
internal fun runIndependentStreamingLockActions(
    first: () -> Unit,
    second: () -> Unit,
    onFailure: (Throwable) -> Unit
) {
    for (action in listOf(first, second)) {
        runCatching(action).onFailure { error -> runCatching { onFailure(error) } }
    }
}

package com.vaydelux.bousygram.core.media

/**
 * Fallback engine construction must not dispose the currently working decoder first.
 * On ARM32 a native decoder can fail during initialization; retain the old engine
 * until the replacement has been constructed and adopted.
 *
 * [bind] attaches the new surface and publishes the replacement to the playback owner.
 * [restore] must undo any partial publication and reattach the old surface.
 * Old-engine cleanup runs only after successful adoption, and stop/release are both
 * attempted even if one native call fails.
 */
internal fun replacePlayerEngineSafely(
    current: PlayerEngine,
    create: () -> PlayerEngine,
    bind: (PlayerEngine) -> Unit,
    restore: () -> Unit,
    reportCleanupFailure: (Throwable) -> Unit = {}
): Result<PlayerEngine> {
    val replacement = try {
        create()
    } catch (failure: Throwable) {
        return Result.failure(failure)
    }
    if (replacement === current) {
        return Result.failure(IllegalStateException("Fallback returned the current engine"))
    }

    try {
        current.detachSurface()
        bind(replacement)
    } catch (failure: Throwable) {
        // Do not strand a newly allocated MPV/LibVLC instance if surface binding
        // or publication fails. No cleanup failure may mask the original error.
        listOf<() -> Unit>(
            { replacement.detachSurface() },
            { replacement.stop() },
            { replacement.release() }
        ).forEach { operation ->
            runCatching(operation).onFailure { if (it !== failure) failure.addSuppressed(it) }
        }
        runCatching(restore).onFailure { if (it !== failure) failure.addSuppressed(it) }
        return Result.failure(failure)
    }

    // The new engine is now the owner. A faulty old native teardown must not
    // revert to a player that may already have been partially destroyed.
    runCatching { current.stop() }.onFailure(reportCleanupFailure)
    runCatching { current.release() }.onFailure(reportCleanupFailure)
    return Result.success(replacement)
}

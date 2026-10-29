package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.data.store.SelectedPlayerEngine

/**
 * Constructs a replacement before handing it to the single engine owner.
 * If the old engine cannot close or the session was already disposed, release the newly
 * allocated, never-adopted engine immediately instead of leaking native player resources.
 */
class PlaybackEngineSwitchCoordinator(
    private val owner: PlaybackEngineOwner,
    private val factory: (SelectedPlayerEngine, Float) -> PlayerEngine
) {
    fun switchTo(target: SelectedPlayerEngine, speed: Float): Result<PlayerEngine> =
        runCatching { adopt(factory(target, speed)) }

    /** The automatic format fallback uses the same ownership and failure path as manual switch. */
    fun fallback(createFallback: () -> Pair<PlayerEngine, Long>): Result<Pair<PlayerEngine, Long>> =
        runCatching {
            val (engine, position) = createFallback()
            adopt(engine) to position
        }

    private fun adopt(next: PlayerEngine): PlayerEngine {
        // A factory may return the current instance; it is not ours to release.
        if (owner.value === next) return next
        try {
            owner.value = next
        } catch (error: Throwable) {
            // An owner can reject adoption after logout or if disposal failed. Only release
            // an engine still outside the owner; never double-release an already adopted one.
            if (owner.value !== next) {
                runCatching { next.detachSurface() }.onFailure(error::addSuppressed)
                runCatching { next.stop() }.onFailure(error::addSuppressed)
                runCatching { next.release() }.onFailure(error::addSuppressed)
            }
            throw error
        }
        return next
    }
}

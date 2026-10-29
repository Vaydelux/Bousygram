package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.data.store.SelectedPlayerEngine

/** Build replacement first; PlaybackEngineOwner alone releases the previous engine. */
class PlaybackEngineSwitchCoordinator(
    private val owner: PlaybackEngineOwner,
    private val factory: (SelectedPlayerEngine, Float) -> PlayerEngine
) {
    fun switchTo(target: SelectedPlayerEngine, speed: Float): Result<PlayerEngine> =
        runCatching {
            val next = factory(target, speed)
            owner.value = next
            next
        }
}

package com.vaydelux.bousygram.core.media

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.atomic.AtomicBoolean

/** One session owns one engine slot. Replacing an engine never releases the session. */
class PlaybackEngineOwner(
    private val session: PlaybackSession,
    initialEngine: PlayerEngine,
    private val onOwnerThread: (() -> Unit) -> Unit = AccountMediaResources::onPlayerThread
) : MutableState<PlayerEngine> {
    private val state = mutableStateOf(initialEngine)
    private val closed = AtomicBoolean(false)
    private var engineResource: (() -> Unit)? = null
    // Explicit function type breaks recursive inference: the registered callback calls close().
    private val unregisterOwner: () -> Unit = PlaybackSessionController.registerResourceOwner(session) { close() }

    override var value: PlayerEngine
        get() = state.value
        set(replacement) = onOwnerThread {
            check(!closed.get()) { "Playback session is closed" }
            val old = state.value
            if (old !== replacement) {
                engineResource?.invoke()
                engineResource = null
                release(old)
                state.value = replacement
            }
        }

    override fun component1(): PlayerEngine = value
    override fun component2(): (PlayerEngine) -> Unit = { value = it }

    /** MediaSession must close before the engine it references. */
    fun registerEngineResource(engine: PlayerEngine, close: () -> Unit): () -> Unit {
        val once = AtomicBoolean(false)
        val closeOnce = { if (once.compareAndSet(false, true)) close() }
        if (closed.get() || state.value !== engine) closeOnce() else engineResource = closeOnce
        return closeOnce
    }

    fun close() = onOwnerThread {
        if (closed.compareAndSet(false, true)) {
            unregisterOwner()
            var failure: Throwable? = null
            runCatching { engineResource?.invoke() }.onFailure { failure = it }
            engineResource = null
            runCatching { release(state.value) }.onFailure { failure = failure ?: it }
            failure?.let { throw it }
        }
    }

    private fun release(engine: PlayerEngine) {
        var failure: Throwable? = null
        listOf<() -> Unit>({ engine.detachSurface() }, { engine.stop() }, { engine.release() }).forEach { action ->
            runCatching(action).onFailure { failure = failure ?: it }
        }
        failure?.let { throw it }
    }
}

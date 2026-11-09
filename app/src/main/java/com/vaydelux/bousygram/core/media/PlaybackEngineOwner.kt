package com.vaydelux.bousygram.core.media

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** One session owns one engine slot. Replacing an engine never releases the session. */
class PlaybackEngineOwner(
    private val session: PlaybackSession,
    initialEngine: PlayerEngine,
    private val onOwnerThread: (() -> Unit) -> Unit = AccountMediaResources::onPlayerThread,
    private val reportReplacementCleanupFailure: (Throwable) -> Unit = { error ->
        Log.w("PlaybackEngineOwner", "Retired native engine cleanup failed after handoff", error)
    }
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
                // The bridge must close before its retired engine, but a vendor
                // failure in old JNI teardown must not leave the owner pointing
                // at a half-released decoder while discarding the new one.
                val retiringResource = engineResource
                engineResource = null
                runCatching { retiringResource?.invoke() }
                    .onFailure(::reportRetirementError)
                state.value = replacement
                runCatching { release(old) }
                    .onFailure(::reportRetirementError)
            }
        }

    override fun component1(): PlayerEngine = value
    override fun component2(): (PlayerEngine) -> Unit = { value = it }

    /** MediaSession must close before the engine it references. */
    fun registerEngineResource(engine: PlayerEngine, close: () -> Unit): () -> Unit {
        val once = AtomicBoolean(false)
        val closeOnce = { if (once.compareAndSet(false, true)) close() }
        // Registration can arrive from a MediaSession callback while an
        // engine swap or account teardown is executing on Android's main thread.
        // Serialize all three operations on the same owner thread; otherwise
        // a bridge can be installed after the old engine has been released.
        onOwnerThread {
            if (closed.get() || state.value !== engine) {
                // A stale callback must not undo ownership of the active engine.
                runCatching { closeOnce() }.onFailure(::reportRetirementError)
            } else {
                // Old MediaSession bridge teardown can fail in platform/vendor code.
                // Clear it first, attempt closure, then register the new bridge
                // regardless of the retired bridge's failure.
                val retiringResource = engineResource
                engineResource = null
                runCatching { retiringResource?.invoke() }.onFailure(::reportRetirementError)
                engineResource = closeOnce
            }
        }
        return closeOnce
    }

    fun close() = onOwnerThread {
        if (closed.compareAndSet(false, true)) {
            // Unregister is not allowed to short-circuit native release.
            // Complete every cleanup step and preserve the original failure.
            var failure: Throwable? = null
            fun record(error: Throwable) {
                if (failure == null) failure = error
                else if (failure !== error) failure?.addSuppressed(error)
            }
            runCatching { unregisterOwner() }.onFailure(::record)
            val ownedResource = engineResource
            engineResource = null
            runCatching { ownedResource?.invoke() }.onFailure(::record)
            runCatching { release(state.value) }.onFailure(::record)
            failure?.let { throw it }
        }
    }

    private fun reportRetirementError(error: Throwable) {
        // Logging must not undo an already-adopted replacement decoder.
        runCatching { reportReplacementCleanupFailure(error) }
    }

    private fun release(engine: PlayerEngine) {
        var failure: Throwable? = null
        listOf<() -> Unit>({ engine.detachSurface() }, { engine.stop() }, { engine.release() }).forEach { action ->
            runCatching(action).onFailure { error ->
                if (failure == null) failure = error
                else if (failure !== error) failure?.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}

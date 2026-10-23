package com.vaydelux.bousygram.core.media

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.core.network.NetworkConnectivityObserver
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine

/**
 * Factory providing the active [PlayerEngine]. Defaults to [MpvPlayerEngine] (libmpv) for
 * comprehensive container/codec support, with graceful fallback to [ExoPlayerEngine].
 */
object PlayerEngineFactory {
    private const val TAG = "PlayerEngineFactory"

    fun create(
        context: Context,
        preferredEngine: SelectedPlayerEngine = EnterprisePreferences.selectedPlayerEngine(),
        preferredSpeed: Float = EnterprisePreferences.preferredSpeed()
    ): PlayerEngine {
        val isCellular = NetworkConnectivityObserver.isCellular
        return if (preferredEngine == SelectedPlayerEngine.MPV) {
            try {
                Log.i(TAG, "Creating MpvPlayerEngine (cellular=$isCellular)")
                val engine = MpvPlayerEngine(context = context, initialIsCellular = isCellular).apply {
                    setPlaybackSpeed(preferredSpeed)
                }
                com.vaydelux.bousygram.data.store.StreamHealthTelemetry.recordMpvInitSuccess()
                engine
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to instantiate MpvPlayerEngine, recording failure and falling back to ExoPlayerEngine", e)
                com.vaydelux.bousygram.data.store.StreamHealthTelemetry.recordMpvInitFailure()
                ExoPlayerEngine.create(context = context, preferredSpeed = preferredSpeed)
            }
        } else {
            Log.i(TAG, "Creating ExoPlayerEngine")
            ExoPlayerEngine.create(context = context, preferredSpeed = preferredSpeed)
        }
    }

    /**
     * Gracefully transitions playback to ExoPlayerEngine when MpvPlayerEngine encounters an unrecoverable
     * codec or hardware failure, preserving the current playback position.
     */
    fun createFallback(
        context: Context,
        failedEngine: PlayerEngine,
        preferredSpeed: Float = EnterprisePreferences.preferredSpeed()
    ): Pair<PlayerEngine, Long> {
        val position = failedEngine.currentPositionMs
        Log.w(TAG, "Initiating fallback engine transition from position $position ms")
        failedEngine.release()
        val fallback = ExoPlayerEngine.create(context = context, preferredSpeed = preferredSpeed)
        return Pair(fallback, position)
    }
}

package com.vaydelux.bousygram.core.media

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.core.network.NetworkConnectivityObserver
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine

/**
 * Factory providing the active [PlayerEngine]. Defaults to [SelectedPlayerEngine.AUTO] (ExoPlayer
 * for progressive MP4/hardware decodable streams, falling back seamlessly to [MpvPlayerEngine]).
 */
object PlayerEngineFactory {
    private const val TAG = "PlayerEngineFactory"

    fun create(
        context: Context,
        preferredEngine: SelectedPlayerEngine = EnterprisePreferences.selectedPlayerEngine(),
        preferredSpeed: Float = EnterprisePreferences.preferredSpeed(),
        fileName: String? = null,
        mimeType: String? = null
    ): PlayerEngine {
        val isCellular = NetworkConnectivityObserver.isCellular
        val shouldUseMpv = preferredEngine == SelectedPlayerEngine.MPV
        return if (shouldUseMpv) {
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
            Log.i(TAG, "Creating ExoPlayerEngine (default engine)")
            ExoPlayerEngine.create(context = context, preferredSpeed = preferredSpeed)
        }
    }

    /**
     * Gracefully transitions playback to MpvPlayerEngine when ExoPlayer encounters an unrecoverable
     * codec or container failure, or to ExoPlayerEngine if MPV failed, preserving the current position.
     */
    fun createFallback(
        context: Context,
        failedEngine: PlayerEngine,
        preferredSpeed: Float = EnterprisePreferences.preferredSpeed()
    ): Pair<PlayerEngine, Long> {
        val position = failedEngine.currentPositionMs
        Log.w(TAG, "Initiating fallback engine transition from position $position ms (failed: ${failedEngine::class.simpleName})")
        // The session's PlaybackEngineOwner closes the failed engine only after a
        // replacement has initialized successfully.
        val isCellular = NetworkConnectivityObserver.isCellular
        val fallback = if (failedEngine is ExoPlayerEngine) {
            try {
                MpvPlayerEngine(context = context, initialIsCellular = isCellular).apply {
                    setPlaybackSpeed(preferredSpeed)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Fallback to MpvPlayerEngine failed, recreating ExoPlayerEngine", e)
                ExoPlayerEngine.create(context = context, preferredSpeed = preferredSpeed)
            }
        } else {
            ExoPlayerEngine.create(context = context, preferredSpeed = preferredSpeed)
        }
        return Pair(fallback, position)
    }
}

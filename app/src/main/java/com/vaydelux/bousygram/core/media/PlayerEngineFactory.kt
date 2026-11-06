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

    /** Decoder-specific audio effects are scoped to the current engine, not TDLib data. */
    private fun configureAudio(engine: PlayerEngine): PlayerEngine = engine.also {
        runCatching { it.applyAudioProcessing(EnterprisePreferences.audioProcessingProfile()) }
            .onFailure { error -> Log.w(TAG, "Audio preset unsupported on this engine", error) }
    }

    /**
     * TS has no mandatory time index; prefer libmpv's demuxer for AUTO files.
     * Explicit engine choices always win. Other containers retain Exo-first.
     */
    internal fun effectiveEngine(
        preferred: SelectedPlayerEngine,
        fileName: String?,
        mimeType: String?
    ): SelectedPlayerEngine = if (
        preferred == SelectedPlayerEngine.AUTO &&
        VideoFormatSupport.isTransportStream(fileName, mimeType)
    ) SelectedPlayerEngine.MPV else preferred

    /** Explicit LibVLC selection; AUTO and default fallbacks stay ExoPlayer/libmpv. */
    private fun createLibVlc(context: Context, preferredSpeed: Float): PlayerEngine? =
        runCatching {
            LibVlcPlayerEngine(context.applicationContext).also {
                it.setPlaybackSpeed(preferredSpeed)
            }
        }.onFailure { Log.e(TAG, "LibVLC initialization failed; using libmpv", it) }
            .getOrNull()

    fun create(
        context: Context,
        preferredEngine: SelectedPlayerEngine = EnterprisePreferences.selectedPlayerEngine(),
        preferredSpeed: Float = EnterprisePreferences.preferredSpeed(),
        fileName: String? = null,
        mimeType: String? = null
    ): PlayerEngine {
        val isCellular = NetworkConnectivityObserver.isCellular
        if (preferredEngine == SelectedPlayerEngine.LIBVLC) {
            createLibVlc(context, preferredSpeed)?.let { return configureAudio(it) }
            Log.w(TAG, "LibVLC unavailable; falling back to libmpv")
        }
        val shouldUseMpv = preferredEngine == SelectedPlayerEngine.LIBVLC ||
            effectiveEngine(preferredEngine, fileName, mimeType) == SelectedPlayerEngine.MPV
        return if (shouldUseMpv) {
            try {
                Log.i(TAG, "Creating MpvPlayerEngine (cellular=$isCellular)")
                val engine = MpvPlayerEngine(context = context, initialIsCellular = isCellular).apply {
                    setPlaybackSpeed(preferredSpeed)
                }
                com.vaydelux.bousygram.data.store.StreamHealthTelemetry.recordMpvInitSuccess()
                configureAudio(engine)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to instantiate MpvPlayerEngine, recording failure and falling back to ExoPlayerEngine", e)
                com.vaydelux.bousygram.data.store.StreamHealthTelemetry.recordMpvInitFailure()
                configureAudio(ExoPlayerEngine.create(context = context, preferredSpeed = preferredSpeed))
            }
        } else {
            Log.i(TAG, "Creating ExoPlayerEngine (default engine)")
            configureAudio(ExoPlayerEngine.create(context = context, preferredSpeed = preferredSpeed))
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
        return Pair(configureAudio(fallback), position)
    }
}

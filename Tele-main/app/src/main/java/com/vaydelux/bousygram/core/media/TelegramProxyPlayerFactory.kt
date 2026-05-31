package com.vaydelux.bousygram.core.media

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

object TelegramProxyPlayerFactory {
    private const val ALLOCATOR_BLOCK_SIZE_BYTES = 128 * 1024
    private const val MIN_BUFFER_MS = 15_000
    private const val MAX_BUFFER_MS = 60_000
    private const val PLAYBACK_START_BUFFER_MS = 500
    private const val REBUFFER_RESUME_MS = 1_000

    @OptIn(UnstableApi::class)
    fun customAllocator(): DefaultAllocator =
        DefaultAllocator(true, ALLOCATOR_BLOCK_SIZE_BYTES)

    @OptIn(UnstableApi::class)
    fun customLoadControl(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setAllocator(customAllocator())
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                PLAYBACK_START_BUFFER_MS,
                REBUFFER_RESUME_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

    @OptIn(UnstableApi::class)
    fun customRenderersFactory(context: Context): DefaultRenderersFactory {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
            .setAllowedVideoJoiningTimeMs(5_000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderersFactory.forceEnableMediaCodecAsynchronousQueueing()
        }
        return renderersFactory
    }

    @OptIn(UnstableApi::class)
    fun build(
        context: Context,
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null,
        preferredSpeed: Float = 1f
    ): ExoPlayer {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(120_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSource).apply {
            if (loadErrorHandlingPolicy != null) {
                setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            }
        }

        return ExoPlayer.Builder(context)
            .setLoadControl(customLoadControl())
            .setRenderersFactory(customRenderersFactory(context))
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                setPlaybackSpeed(preferredSpeed)
            }
    }
}

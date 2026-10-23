package com.vaydelux.bousygram.core.media

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.vaydelux.bousygram.data.store.BufferMode
import com.vaydelux.bousygram.data.store.EnterprisePreferences

import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor

object TelegramProxyPlayerFactory {
    private const val ALLOCATOR_BLOCK_SIZE_BYTES = 128 * 1024

    @OptIn(UnstableApi::class)
    fun customAllocator(): DefaultAllocator =
        DefaultAllocator(true, ALLOCATOR_BLOCK_SIZE_BYTES)

    @OptIn(UnstableApi::class)
    fun customLoadControl(
        mode: BufferMode = BufferMode.FastStart,
        isCellular: Boolean = com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.isCellular
    ): DefaultLoadControl {
        val (minBuffer, maxBuffer, startBuffer, rebuffer) = if (isCellular) {
            // Cellular: 1.5s startup runway and 3s rebuffer to prevent stutter on network jitter
            BufferWindow(15_000, 50_000, 1_500, 3_000)
        } else {
            when (mode) {
                BufferMode.FastStart -> BufferWindow(5_000, 30_000, 300, 600)
                BufferMode.Balanced -> BufferWindow(10_000, 50_000, 600, 1_000)
                BufferMode.StableNetwork -> BufferWindow(15_000, 60_000, 1_000, 2_000)
            }
        }
        return DefaultLoadControl.Builder()
            .setAllocator(customAllocator())
            .setBufferDurationsMs(minBuffer, maxBuffer, startBuffer, rebuffer)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

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
    fun customExtractorsFactory(): DefaultExtractorsFactory =
        DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(
                Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS or
                    Mp4Extractor.FLAG_READ_SEF_DATA
            )
            .setFragmentedMp4ExtractorFlags(
                FragmentedMp4Extractor.FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME or
                    FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK
            )
            .setMatroskaExtractorFlags(0)
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
            .setTsExtractorMode(TsExtractor.MODE_SINGLE_PMT)

    @OptIn(UnstableApi::class)
    fun build(
        context: Context,
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null,
        preferredSpeed: Float = 1f
    ): ExoPlayer {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(180_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSource, customExtractorsFactory()).apply {
            if (loadErrorHandlingPolicy != null) {
                setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            }
        }

        return ExoPlayer.Builder(context)
            .setLoadControl(customLoadControl(runCatching { EnterprisePreferences.bufferMode() }.getOrDefault(BufferMode.FastStart)))
            .setRenderersFactory(customRenderersFactory(context))
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                setPlaybackSpeed(preferredSpeed)
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
    }
}

private data class BufferWindow(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val startBufferMs: Int,
    val rebufferMs: Int
)

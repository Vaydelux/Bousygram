package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Engine readiness and first video output are different signals. Engines that do not expose
 * a reliable rendered-frame callback must explicitly retain the documented null fallback.
 */
class FirstFrameContractTest {
    @Test
    fun firstFrameSignalIsOptionalForLegacyEngines() {
        // Compile-time contract: the PlayerEngine interface exposes a nullable signal so
        // an engine cannot accidentally advertise STATE_READY as a rendered video frame.
        val property = PlayerEngine::firstFrameRendered
        assertNull(property.get(object : PlayerEngine {
            override val engineName = "test"
            override val playbackState = kotlinx.coroutines.flow.MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Ready)
            override val currentPositionMs = 0L
            override val durationMs = 0L
            override val bufferedPositionMs = 0L
            override val isPlaying = false
            override val playWhenReady = false
            override val activeDecoder = "test"
            override val tracks = kotlinx.coroutines.flow.MutableStateFlow<List<MediaTrackInfo>>(emptyList())
            override val videoDimensions = kotlinx.coroutines.flow.MutableStateFlow(0 to 0)
            override fun attachSurface(surface: android.view.Surface) {}
            override fun detachSurface() {}
            override fun prepare(streamUrl: String) {}
            override fun play() {}
            override fun pause() {}
            override fun seekTo(positionMs: Long) {}
            override fun setPlaybackSpeed(speed: Float) {}
            override fun setResizeMode(mode: Int) {}
            override fun selectTrack(track: MediaTrackInfo) {}
            override fun deselectTrack(type: MediaTrackInfo.TrackType) {}
            override fun updateNetworkState(isCellular: Boolean) {}
            override fun stop() {}
            override fun release() {}
        }))
    }
}

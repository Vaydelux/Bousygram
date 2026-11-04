package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatEngineSelectionTest {
    @Test fun autoPrefersCompatibilityDemuxerForTransportStreams() {
        val select = PlayerEngineFactory::effectiveEngine
        assertEquals(SelectedPlayerEngine.MPV, select(SelectedPlayerEngine.AUTO, "episode.ts", null))
        assertEquals(SelectedPlayerEngine.MPV, select(SelectedPlayerEngine.AUTO, "capture.m2ts", null))
        assertEquals(SelectedPlayerEngine.MPV, select(SelectedPlayerEngine.AUTO, "video.bin", "video/mp2t"))
        assertEquals(SelectedPlayerEngine.AUTO, select(SelectedPlayerEngine.AUTO, "movie.mkv", null))
        assertEquals(SelectedPlayerEngine.AUTO, select(SelectedPlayerEngine.AUTO, "movie.mp4", null))
    }

    @Test fun explicitlySelectedEngineIsNeverOverridden() {
        assertEquals(
            SelectedPlayerEngine.ExoPlayer,
            PlayerEngineFactory.effectiveEngine(SelectedPlayerEngine.ExoPlayer, "movie.ts", "video/mp2t")
        )
        assertEquals(
            SelectedPlayerEngine.MPV,
            PlayerEngineFactory.effectiveEngine(SelectedPlayerEngine.MPV, "movie.mkv", null)
        )
    }
}

package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOutputPolicyTest {
    private val audio = MediaTrackInfo(
        id = 0, type = MediaTrackInfo.TrackType.AUDIO, name = "Audio", isSelected = true
    )
    private val video = MediaTrackInfo(
        id = 0, type = MediaTrackInfo.TrackType.VIDEO, name = "Video", isSelected = true
    )

    @Test fun anAudioOnlyReadyTrackCanStartWithoutVideoFrame() {
        assertTrue(hasConfirmedAudioOutput(EnginePlaybackState.Ready, listOf(audio), true, true))
        assertTrue(hasInitialMediaOutput(firstFrameMs = 0L, firstAudioReadyMs = 42L))
    }

    @Test fun videoWithAudioMustStillRenderAFrame() {
        assertFalse(hasConfirmedAudioOutput(EnginePlaybackState.Ready, listOf(audio, video), true, true))
        assertFalse(hasConfirmedAudioOutput(EnginePlaybackState.Ready, listOf(audio, video), true, true, declaredAudioOnly = true))
        assertFalse(hasInitialMediaOutput(firstFrameMs = 0L, firstAudioReadyMs = 0L))
    }

    @Test fun bufferingAndUnselectedAudioAreNotSuccessfulStartup() {
        assertFalse(hasConfirmedAudioOutput(EnginePlaybackState.Buffering, listOf(audio), true, true))
        assertFalse(hasConfirmedAudioOutput(EnginePlaybackState.Ready, listOf(audio.copy(isSelected = false)), true, true))
        assertFalse(hasConfirmedAudioOutput(EnginePlaybackState.Ready, emptyList(), true, true))
    }

    @Test fun declaredAudioOnlyMayArriveBeforeSelectedTrackMetadata() {
        assertTrue(hasConfirmedAudioOutput(EnginePlaybackState.Ready, emptyList(), true, true, declaredAudioOnly = true))
        assertFalse(hasConfirmedAudioOutput(EnginePlaybackState.Buffering, emptyList(), true, true, declaredAudioOnly = true))
        assertTrue(hasConfirmedAudioOutput(EnginePlaybackState.Ready, listOf(audio), false, false))
        assertFalse(hasConfirmedAudioOutput(EnginePlaybackState.Ready, listOf(audio), false, true))
    }
}

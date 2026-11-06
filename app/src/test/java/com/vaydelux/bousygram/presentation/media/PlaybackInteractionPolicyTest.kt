package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.domain.model.VideoChatItem
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackInteractionPolicyTest {
    @Test fun newChatsAppendWithoutReorderingVisibleItems() {
        val previous = listOf(
            VideoChatItem(8L, "Zeta", false),
            VideoChatItem(4L, "Alpha", false)
        )
        val updated = listOf(
            VideoChatItem(2L, "New", false),
            VideoChatItem(4L, "Alpha Updated", false),
            VideoChatItem(8L, "Zeta", false)
        )
        val stable = stableChatCatalog(previous, updated)
        assertEquals(listOf(8L, 4L, 2L), stable.map { it.chatId })
        assertEquals("Alpha Updated", stable[1].title)
    }

    @Test fun removedChatsDisappearAndDuplicatesDoNotMultiply() {
        val previous = listOf(
            VideoChatItem(1L, "Old", false),
            VideoChatItem(2L, "Keep", false)
        )
        val incoming = listOf(
            VideoChatItem(2L, "Keep", false),
            VideoChatItem(3L, "New", false),
            VideoChatItem(3L, "New", false)
        )
        assertEquals(listOf(2L, 3L), stableChatCatalog(previous, incoming).map { it.chatId })
    }

    @Test fun activityReturnRespectsCurrentPauseCastAndFocusState() {
        assertTrue(shouldResumeAfterActivityReturn(true, false, false, false))
        assertFalse(shouldResumeAfterActivityReturn(false, false, false, false))
        assertFalse(shouldResumeAfterActivityReturn(true, true, false, false))
        assertFalse(shouldResumeAfterActivityReturn(true, false, true, false))
        assertFalse(shouldResumeAfterActivityReturn(true, false, false, true))
        assertFalse(shouldResumeAfterActivityReturn(true, true, true, true))
    }

    @Test fun temporaryLifecyclePauseResumesOnlyPreviouslyRequestedPlayback() {
        val intent = ForegroundPlaybackIntent()
        assertFalse(intent.onActivityPaused(playWhenReady = false))
        assertFalse(intent.consumeResume())
        assertTrue(intent.onActivityPaused(playWhenReady = true))
        assertTrue(intent.consumeResume())
        assertFalse(intent.consumeResume()) // no surprise second play command
        assertTrue(intent.onActivityPaused(playWhenReady = true))
        // A duplicate lifecycle pause after the engine auto-paused must not erase intent.
        assertFalse(intent.onActivityPaused(playWhenReady = false))
        assertTrue(intent.consumeResume())
    }

    @Test fun bufferingAutoplayStartsWhenReadyAndPreservesExplicitPause() {
        assertTrue(shouldAutoResumeAfterReady(isReady = true, playWhenReady = true, isPlaying = false))
        assertFalse(shouldAutoResumeAfterReady(isReady = false, playWhenReady = true, isPlaying = false))
        assertFalse(shouldAutoResumeAfterReady(isReady = true, playWhenReady = false, isPlaying = false))
        assertFalse(shouldAutoResumeAfterReady(isReady = true, playWhenReady = true, isPlaying = true))
        // Buffering isPlaying=false must still cause a pause on the first toggle.
        assertTrue(shouldPauseOnTransportToggle(isPlaying = false, playWhenReady = true))
        assertTrue(shouldPauseOnTransportToggle(isPlaying = true, playWhenReady = false))
        assertFalse(shouldPauseOnTransportToggle(isPlaying = false, playWhenReady = false))
    }

    @Test fun streamRecoveryPreservesUserPlayIntentButNeverOverridesPauseOrBackground() {
        assertTrue(shouldRestorePlayIntent(true, false, true, false, false))
        assertFalse(shouldRestorePlayIntent(true, true, true, false, false))
        assertFalse(shouldRestorePlayIntent(true, false, false, false, false))
        assertFalse(shouldRestorePlayIntent(true, false, true, true, false))
        assertFalse(shouldRestorePlayIntent(false, false, true, false, false))
        assertFalse(shouldRestorePlayIntent(true, false, true, false, true))
    }

    @Test fun transportStreamSeekRequiresRealDemuxerIndex() {
        assertFalse(canSeekTransportStream(true, false))
        assertTrue(canSeekTransportStream(true, true))
        assertTrue(canSeekTransportStream(false, false))
    }

    @Test fun backwardSeekTargetWinsOverOlderHighWaterRecoveryPosition() {
        assertEquals(30_000L, recoveryPositionMs(30_000L, 240_000L, 260_000L))
        assertEquals(260_000L, recoveryPositionMs(-1L, 240_000L, 260_000L))
        assertEquals(0L, recoveryPositionMs(-1L, -99L, -45L))
    }

    @Test fun handoffRetainsPendingBackwardSeekOverObsoleteNativePosition() {
        assertEquals(20_000L, engineHandoffPositionMs(
            pendingSeekTargetMs = 20_000L,
            enginePositionMs = 220_000L,
            lastConfirmedMs = 250_000L,
            pendingResumeMs = 260_000L
        ))
        assertEquals(0L, engineHandoffPositionMs(
            pendingSeekTargetMs = 0L,
            enginePositionMs = 220_000L,
            lastConfirmedMs = 250_000L,
            pendingResumeMs = 260_000L
        ))
        assertEquals(260_000L, engineHandoffPositionMs(
            pendingSeekTargetMs = -1L,
            enginePositionMs = 220_000L,
            lastConfirmedMs = 250_000L,
            pendingResumeMs = 260_000L
        ))
    }

    @Test fun reprepareRespectsExplicitZeroInsteadOfOldSavedResume() {
        assertEquals(0L, enginePreparationResumePositionMs(
            handoffPositionMs = 0L,
            lastConfirmedMs = 0L,
            savedResumeMs = 175_000L
        ))
        assertEquals(20_000L, enginePreparationResumePositionMs(
            handoffPositionMs = 20_000L,
            lastConfirmedMs = 250_000L,
            savedResumeMs = 175_000L
        ))
        assertEquals(250_000L, enginePreparationResumePositionMs(
            handoffPositionMs = null,
            lastConfirmedMs = 250_000L,
            savedResumeMs = 175_000L
        ))
        assertEquals(175_000L, enginePreparationResumePositionMs(
            handoffPositionMs = null,
            lastConfirmedMs = 0L,
            savedResumeMs = 175_000L
        ))
        assertEquals(0L, enginePreparationResumePositionMs(
            handoffPositionMs = null,
            lastConfirmedMs = -5L,
            savedResumeMs = -9L
        ))
    }

    @Test fun transientAudioFocusLossResumesOnlyOnGainAndWithoutUserPause() {
        val gate = AudioFocusRecoveryPolicy()
        gate.onFocusLost(transient = true, wasPlayRequested = true)
        assertTrue(gate.isSuppressed)
        gate.noteExplicitPause()
        assertFalse(gate.onFocusGained(mayResume = true))
        gate.onFocusLost(transient = true, wasPlayRequested = true)
        assertFalse(gate.onFocusGained(mayResume = false))
        gate.onFocusLost(transient = true, wasPlayRequested = true)
        assertTrue(gate.onFocusGained(mayResume = true))
        assertFalse(gate.onFocusGained(mayResume = true))
    }

    @Test fun permanentAudioFocusLossNeverAutoResumes() {
        val gate = AudioFocusRecoveryPolicy()
        gate.onFocusLost(transient = false, wasPlayRequested = true)
        assertTrue(gate.isSuppressed)
        assertFalse(gate.onFocusGained(mayResume = true))
        assertFalse(shouldRestorePlayIntent(
            isReady = true, explicitlyPaused = false, inForeground = true,
            castConnected = false, isPlaying = false, audioFocusSuppressed = true
        ))
    }

    @Test fun decoderSeekTargetMatchesKnownDurationWithoutFabricatedProgress() {
        assertEquals(90_000L, boundedPlayerSeekTarget(Long.MAX_VALUE, 90_000L))
        assertEquals(0L, boundedPlayerSeekTarget(-1_000L, 90_000L))
        assertEquals(105_000L, boundedPlayerSeekTarget(105_000L, 0L))
    }

    @Test fun seekConfirmationRequiresObservedPositionNearAcceptedTarget() {
        assertTrue(isConfirmedPlayerSeekPosition(85_000L, 90_000L))
        assertTrue(isConfirmedPlayerSeekPosition(90_100L, 90_000L))
        assertFalse(isConfirmedPlayerSeekPosition(0L, 90_000L))
        assertFalse(isConfirmedPlayerSeekPosition(Long.MAX_VALUE, 0L))
        assertFalse(isConfirmedPlayerSeekPosition(-1L, 90_000L))
        assertFalse(isConfirmedPlayerSeekPosition(5_000L, Long.MAX_VALUE))
    }

    @Test fun relativeSeekWorksWithoutDurationAndStopsAtKnownEnd() {
        assertEquals(10_000L, relativePlayerSeekTarget(0L, 10_000L, 0L))
        assertEquals(20_000L, relativePlayerSeekTarget(10_000L, 10_000L, 0L))
        assertEquals(0L, relativePlayerSeekTarget(5_000L, -10_000L, 0L))
        assertEquals(12_000L, relativePlayerSeekTarget(10_000L, 10_000L, 12_000L))
        assertEquals(Long.MAX_VALUE, relativePlayerSeekTarget(Long.MAX_VALUE, 10_000L, 0L))
        assertEquals(12_000L, relativePlayerSeekTarget(Long.MAX_VALUE, 10_000L, 12_000L))
    }
}

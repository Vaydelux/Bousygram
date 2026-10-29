package com.vaydelux.bousygram.presentation

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.vaydelux.bousygram.presentation.media.BackResult
import com.vaydelux.bousygram.presentation.media.PlayerFocusTarget
import com.vaydelux.bousygram.presentation.media.PlayerInputActions
import com.vaydelux.bousygram.presentation.media.PlayerInputController
import com.vaydelux.bousygram.presentation.media.PlayerMenuType
import com.vaydelux.bousygram.presentation.media.initialFocusTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerInputControllerTest {

    private lateinit var controller: PlayerInputController
    private lateinit var recordedActions: MutableList<String>
    private var lastFocusTarget: PlayerFocusTarget? = null
    private var lastSeekRelativeSeconds: Int = 0

    private val testActions = object : PlayerInputActions {
        override fun showHud(target: PlayerFocusTarget) {
            recordedActions.add("showHud")
            lastFocusTarget = target
        }

        override fun hideHud() {
            recordedActions.add("hideHud")
        }

        override fun openMenu(menu: PlayerMenuType) {
            recordedActions.add("openMenu:$menu")
        }

        override fun closeMenu() {
            recordedActions.add("closeMenu")
        }

        override fun togglePlayPause() {
            recordedActions.add("togglePlayPause")
        }

        override fun play() {
            recordedActions.add("play")
        }

        override fun pause() {
            recordedActions.add("pause")
        }

        override fun seekRelative(seconds: Int) {
            recordedActions.add("seekRelative:$seconds")
            lastSeekRelativeSeconds = seconds
        }

        override fun previewSeekRelative(seconds: Int) {
            recordedActions.add("previewSeekRelative:$seconds")
            lastSeekRelativeSeconds = seconds
        }

        override fun confirmSeekPreview() {
            recordedActions.add("confirmSeekPreview")
        }

        override fun cancelSeekPreview() {
            recordedActions.add("cancelSeekPreview")
        }

        override fun skipNext() {
            recordedActions.add("skipNext")
        }

        override fun skipPrevious() {
            recordedActions.add("skipPrevious")
        }

        override fun exitPlayer() {
            recordedActions.add("exitPlayer")
        }

        override fun cancelAutoNext() {
            recordedActions.add("cancelAutoNext")
        }

        override fun playNextNow() {
            recordedActions.add("playNextNow")
        }
    }

    private var currentTimeMs: Long = 100_000L

    @Before
    fun setUp() {
        currentTimeMs = 100_000L
        controller = PlayerInputController(clock = { currentTimeMs })
        recordedActions = mutableListOf()
        lastFocusTarget = null
        lastSeekRelativeSeconds = 0
    }

    private fun sendKey(keyCode: Int, action: Int = KeyEvent.ACTION_DOWN): Boolean {
        return controller.handleRawKeyEvent(action, keyCode, testActions)
    }

    @Test
    fun controlsHidden_dpadCenter_showsControlsOnly_doesNotTogglePlayPause() {
        controller.isHudVisible = false

        val handled = sendKey(KeyEvent.KEYCODE_DPAD_CENTER)

        assertTrue(handled)
        assertTrue(controller.isHudVisible)
        assertEquals(listOf("showHud"), recordedActions)
        assertEquals(PlayerFocusTarget.BUTTON_PLAY_PAUSE, lastFocusTarget)
        assertFalse(recordedActions.contains("togglePlayPause"))
    }

    @Test
    fun controlsHidden_dpadUp_showsControlsOnlyWithSeekBarFocus() {
        controller.isHudVisible = false

        val handled = sendKey(KeyEvent.KEYCODE_DPAD_UP)

        assertTrue(handled)
        assertTrue(controller.isHudVisible)
        assertEquals(listOf("showHud"), recordedActions)
        assertEquals(PlayerFocusTarget.SEEK_BAR, lastFocusTarget)
        assertFalse(recordedActions.contains("openMenu:TRACKS"))
    }

    @Test
    fun controlsHidden_dpadDown_showsControlsOnly_neverSkipsTrack() {
        controller.isHudVisible = false

        val handled = sendKey(KeyEvent.KEYCODE_DPAD_DOWN)

        assertTrue(handled)
        assertTrue(controller.isHudVisible)
        assertEquals(listOf("showHud"), recordedActions)
        assertEquals(PlayerFocusTarget.BUTTON_PLAY_PAUSE, lastFocusTarget)
        assertFalse(recordedActions.contains("skipNext"))
    }

    @Test
    fun controlsHidden_dpadLeft_relativeSeekBackward() {
        controller.isHudVisible = false

        val handled = sendKey(KeyEvent.KEYCODE_DPAD_LEFT)

        assertTrue(handled)
        assertEquals(listOf("previewSeekRelative:-10"), recordedActions)
        assertEquals(-10, lastSeekRelativeSeconds)
    }

    @Test
    fun controlsHidden_dpadRight_relativeSeekForwardWithAcceleration() {
        controller.isHudVisible = false

        val handled1 = sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(handled1)
        assertEquals(10, lastSeekRelativeSeconds)

        // Rapid second press within acceleration window (200ms later, > 50ms debounce, < 1200ms window)
        currentTimeMs += 200L
        val handled2 = sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(handled2)
        assertEquals(30, lastSeekRelativeSeconds)
    }

    @Test
    fun controlsHidden_leftAndRightNeverSkipAndDirectionChangeResetsAcceleration() {
        controller.isHudVisible = false

        assertTrue(sendKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        currentTimeMs += 200L
        assertTrue(sendKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(30, lastSeekRelativeSeconds)

        currentTimeMs += 200L
        assertTrue(sendKey(KeyEvent.KEYCODE_DPAD_LEFT))

        assertEquals(-10, lastSeekRelativeSeconds)
        assertFalse(recordedActions.contains("skipNext"))
        assertFalse(recordedActions.contains("skipPrevious"))
    }

    @Test
    fun controlsVisible_leftAndRightAreLeftToFocusedControlAndNeverSkip() {
        controller.isHudVisible = true

        assertFalse(sendKey(KeyEvent.KEYCODE_DPAD_LEFT))
        currentTimeMs += 100L
        assertFalse(sendKey(KeyEvent.KEYCODE_DPAD_RIGHT))

        assertFalse(recordedActions.contains("skipNext"))
        assertFalse(recordedActions.contains("skipPrevious"))
        assertFalse(recordedActions.any { it.startsWith("seekRelative:") })
    }

    @Test
    fun hiddenSeekPreview_okCommitsOnceAndBackCancelsWithoutExiting() {
        controller.isHudVisible = false

        assertTrue(sendKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        currentTimeMs += 100L
        assertTrue(sendKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(1, recordedActions.count { it == "confirmSeekPreview" })
        assertFalse(recordedActions.any { it.startsWith("seekRelative:") })

        currentTimeMs += 100L
        assertTrue(sendKey(KeyEvent.KEYCODE_DPAD_LEFT))
        currentTimeMs += 100L
        assertTrue(sendKey(KeyEvent.KEYCODE_BACK))
        assertTrue(recordedActions.contains("cancelSeekPreview"))
        assertFalse(recordedActions.contains("exitPlayer"))
    }

    @Test
    fun mediaKeys_actImmediatelyInAllStates() {
        controller.isHudVisible = false

        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        assertTrue(recordedActions.contains("togglePlayPause"))

        currentTimeMs += 100L
        sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        assertTrue(recordedActions.contains("skipNext"))

        currentTimeMs += 100L
        sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        assertTrue(recordedActions.contains("skipPrevious"))
    }

    @Test
    fun dedicatedMediaKeysAreIdempotentInsteadOfToggling() {
        controller.isHudVisible = false
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PLAY))
        currentTimeMs += 60
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PLAY))
        currentTimeMs += 60
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
        currentTimeMs += 60
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(listOf("play", "play", "pause", "pause"), recordedActions)
        assertFalse(recordedActions.contains("togglePlayPause"))
    }

    @Test
    fun differentRemoteKeysAreNotMistakenForBounce() {
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
        currentTimeMs += 10
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PLAY))
        currentTimeMs += 10
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(listOf("pause", "play", "pause"), recordedActions)
    }

    @Test
    fun sameRemoteKeyRapidBounceIsStillDebounced() {
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PLAY))
        currentTimeMs += 10
        assertTrue(sendKey(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(listOf("play"), recordedActions)
    }

    @Test
    fun controlsHidden_backKey_exitsPlayer() {
        controller.isHudVisible = false

        val handled = sendKey(KeyEvent.KEYCODE_BACK)

        assertTrue(handled)
        assertEquals(listOf("exitPlayer"), recordedActions)
    }

    @Test
    fun controlsVisible_backKey_hidesControlsOnly() {
        controller.isHudVisible = true

        val handled = sendKey(KeyEvent.KEYCODE_BACK)

        assertTrue(handled)
        assertFalse(controller.isHudVisible)
        assertEquals(listOf("hideHud"), recordedActions)
        assertFalse(recordedActions.contains("exitPlayer"))
    }

    @Test
    fun menuOpen_backKey_closesMenuAndRestoresFocus_neverExitsPlayer() {
        controller.isHudVisible = true
        controller.activeMenu = PlayerMenuType.TRACKS

        val handled = sendKey(KeyEvent.KEYCODE_BACK)

        assertTrue(handled)
        assertFalse(controller.isMenuOpen)
        assertTrue(recordedActions.contains("closeMenu"))
        assertTrue(recordedActions.contains("showHud"))
        assertEquals(PlayerFocusTarget.BUTTON_SUBTITLES, lastFocusTarget)
        assertFalse(recordedActions.contains("exitPlayer"))
    }

    @Test
    fun autoNextCard_backKey_cancelsCardAndLeavesControlsVisible() {
        controller.isAutoNextActive = true

        val handled = sendKey(KeyEvent.KEYCODE_BACK)

        assertTrue(handled)
        assertFalse(controller.isAutoNextActive)
        assertTrue(recordedActions.contains("cancelAutoNext"))
        assertTrue(recordedActions.contains("showHud"))
        assertEquals(PlayerFocusTarget.BUTTON_PLAY_PAUSE, lastFocusTarget)
        assertFalse(recordedActions.contains("exitPlayer"))
    }

    @Test
    fun autoNextCard_dpadCenter_playsNextImmediately() {
        controller.isAutoNextActive = true

        val handled = sendKey(KeyEvent.KEYCODE_DPAD_CENTER)

        assertTrue(handled)
        assertFalse(controller.isAutoNextActive)
        assertTrue(recordedActions.contains("playNextNow"))
    }

    @Test
    fun debounce_ignoresRapidKeyBounceWithin50ms() {
        controller.isHudVisible = false

        val handled1 = sendKey(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(handled1)

        // Instant duplicate bounce event (<10ms)
        currentTimeMs += 10L
        val handled2 = sendKey(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(handled2) // Consumed/debounced

        // showHud should have been called only ONCE
        assertEquals(listOf("showHud"), recordedActions)
    }

    @Test
    fun handleBack_priorityOrdering() {
        // 1. AutoNext active -> cancels auto-next and returns CANCELLED_AUTO_NEXT
        controller.isAutoNextActive = true
        assertEquals(BackResult.CANCELLED_AUTO_NEXT, controller.handleBack(testActions))
        assertFalse(controller.isAutoNextActive)
        assertTrue(recordedActions.contains("cancelAutoNext"))
        recordedActions.clear()

        // 2. Seek preview active -> cancels preview and returns CANCELLED_SEEK_PREVIEW
        controller.isSeekPreviewActive = true
        assertEquals(BackResult.CANCELLED_SEEK_PREVIEW, controller.handleBack(testActions))
        assertFalse(controller.isSeekPreviewActive)
        assertTrue(recordedActions.contains("cancelSeekPreview"))
        recordedActions.clear()

        // 3. Menu open -> closes menu, restores opener focus, returns CLOSED_MENU
        controller.isHudVisible = true
        controller.activeMenu = PlayerMenuType.TRACKS
        assertEquals(BackResult.CLOSED_MENU, controller.handleBack(testActions))
        assertFalse(controller.isMenuOpen)
        assertTrue(recordedActions.contains("closeMenu"))
        assertEquals(PlayerFocusTarget.BUTTON_SUBTITLES, lastFocusTarget)
        recordedActions.clear()

        // 4. HUD visible -> hides HUD and returns HID_HUD
        controller.isHudVisible = true
        assertEquals(BackResult.HID_HUD, controller.handleBack(testActions))
        assertFalse(controller.isHudVisible)
        assertTrue(recordedActions.contains("hideHud"))
        assertFalse(recordedActions.contains("exitPlayer"))
        recordedActions.clear()

        // 5. HUD hidden -> exits player and returns EXITED_PLAYER
        controller.isHudVisible = false
        assertEquals(BackResult.EXITED_PLAYER, controller.handleBack(testActions))
        assertTrue(recordedActions.contains("exitPlayer"))
    }

    @Test
    fun initialFocusTargetPolicy() {
        assertEquals(
            PlayerFocusTarget.BUTTON_RETRY,
            initialFocusTarget(hudVisible = true, hasError = true)
        )
        assertEquals(
            PlayerFocusTarget.BUTTON_PLAY_PAUSE,
            initialFocusTarget(hudVisible = true, hasError = false)
        )
        assertEquals(
            PlayerFocusTarget.SURFACE,
            initialFocusTarget(hudVisible = false, hasError = false)
        )
    }

    @Test
    fun seekPreviewAccelerationReset() {
        controller.isHudVisible = false
        // Perform multiple fast right seeks to increase step index
        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        currentTimeMs += 100L
        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(controller.isSeekPreviewActive)

        // Reset seek preview state
        controller.resetSeekPreviewState()
        // Next seek should start fresh from first step interval
        recordedActions.clear()
        currentTimeMs += 2000L
        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(listOf("previewSeekRelative:10"), recordedActions)
    }
}

package com.vaydelux.bousygram.presentation

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.vaydelux.bousygram.presentation.media.PlayerFocusTarget
import com.vaydelux.bousygram.presentation.media.PlayerInputActions
import com.vaydelux.bousygram.presentation.media.PlayerInputController
import com.vaydelux.bousygram.presentation.media.PlayerMenuType
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

        override fun seekRelative(seconds: Int) {
            recordedActions.add("seekRelative:$seconds")
            lastSeekRelativeSeconds = seconds
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
        assertEquals(listOf("seekRelative:-10"), recordedActions)
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
}

package com.vaydelux.bousygram.presentation.media

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

enum class PlayerMenuType {
    NONE,
    TRACKS,
    EPISODES,
    SPEED,
    ASPECT
}

enum class PlayerFocusTarget {
    SURFACE,
    SEEK_BAR,
    BUTTON_PLAY_PAUSE,
    BUTTON_PREVIOUS,
    BUTTON_NEXT,
    BUTTON_AUDIO,
    BUTTON_SUBTITLES,
    BUTTON_SPEED,
    BUTTON_ASPECT,
    BUTTON_EPISODES,
    BUTTON_RETRY,
    MENU_CONTENT,
    AUTO_NEXT_PLAY_NOW
}

enum class BackResult {
    CANCELLED_AUTO_NEXT,
    CANCELLED_SEEK_PREVIEW,
    CLOSED_MENU,
    HID_HUD,
    EXITED_PLAYER
}

fun initialFocusTarget(hudVisible: Boolean, hasError: Boolean): PlayerFocusTarget = when {
    hasError -> PlayerFocusTarget.BUTTON_RETRY
    hudVisible -> PlayerFocusTarget.BUTTON_PLAY_PAUSE
    else -> PlayerFocusTarget.SURFACE
}

interface PlayerInputActions {
    fun showHud(target: PlayerFocusTarget)
    fun hideHud()
    fun openMenu(menu: PlayerMenuType)
    fun closeMenu()
    fun togglePlayPause()
    fun play()
    fun pause()
    fun seekRelative(seconds: Int)
    fun previewSeekRelative(seconds: Int)
    fun confirmSeekPreview()
    fun cancelSeekPreview()
    fun skipNext()
    fun skipPrevious()
    fun exitPlayer()
    fun cancelAutoNext()
    fun playNextNow()
}

/**
 * Central input router for all TV remote, D-pad, and media key events in the Player.
 * Enforces the exact Behavior Specification Table:
 * 1. Controls Hidden: First D-pad press ONLY shows controls (OK -> Play/Pause, Up -> Seek Bar, Down -> Play/Pause).
 * 2. Media Keys: Act immediately regardless of HUD state and show brief indicator.
 * 3. Left/Right while hidden: Visible seek preview overlay with accelerating steps (10s -> 30s -> 60s -> 120s).
 * 4. Controls Visible: Up/Down moves strictly between Seek Bar and Button row; never triggers action or menu.
 * 5. Menus: Back closes menu and restores button focus; auto-hide timer is paused while menu is open.
 * 6. Back: Closes menu -> Hides controls -> Exits player. Never skips straight out.
 * 7. Auto-Next: 5-second countdown card, "Play Now" focused. Back cancels card and leaves finished video paused with controls visible.
 * 8. Remote variations: Debounces key bounce (<50ms) and handles remotes without media keys.
 */
class PlayerInputController(
    private val clock: () -> Long = { SystemClock.uptimeMillis() }
) {

    companion object {
        private const val DEBOUNCE_THRESHOLD_MS = 50L
        private const val SEEK_ACCELERATION_WINDOW_MS = 1_200L
        val SEEK_STEPS = listOf(10, 30, 60, 120)
    }

    var isHudVisible: Boolean = true
    var activeMenu: PlayerMenuType = PlayerMenuType.NONE
    val isMenuOpen: Boolean get() = activeMenu != PlayerMenuType.NONE
    var isAutoNextActive: Boolean = false
    var isSeekPreviewActive: Boolean = false
        internal set
    var currentFocusRow: Int = 1 // 0: Seek Bar, 1: Button Row

    private var lastKeyDownTimeMs: Long = -1_000L
    private var lastKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN
    private var lastSeekKeyTimeMs: Long = -10_000L
    private var lastSeekDirection: Int = 0
    private var seekStepIndex: Int = 0

    /**
     * Routes a Compose key event. Returns true if handled/consumed.
     */
    fun handleKeyEvent(
        event: androidx.compose.ui.input.key.KeyEvent,
        actions: PlayerInputActions
    ): Boolean {
        val native = event.nativeKeyEvent
        return handleRawKeyEvent(native.action, native.keyCode, actions)
    }

    /**
     * Routes raw Android key events by action and keycode.
     */
    fun handleRawKeyEvent(
        action: Int,
        nativeKeyCode: Int,
        actions: PlayerInputActions
    ): Boolean {
        if (action != KeyEvent.ACTION_DOWN) return false

        val now = clock()
        if (nativeKeyCode == lastKeyCode &&
            lastKeyDownTimeMs >= 0L &&
            (now - lastKeyDownTimeMs) in 0 until DEBOUNCE_THRESHOLD_MS
        ) {
            return true // Ignore repeated presses of this key, not a different remote action.
        }
        lastKeyDownTimeMs = now
        lastKeyCode = nativeKeyCode

        // 1. Dedicated Media Keys (Act immediately in all states)
        when (nativeKeyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                actions.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                actions.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                actions.togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                actions.skipNext()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                actions.skipPrevious()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                actions.exitPlayer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                cancelSeekPreview(actions)
                actions.seekRelative(30)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                cancelSeekPreview(actions)
                actions.seekRelative(-30)
                return true
            }
        }

        // 2. Auto-Next Countdown Card Active
        if (isAutoNextActive) {
            when (nativeKeyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    isAutoNextActive = false
                    actions.cancelAutoNext()
                    isHudVisible = true
                    actions.showHud(PlayerFocusTarget.BUTTON_PLAY_PAUSE)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    isAutoNextActive = false
                    actions.playNextNow()
                    return true
                }
            }
            return true // Consume other D-pad keys during auto-next
        }

        // 3. Submenu Open (Subtitles, Audio, Speed, Episodes)
        if (isMenuOpen) {
            if (nativeKeyCode == KeyEvent.KEYCODE_BACK || nativeKeyCode == KeyEvent.KEYCODE_MENU) {
                val previousMenu = activeMenu
                activeMenu = PlayerMenuType.NONE
                actions.closeMenu()
                val returnTarget = when (previousMenu) {
                    PlayerMenuType.TRACKS -> PlayerFocusTarget.BUTTON_SUBTITLES
                    PlayerMenuType.SPEED -> PlayerFocusTarget.BUTTON_SPEED
                    PlayerMenuType.EPISODES -> PlayerFocusTarget.BUTTON_EPISODES
                    PlayerMenuType.ASPECT -> PlayerFocusTarget.BUTTON_ASPECT
                    else -> PlayerFocusTarget.BUTTON_PLAY_PAUSE
                }
                actions.showHud(returnTarget)
                return true
            }
            // Allow D-pad keys to navigate inside the menu
            return false
        }

        // 4. Controls Hidden State
        if (!isHudVisible) {
            when (nativeKeyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (isSeekPreviewActive) {
                        resetSeekPreviewState()
                        actions.confirmSeekPreview()
                        return true
                    }
                    // First press only shows controls (does not toggle play/pause).
                    isHudVisible = true
                    actions.showHud(PlayerFocusTarget.BUTTON_PLAY_PAUSE)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cancelSeekPreview(actions)
                    // Show controls with focus on Seek Bar
                    isHudVisible = true
                    currentFocusRow = 0
                    actions.showHud(PlayerFocusTarget.SEEK_BAR)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cancelSeekPreview(actions)
                    // Show controls with focus on Button Row (never skip video!)
                    isHudVisible = true
                    currentFocusRow = 1
                    actions.showHud(PlayerFocusTarget.BUTTON_PLAY_PAUSE)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    // Preview only. OK commits a single seek; Back cancels it.
                    val step = calculateAcceleratingSeekStep(isForward = false, now = now)
                    isSeekPreviewActive = true
                    actions.previewSeekRelative(-step)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Preview only. OK commits a single seek; Back cancels it.
                    val step = calculateAcceleratingSeekStep(isForward = true, now = now)
                    isSeekPreviewActive = true
                    actions.previewSeekRelative(step)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    handleBack(actions)
                    return true
                }
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_INFO,
                KeyEvent.KEYCODE_CAPTIONS -> {
                    isHudVisible = true
                    activeMenu = PlayerMenuType.TRACKS
                    actions.openMenu(PlayerMenuType.TRACKS)
                    return true
                }
            }
            return false
        }

        // 5. Controls Visible State
        when (nativeKeyCode) {
            KeyEvent.KEYCODE_BACK -> {
                handleBack(actions)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (currentFocusRow == 1) {
                    currentFocusRow = 0
                    actions.showHud(PlayerFocusTarget.SEEK_BAR)
                }
                return true // Never open menu or exit row!
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (currentFocusRow == 0) {
                    currentFocusRow = 1
                    actions.showHud(PlayerFocusTarget.BUTTON_PLAY_PAUSE)
                }
                return true // Never skip track!
            }
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_CAPTIONS -> {
                activeMenu = PlayerMenuType.TRACKS
                actions.openMenu(PlayerMenuType.TRACKS)
                return true
            }
        }

        return false
    }

    fun resetSeekPreviewState() {
        isSeekPreviewActive = false
        seekStepIndex = 0
        lastSeekDirection = 0
        lastSeekKeyTimeMs = -10_000L
    }

    private fun cancelSeekPreview(actions: PlayerInputActions) {
        if (isSeekPreviewActive) {
            resetSeekPreviewState()
            actions.cancelSeekPreview()
        }
    }

    fun handleBack(actions: PlayerInputActions): BackResult {
        return when {
            isAutoNextActive -> {
                isAutoNextActive = false
                actions.cancelAutoNext()
                isHudVisible = true
                actions.showHud(PlayerFocusTarget.BUTTON_PLAY_PAUSE)
                BackResult.CANCELLED_AUTO_NEXT
            }
            isSeekPreviewActive -> {
                cancelSeekPreview(actions)
                BackResult.CANCELLED_SEEK_PREVIEW
            }
            isMenuOpen -> {
                val previousMenu = activeMenu
                activeMenu = PlayerMenuType.NONE
                actions.closeMenu()
                val returnTarget = when (previousMenu) {
                    PlayerMenuType.TRACKS -> PlayerFocusTarget.BUTTON_SUBTITLES
                    PlayerMenuType.SPEED -> PlayerFocusTarget.BUTTON_SPEED
                    PlayerMenuType.EPISODES -> PlayerFocusTarget.BUTTON_EPISODES
                    PlayerMenuType.ASPECT -> PlayerFocusTarget.BUTTON_ASPECT
                    else -> PlayerFocusTarget.BUTTON_PLAY_PAUSE
                }
                actions.showHud(returnTarget)
                BackResult.CLOSED_MENU
            }
            isHudVisible -> {
                isHudVisible = false
                actions.hideHud()
                BackResult.HID_HUD
            }
            else -> {
                actions.exitPlayer()
                BackResult.EXITED_PLAYER
            }
        }
    }

    private fun calculateAcceleratingSeekStep(isForward: Boolean, now: Long): Int {
        val direction = if (isForward) 1 else -1
        if (lastSeekDirection == direction &&
            lastSeekKeyTimeMs >= 0L &&
            now - lastSeekKeyTimeMs < SEEK_ACCELERATION_WINDOW_MS
        ) {
            seekStepIndex = (seekStepIndex + 1).coerceAtMost(SEEK_STEPS.lastIndex)
        } else {
            seekStepIndex = 0
        }
        lastSeekDirection = direction
        lastSeekKeyTimeMs = now
        return SEEK_STEPS[seekStepIndex]
    }
}

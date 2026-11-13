package com.vaydelux.bousygram.presentation.extras

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.mediaforge.webbridge.MediaForgeWebBridge
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline, real Android input-dispatch tests; no external iframe or login needed. */
@RunWith(AndroidJUnit4::class)
class MediaForgeVirtualMouseInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultDpadModeHasNoPointerAndMenuRestoresNativeControls() {
        compose.setContent {
            BousygramTheme {
                var cursorEnabled by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    ExtrasTvCursorModeButton(
                        cursorEnabled = cursorEnabled,
                        onToggle = { cursorEnabled = !cursorEnabled }
                    )
                    if (cursorEnabled) {
                        ExtrasVirtualCursor(
                            target = null,
                            widthPx = 360,
                            heightPx = 240,
                            xPx = 90,
                            yPx = 90,
                            onDelta = { _, _ -> },
                            onConfirm = {},
                            isActive = true,
                            tag = "extras-cursor-mode-test",
                            onMenu = { cursorEnabled = false }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Use cursor").assertExists()
        compose.onNodeWithTag("extras-cursor-mode-test").assertDoesNotExist()
        compose.onNodeWithTag("extras-virtual-mouse").performClick()
        compose.onNodeWithText("Use D-pad").assertExists()
        compose.onNodeWithTag("extras-cursor-pointer").assertExists()
        val cursor = compose.onNodeWithTag("extras-cursor-mode-test")
        cursor.performSemanticsAction(SemanticsActions.RequestFocus)
        cursor.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("extras-cursor-mode-test").assertDoesNotExist()
        compose.onNodeWithText("Use cursor").assertExists()
    }

    @Test fun fullscreenRecoveryControlsSwitchBetweenDpadAndPointerWithoutExiting() {
        var fullscreenExited = 0
        compose.setContent {
            BousygramTheme {
                var cursorEnabled by remember { mutableStateOf(false) }
                ExtrasFullscreenRecoveryControls(
                    isAnime = true,
                    onExitFullscreen = { fullscreenExited++ },
                    onChooseAnimeServer = {},
                    cursorEnabled = cursorEnabled,
                    onToggleCursor = { cursorEnabled = !cursorEnabled }
                )
            }
        }
        compose.onNodeWithTag("extras-fullscreen-cursor-mode").assertExists()
        compose.onNodeWithText("Use cursor").assertExists()
        compose.onNodeWithTag("extras-fullscreen-cursor-mode").performClick()
        compose.onNodeWithText("Use D-pad").assertExists()
        compose.runOnIdle { assertEquals(0, fullscreenExited) }
        compose.onNodeWithTag("extras-fullscreen-cursor-mode").performClick()
        compose.onNodeWithText("Use cursor").assertExists()
    }

    @Test fun dpadMovesPointerAndOkDispatchesOneCompleteTap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val received = mutableListOf<Triple<Int, Float, Float>>()
        val target = object : View(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                received.add(Triple(event.actionMasked, event.x, event.y))
                return true
            }
        }
        val bridge = MediaForgeWebBridge(onMediaResource = {})
        try {
            compose.setContent {
                BousygramTheme {
                    var x by remember { mutableIntStateOf(100) }
                    var y by remember { mutableIntStateOf(100) }
                    Box(Modifier.fillMaxSize()) {
                        ExtrasVirtualCursor(
                            target = target,
                            widthPx = 360,
                            heightPx = 240,
                            xPx = x,
                            yPx = y,
                            onDelta = { dx, dy ->
                                x = (x + dx).coerceIn(0, 359)
                                y = (y + dy).coerceIn(0, 239)
                            },
                            onConfirm = { bridge.click(target, x.toFloat(), y.toFloat()) },
                            isActive = true,
                            tag = "mediaforge-pointer-test"
                        )
                        Text("Pointer: $x,$y")
                    }
                }
            }
            val cursor = compose.onNodeWithTag("mediaforge-pointer-test")
            cursor.assertExists()
            cursor.performSemanticsAction(SemanticsActions.RequestFocus)
            cursor.performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionDown)
            }
            compose.onNodeWithText("Pointer: 118,118").assertExists()
            cursor.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.runOnIdle {
                assertEquals(2, received.size)
                assertEquals(MotionEvent.ACTION_DOWN, received[0].first)
                assertEquals(MotionEvent.ACTION_UP, received[1].first)
                received.forEach {
                    assertEquals(118f, it.second, 0.01f)
                    assertEquals(118f, it.third, 0.01f)
                }
            }
        } finally {
            compose.runOnIdle { bridge.close() }
        }
    }

    @Test fun remoteMenuKeyCanEscapeFullscreenVirtualCursor() {
        var returnedToServers = 0
        compose.setContent {
            BousygramTheme {
                ExtrasVirtualCursor(
                    target = null,
                    widthPx = 360,
                    heightPx = 240,
                    xPx = 100,
                    yPx = 100,
                    onDelta = { _, _ -> },
                    onConfirm = {},
                    isActive = true,
                    tag = "mediaforge-pointer-menu",
                    onMenu = { returnedToServers += 1 }
                )
            }
        }
        val cursor = compose.onNodeWithTag("mediaforge-pointer-menu")
        cursor.assertExists()
        cursor.performSemanticsAction(SemanticsActions.RequestFocus)
        cursor.performKeyInput { pressKey(Key.Menu) }
        compose.runOnIdle { assertEquals(1, returnedToServers) }
    }

    @Test fun pointerNeverMovesOutsideWebViewBounds() {
        val bridge = MediaForgeWebBridge(onMediaResource = {})
        try {
            compose.setContent {
                BousygramTheme {
                    var x by remember { mutableIntStateOf(350) }
                    var y by remember { mutableIntStateOf(230) }
                    Box(Modifier.fillMaxSize()) {
                        ExtrasVirtualCursor(
                            target = null,
                            widthPx = 360,
                            heightPx = 240,
                            xPx = x,
                            yPx = y,
                            onDelta = { dx, dy ->
                                x = (x + dx).coerceIn(0, 359)
                                y = (y + dy).coerceIn(0, 239)
                            },
                            onConfirm = {},
                            isActive = true,
                            tag = "mediaforge-pointer-test"
                        )
                        Text("Pointer: $x,$y")
                    }
                }
            }
            val cursor = compose.onNodeWithTag("mediaforge-pointer-test")
            cursor.performSemanticsAction(SemanticsActions.RequestFocus)
            cursor.performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionDown)
            }
            compose.onNodeWithText("Pointer: 359,239").assertExists()
        } finally {
            compose.runOnIdle { bridge.close() }
        }
    }
}

package com.vaydelux.bousygram.presentation.extras

import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.mediaforge.webbridge.MediaForgeWebBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android WebView settings, independent of CineSrc/TMDB and Telegram login.
 * The reserved .invalid hostname never represents a live playback provider.
 */
@RunWith(AndroidJUnit4::class)
class MediaForgeWebBridgeInstrumentedTest {
    private val offlineEmbed = "https://mediaforge.example.invalid/embed/movie/42"

    @Test fun webBridgeAppliesSandboxedSettingsAndDisablesThirdPartyCookies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = MediaForgeWebBridge(onMediaResource = {})
            val view = bridge.create(instrumentation.targetContext, offlineEmbed)
            try {
                val settings = view.settings
                assertTrue(settings.javaScriptEnabled)
                assertTrue(settings.domStorageEnabled)
                assertTrue(settings.mediaPlaybackRequiresUserGesture)
                assertFalse(settings.allowFileAccess)
                assertFalse(settings.allowContentAccess)
                assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.mixedContentMode)
                assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(view))
            } finally {
                bridge.close()
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }
        }
    }

    @Test fun simulatedRendererLossDiscardsWebViewAndSignalsManualRecovery() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var signals = 0
            val bridge = MediaForgeWebBridge(
                onMediaResource = {},
                onRendererGone = { signals++ }
            )
            val view = bridge.create(instrumentation.targetContext, offlineEmbed)
            try {
                // A simulated callback avoids destabilizing the shared
                // instrumentation renderer with chrome://crash.
                val detail = object : RenderProcessGoneDetail() {
                    override fun didCrash(): Boolean = false
                    override fun rendererPriorityAtExit(): Int = WebView.RENDERER_PRIORITY_BOUND
                }
                assertTrue(view.webViewClient.onRenderProcessGone(view, detail))
                assertTrue(bridge.rendererGone(view))
                assertEquals(1, signals)
            } finally {
                bridge.close()
            }
        }
    }

    @Test fun webBridgeRejectsUnsafeTopLevelEmbed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = MediaForgeWebBridge(onMediaResource = {})
            try {
                val blocked = listOf(
                    "http://mediaforge.example.invalid/embed/movie/42",
                    "https://127.0.0.1/embed/movie/42",
                    "https://device.local./embed/movie/42",
                    "https://router.localdomain/embed/movie/42",
                    "https://nas.lan/embed/movie/42",
                    "https://mediaforge.example.invalid./embed/movie/42",
                    "file:///data/local/tmp/embed.html",
                    "https://user:secret@mediaforge.example.invalid/embed/movie/42"
                )
                blocked.forEach { url ->
                    try {
                        bridge.create(instrumentation.targetContext, url).destroy()
                        fail("Unsafe source should be refused before creating WebView")
                    } catch (_: IllegalArgumentException) {
                        // Expected: invalid scheme/host/userinfo is never opened.
                    }
                }
            } finally {
                bridge.close()
            }
        }
    }
}

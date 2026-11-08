package com.vaydelux.bousygram.presentation.extras

import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.mediaforge.webbridge.MediaForgeWebBridge
import com.vaydelux.mediaforge.webbridge.MediaForgeWebRequestPolicy
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

    @Test fun closedBridgeCannotRecreateBrowserOrDispatchLateRemoteClicks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = MediaForgeWebBridge(onMediaResource = {})
            var touchEvents = 0
            val target = object : View(instrumentation.targetContext) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    touchEvents++
                    return true
                }
            }
            bridge.click(target, 10f, 10f)
            assertEquals(2, touchEvents) // ACTION_DOWN followed by ACTION_UP.
            bridge.close()
            bridge.click(target, 10f, 10f)
            assertEquals(2, touchEvents)
            try {
                val unexpected = bridge.create(instrumentation.targetContext, offlineEmbed)
                unexpected.destroy()
                fail("Disposed browser bridge must not allocate a new WebView")
            } catch (_: IllegalStateException) {
                // Required: native playback and WebView renderer recovery own a new bridge.
            }
        }
    }

    @Test fun webBridgeBlocksUnsafeNetworkSubresourcesButLeavesBrowserSchemesUntouched() {
        for (url in listOf(
            "http://example.org/insecure.mp4",
            "https://127.0.0.1/private",
            "https://nas.local/private",
            "https://router.localdomain/private",
            "https://nas.lan/private",
            "https://gateway.home/private",
            "https://printer.internal/private",
            "https://local.example.org./private",
            "https://[::1]/private",
            "https://user:password@cdn.example.org/segment.mp4",
            "https://cdn.example.org:8443/segment.mp4"
        )) {
            assertTrue("WebView must refuse network request: $url",
                MediaForgeWebRequestPolicy.blockNetworkSubresource(url))
        }
        for (url in listOf(
            "https://cdn.example.org/video.mp4?token=fixture",
            "https://mediaforge.example.invalid/embed/movie/42",
            "https://subtitles.example.org/en.vtt",
            "blob:https://cdn.example.org/fixture",
            "data:text/plain,fixture",
            "about:blank"
        )) {
            assertFalse("WebView should not intercept permitted/browser URLs: $url",
                MediaForgeWebRequestPolicy.blockNetworkSubresource(url))
        }
    }

    @Test fun webViewInterceptorReturnsForbiddenForLocalNetworkSubresources() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = MediaForgeWebBridge(onMediaResource = {})
            val view = bridge.create(instrumentation.targetContext, offlineEmbed)
            try {
                fun fakeRequest(value: String) = object : WebResourceRequest {
                    override fun getUrl(): Uri = Uri.parse(value)
                    override fun isForMainFrame(): Boolean = false
                    override fun isRedirect(): Boolean = false
                    override fun hasGesture(): Boolean = false
                    override fun getMethod(): String = "GET"
                    override fun getRequestHeaders(): Map<String, String> = emptyMap()
                }

                val unsafe = view.webViewClient.shouldInterceptRequest(
                    view, fakeRequest("https://router.localdomain/fixture.mp4")
                )
                assertEquals(403, unsafe?.statusCode)
                // Keep the browser's normal HTTPS path intact; interception is
                // only used for policy-denied initial network requests.
                val public = view.webViewClient.shouldInterceptRequest(
                    view, fakeRequest("https://cdn.example.org/fixture.mp4")
                )
                assertEquals(null, public)
            } finally {
                bridge.close()
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
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

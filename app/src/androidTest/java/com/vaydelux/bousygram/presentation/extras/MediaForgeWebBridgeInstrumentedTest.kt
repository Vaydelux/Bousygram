package com.vaydelux.bousygram.presentation.extras

import android.webkit.CookieManager
import android.webkit.WebSettings
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

    @Test fun webBridgeRejectsUnsafeTopLevelEmbed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = MediaForgeWebBridge(onMediaResource = {})
            try {
                val blocked = listOf(
                    "http://mediaforge.example.invalid/embed/movie/42",
                    "https://127.0.0.1/embed/movie/42",
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

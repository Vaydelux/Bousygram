package com.vaydelux.bousygram.core.cast

import android.graphics.Color
import androidx.appcompat.R as AppCompatR
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On an actual Android device, exercise the constructor that previously crashed when
 * MediaRouterThemeHelper called ColorUtils.calculateContrast on a transparent primary color.
 *
 * Does not require a Telegram account, a Chromecast, or Google Play Services sign-in.
 */
@RunWith(AndroidJUnit4::class)
class CastRouteButtonThemeInstrumentedTest {
    @Test
    fun mediaRouteButtonUsesFullyOpaqueViewTheme() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            // This must not throw "background can not be translucent".
            val button = CastRouteButtonFactory.create(instrumentation.targetContext)
            assertNotNull(button)
            val colors = button.context.obtainStyledAttributes(
                intArrayOf(AppCompatR.attr.colorPrimary, android.R.attr.colorBackground)
            )
            try {
                assertEquals("Cast colorPrimary must be opaque", 255, Color.alpha(colors.getColor(0, 0)))
                assertEquals("Cast colorBackground must be opaque", 255, Color.alpha(colors.getColor(1, 0)))
            } finally {
                colors.recycle()
            }
        }
    }
}

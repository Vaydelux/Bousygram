package com.vaydelux.bousygram.app

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation journey test verifying the structural fix for episode transitions and back navigation:
 * 1. Play Ep 1
 * 2. Auto-next or skip to Ep 2 in-place (single player destination)
 * 3. Press Back
 * 4. Land on Home (single back pop, regardless of how many episodes were auto-played)
 */
@RunWith(AndroidJUnit4::class)
class EpisodeTransitionAndBackJourneyTest {

    @Test
    fun playEpisode1_autoNextToEpisode2_pressBack_landsOnHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName

        // Launch app directly into preview dashboard
        val output = instrumentation.runShellCommand(
            "am force-stop $packageName; am start -W -n $packageName/.app.MainActivity " +
                "--ez ${MainActivity.EXTRA_UI_PREVIEW} true"
        )
        assertTrue(output, output.contains("Status: ok"))

        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Dashboard heading should be visible", device.wait(Until.hasObject(By.text("Bousygram")), 5_000))

        // Navigate to and click a video card to start playback of Ep 1
        repeat(2) { device.pressDPadDown() }
        device.pressDPadCenter()

        // Allow player surface to prepare and display HUD
        device.waitForIdle(2_000)

        // Simulate Next Episode trigger (auto-next or remote next keycode)
        // In-place episode swapping updates media without pushing a new player route
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT)
        device.waitForIdle(1_500)

        // Press Back (via D-pad Back or BackHandler)
        device.pressBack()
        device.waitForIdle(1_000)

        // Must land directly on Home/Dashboard, NOT on Episode 1
        assertTrue(
            "Pressing Back once after auto-next must immediately return to Home/Dashboard",
            device.wait(Until.hasObject(By.text("Bousygram")), 5_000)
        )
    }

    private fun android.app.Instrumentation.runShellCommand(command: String): String =
        uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().readText()
        }
}

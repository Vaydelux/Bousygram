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

@RunWith(AndroidJUnit4::class)
class AndroidTvNavigationSmokeTest {
    @Test
    fun realApplicationLaunch_survivesDpadAndBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val output = instrumentation.runShellCommand(
            "am start -W -n $packageName/.app.MainActivity"
        )
        assertTrue(output, output.contains("Status: ok"))

        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Real app heading should be visible", device.wait(Until.hasObject(By.text("Bousygram")), 10_000))

        repeat(2) { device.pressDPadDown() }
        repeat(2) { device.pressDPadRight() }
        device.pressDPadCenter()
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        device.pressBack()

        assertTrue("Real app should remain visible after remote input", device.wait(Until.hasObject(By.text("Bousygram")), 5_000))
    }

    @Test
    fun dashboardPreview_showsRealDashboardAndHandlesDpad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val output = instrumentation.runShellCommand(
            "am start -W -n $packageName/.app.MainActivity " +
                "--ez ${MainActivity.EXTRA_UI_PREVIEW} true"
        )
        assertTrue(output, output.contains("Status: ok"))

        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Dashboard heading should be visible", device.wait(Until.hasObject(By.text("Your library")), 5_000))
        // The compact browser intentionally combines the selected source and
        // file count in one line; the actual navigation controls are separate.
        assertTrue("Saved section should be reachable", device.wait(Until.hasObject(By.text("Saved")), 5_000))
        assertTrue("Channels section should be reachable", device.wait(Until.hasObject(By.text("Channels")), 5_000))
        assertTrue("Groups section should be reachable", device.wait(Until.hasObject(By.text("Groups")), 5_000))
        assertTrue("Selected collection context should be visible",
            device.wait(Until.hasObject(By.textContains("Saved Messages")), 5_000))

        repeat(4) { device.pressDPadDown() }
        repeat(3) { device.pressDPadRight() }
        device.pressDPadCenter()
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        device.pressBack()
        assertTrue("Dashboard should remain alive after D-pad input", device.hasObject(By.text("Your library")))
    }

    private fun android.app.Instrumentation.runShellCommand(command: String): String =
        uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
}

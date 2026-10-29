package com.vaydelux.bousygram.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Visual preview coverage only. Real authenticated episode playback requires device qualification. */
@RunWith(AndroidJUnit4::class)
class DashboardPreviewNavigationTest {

    @Test
    fun emptyDashboardPreview_handlesDpadWithoutStartingPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName

        // Launch app directly into preview dashboard
        val output = instrumentation.runShellCommand(
            "am start -W -n $packageName/.app.MainActivity " +
                "--ez ${MainActivity.EXTRA_UI_PREVIEW} true"
        )
        assertTrue(output, output.contains("Status: ok"))

        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Dashboard heading should be visible", device.wait(Until.hasObject(By.text("Bousygram")), 5_000))
        assertTrue("Preview must show its empty source", device.wait(Until.hasObject(By.text("No videos in this source")), 5_000))

        // The preview has no playable media and intentionally inert navigation callbacks.
        repeat(2) { device.pressDPadDown() }
        device.pressDPadCenter()
        device.waitForIdle(2_000)

        assertTrue(
            "Preview should remain visible after remote navigation",
            device.wait(Until.hasObject(By.text("Bousygram")), 5_000)
        )
    }

    private fun android.app.Instrumentation.runShellCommand(command: String): String =
        uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().readText()
        }
}

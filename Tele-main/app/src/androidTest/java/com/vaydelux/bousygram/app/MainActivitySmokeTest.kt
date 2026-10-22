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

/**
 * Startup checks for the real app route. These deliberately do not pass the debug-only
 * ui_preview extra, so CI also detects crashes in application initialization and auth UI.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun launchMainActivity_doesNotCrashAndShowsRealApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertSuccessfulActivityStart(launchMainActivity(instrumentation, preview = false))

        val device = UiDevice.getInstance(instrumentation)
        assertTrue(
            "The real app should show its Bousygram brand after launch",
            device.wait(Until.hasObject(By.text("Bousygram")), 10_000)
        )
    }

    @Test
    fun relaunchMainActivity_doesNotCrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertSuccessfulActivityStart(launchMainActivity(instrumentation, preview = false))
        assertSuccessfulActivityStart(launchMainActivity(instrumentation, preview = false))

        val device = UiDevice.getInstance(instrumentation)
        assertTrue(
            "The real app should remain visible after relaunch",
            device.wait(Until.hasObject(By.text("Bousygram")), 10_000)
        )
    }

    @Test
    fun debugDashboardPreview_opensForVisualSmokeCheck() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertSuccessfulActivityStart(launchMainActivity(instrumentation, preview = true))

        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Preview dashboard heading should be visible", device.wait(Until.hasObject(By.text("Bousygram")), 5_000))
        assertTrue("Favorites navigation should be visible", device.wait(Until.hasObject(By.text("Favorites")), 5_000))
    }

    private fun launchMainActivity(
        instrumentation: android.app.Instrumentation,
        preview: Boolean
    ): String {
        val packageName = instrumentation.targetContext.packageName
        val previewArg = if (preview) "--ez ${MainActivity.EXTRA_UI_PREVIEW} true" else ""
        return instrumentation.runShellCommand(
            "am force-stop $packageName; am start -W -n $packageName/.app.MainActivity $previewArg"
        )
    }

    private fun assertSuccessfulActivityStart(output: String) {
        assertTrue(output, output.contains("Status: ok"))
        assertTrue(output, output.contains("Complete"))
    }

    private fun android.app.Instrumentation.runShellCommand(command: String): String =
        uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
}

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
        assertTrue("Library navigation should be visible", device.wait(Until.hasObject(By.text("Saved Messages")), 5_000))
    }

    /** Regression: Android can reuse the task's top Activity for a new launch intent. */
    @Test
    fun singleTopRelaunch_switchesBetweenRealModeAndDebugPreview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val device = UiDevice.getInstance(instrumentation)

        assertSuccessfulActivityStart(launchMainActivity(instrumentation, preview = true))
        assertTrue(
            "Debug preview must show its empty media grid",
            device.wait(Until.hasObject(By.text("No playable media found in this folder")), 10_000)
        )

        // --activity-single-top delivers onNewIntent when MainActivity is on top.
        val realOutput = instrumentation.runShellCommand(
            "am start -W --activity-single-top -n $packageName/.app.MainActivity"
        )
        assertTrue(realOutput, realOutput.contains("Complete"))
        assertTrue(
            "Real mode chooser should replace the debug preview after onNewIntent",
            device.wait(Until.hasObject(By.text("Choose how you want to explore.")), 10_000)
        )

        val previewOutput = instrumentation.runShellCommand(
            "am start -W --activity-single-top -n $packageName/.app.MainActivity " +
                "--ez ${MainActivity.EXTRA_UI_PREVIEW} true"
        )
        assertTrue(previewOutput, previewOutput.contains("Complete"))
        assertTrue(
            "Debug preview should return after a subsequent preview intent",
            device.wait(Until.hasObject(By.text("No playable media found in this folder")), 10_000)
        )
    }

    private fun launchMainActivity(
        instrumentation: android.app.Instrumentation,
        preview: Boolean
    ): String {
        val packageName = instrumentation.targetContext.packageName
        val previewArg = if (preview) "--ez ${MainActivity.EXTRA_UI_PREVIEW} true" else ""
        return instrumentation.runShellCommand(
            "am start -W -n $packageName/.app.MainActivity $previewArg"
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

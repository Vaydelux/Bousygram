package com.vaydelux.bousygram.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun launchMainActivity_doesNotCrash() {
        assertLaunchSucceeded(launchSmokePreview())
    }

    @Test
    fun relaunchMainActivity_doesNotCrash() {
        assertLaunchSucceeded(launchSmokePreview())
        assertLaunchSucceeded(launchSmokePreview())
    }

    @Test
    fun launchPreviewMode_doesNotCrash() {
        assertLaunchSucceeded(launchSmokePreview())
    }

    private fun launchSmokePreview(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        return instrumentation.runShellCommand(
            "am start -W -n $packageName/.app.MainActivity " +
                "--ez ${MainActivity.EXTRA_UI_PREVIEW} true " +
                "--ez ${MainActivity.EXTRA_UI_SMOKE} true"
        )
    }

    private fun assertLaunchSucceeded(output: String) {
        assertTrue(output, output.contains("Status: ok"))
        assertTrue(output, output.contains("Complete"))
    }

    private fun android.app.Instrumentation.runShellCommand(command: String): String {
        return uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }
}

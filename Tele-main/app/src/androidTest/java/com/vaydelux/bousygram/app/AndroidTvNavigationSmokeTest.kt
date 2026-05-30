package com.vaydelux.bousygram.app

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTvNavigationSmokeTest {
    @Test
    fun dpadNavigationSmoke_doesNotCrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val output = instrumentation.runShellCommand(
            "am start -W -n $packageName/.app.MainActivity " +
                "--ez ${MainActivity.EXTRA_UI_PREVIEW} true " +
                "--ez ${MainActivity.EXTRA_UI_SMOKE} true"
        )
        assertTrue(output, output.contains("Status: ok"))

        val device = UiDevice.getInstance(instrumentation)
        repeat(4) { device.pressDPadDown() }
        repeat(3) { device.pressDPadRight() }
        device.pressDPadCenter()
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        device.pressBack()
    }

    private fun android.app.Instrumentation.runShellCommand(command: String): String {
        return uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }
}

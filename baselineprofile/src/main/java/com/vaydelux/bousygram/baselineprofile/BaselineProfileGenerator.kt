package com.vaydelux.bousygram.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile Generator covering critical Bousygram user journeys:
 * 1. App launch
 * 2. TvHybridHomeDashboard scrolling and D-pad focus movement
 * 3. Opening a chat / topic
 * 4. Opening the player
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = "com.vaydelux.bousygram",
            includeInStartupProfile = true
        ) {
            // 1. App Launch
            pressHome()
            startActivityAndWait()

            // 2. TvHybridHomeDashboard scrolling and D-pad focus movement
            device.waitForIdle()
            // Navigate down across content rows
            repeat(4) {
                device.pressDPadDown()
                device.waitForIdle()
            }
            // Navigate right and left across cards within a row
            repeat(3) {
                device.pressDPadRight()
                device.waitForIdle()
            }
            repeat(2) {
                device.pressDPadLeft()
                device.waitForIdle()
            }
            // Further vertical scrolling
            repeat(2) {
                device.pressDPadDown()
                device.waitForIdle()
            }

            // 3. Opening a chat / channel / topic
            device.pressDPadCenter()
            device.waitForIdle()

            // 4. Opening the player
            device.pressDPadCenter()
            device.waitForIdle()

            // Allow video surface and player HUD to render
            Thread.sleep(1000)

            // Return back to dashboard
            device.pressBack()
            device.waitForIdle()
        }
    }
}

package com.vaydelux.bousygram.presentation.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

/**
 * Captures real Compose pixels from an emulator run, rather than declaring a
 * visually broken build 'polished' based solely on green unit tests.
 * Screenshot evidence still requires review; it is not pixel-diff certification.
 */
internal fun writeUiEvidence(rule: ComposeContentTestRule, name: String) {
    require(Regex("[a-z0-9-]+").matches(name)) { "Invalid UI evidence filename" }
    rule.waitForIdle()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.filesDir, "ui-evidence")
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create UI evidence folder" }
    val file = File(directory, "$name.png")
    FileOutputStream(file).use { output ->
        check(rule.onRoot().captureToImage().asAndroidBitmap()
            .compress(Bitmap.CompressFormat.PNG, 100, output)) {
            "Could not capture Compose screenshot"
        }
    }
    check(file.length() > 0) { "UI screenshot is empty: $name" }
}

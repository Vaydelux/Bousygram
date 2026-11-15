package com.vaydelux.bousygram.presentation.extras

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.R
import com.vaydelux.bousygram.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Launcher artwork and Google TV app management must point to approved mascot. */
@RunWith(AndroidJUnit4::class)
class BousygramGoogleTvBrandingInstrumentedTest {
    @Test fun androidTvAndAppSettingsUseTheSameLogoOnlyBanner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = R.drawable.ic_bousygram_tv_banner
        assertEquals(expected, context.applicationInfo.banner)
        val launcher = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java), 0
        )
        assertEquals(expected, launcher.banner)
        assertNotNull(context.getDrawable(expected))
    }
}

package com.vaydelux.bousygram.presentation.theme

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * One motion policy for mobile, tablet and TV. Rendering preferences are determined
 * by Android accessibility settings and device resources, never by CPU ABI.
 * All animation here is decorative: navigation and playback do not depend on it.
 */
internal data class BousygramMotionPolicy(
    val animationsEnabled: Boolean,
    val lowRam: Boolean
) {
    val animateFocusScale: Boolean get() = animationsEnabled && !lowRam
    val animateShimmer: Boolean get() = animationsEnabled && !lowRam
    val animateIndeterminateProgress: Boolean get() = animationsEnabled && !lowRam

    fun duration(baseMs: Int): Int = when {
        !animationsEnabled -> 0
        lowRam -> baseMs.coerceIn(0, 90)
        else -> baseMs.coerceAtLeast(0)
    }
}

internal fun androidMotionPolicy(animationsEnabled: Boolean, lowRam: Boolean) =
    BousygramMotionPolicy(animationsEnabled = animationsEnabled, lowRam = lowRam)

@Composable
internal fun rememberBousygramMotionPolicy(): BousygramMotionPolicy {
    val context = LocalContext.current
    return remember(context) {
        val reducedMotion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !ValueAnimator.areAnimatorsEnabled()
        } else {
            val durationScale = runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            }.getOrDefault(1f)
            durationScale <= 0f
        }
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice == true
        androidMotionPolicy(!reducedMotion, lowRam)
    }
}

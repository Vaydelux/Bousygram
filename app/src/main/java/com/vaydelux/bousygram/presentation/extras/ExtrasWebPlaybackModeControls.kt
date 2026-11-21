package com.vaydelux.bousygram.presentation.extras

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The same WebView player handles TMDB Movies/TV and AniList Anime.
 * Explicitly consented fallbacks never silently alter the strict default.
 * These controls are content-agnostic and can be UI-tested offline.
 */
@Composable
internal fun ExtrasWebPlaybackModeControls(
    compatibility: Boolean,
    legacyWindows: Boolean,
    onStrict: () -> Unit,
    onCompatibility: () -> Unit,
    onLegacy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(
            onClick = if (compatibility) onStrict else onCompatibility,
            modifier = Modifier.testTag("extras-webview-compatibility")
        ) {
            Text(
                if (compatibility) "Use strict WebView mode"
                else "Black screen? Try compatibility (cookies + autoplay)",
                maxLines = 2
            )
        }
        if (compatibility) {
            TextButton(
                onClick = if (legacyWindows) onCompatibility else onLegacy,
                modifier = Modifier.testTag("extras-webview-legacy-navigation")
            ) {
                Text(
                    if (legacyWindows) "Use popup-isolated compatibility"
                    else "Still black? Try legacy WebView navigation",
                    maxLines = 2
                )
            }
        }
    }
}

/** Safe to show in a device screenshot; contains no page URL or account data. */
internal fun extrasWebViewRuntimeLabel(): String =
    runCatching {
        val info = WebView.getCurrentWebViewPackage()
            ?: return@runCatching "WebView engine unavailable"
        val version = info.versionName.orEmpty().take(48)
        "WebView engine: ${info.packageName.take(64)} ${version}".trim()
    }.getOrDefault("WebView engine unavailable")

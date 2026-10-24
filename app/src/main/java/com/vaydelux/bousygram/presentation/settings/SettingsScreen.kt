package com.vaydelux.bousygram.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.tdlib.cache.TelegramCachePruner
import com.vaydelux.bousygram.core.tdlib.cache.TelegramCacheStorageStats
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramChip
import com.vaydelux.bousygram.presentation.components.BousygramScaffold
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings Screen strictly constrained to:
 * 1. Privacy mode (blur posters)
 * 2. Subtitle language
 * 3. Clear cache
 * 4. Export diagnostics
 * 5. Log out
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themePreset: BousygramThemePreset = BousygramThemePreset.DARK,
    onThemePresetChange: (BousygramThemePreset) -> Unit = {},
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adaptive = rememberAdaptiveMetrics()
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val tdLibCacheDir = remember(context) { File(context.filesDir, "tdlib_files") }

    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var subtitleLang by remember { mutableStateOf(EnterprisePreferences.subtitleLanguage()) }
    var storageRefresh by remember { mutableStateOf(0) }
    var clearCacheStatus by remember { mutableStateOf<String?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }

    val storageStats by produceState(
        initialValue = TelegramCacheStorageStats(
            appCacheBytes = 0L,
            appFilesBytes = 0L,
            tdLibCacheBytes = 0L,
            cacheLimitBytes = TelegramCachePruner.calculateDynamicCacheLimit(context)
        ),
        context,
        storageRefresh
    ) {
        value = withContext(Dispatchers.IO) {
            TelegramCachePruner.storageStats(context, tdLibCacheDir)
        }
    }
    val cacheSize = storageStats.totalBytes

    val diagnosticsText = remember(safeMode, subtitleLang, cacheSize) {
        EnterprisePreferences.diagnosticsText(cacheSize, BuildConfig.VERSION_NAME)
    }

    val subtitleLanguages = listOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ar" to "Arabic",
        "off" to "Off"
    )

    BousygramScaffold(modifier) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (adaptive.isCompact) 16.dp else 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!adaptive.isCompact) {
                Column(
                    modifier = Modifier.width(280.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Settings",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Zero-configuration controls and diagnostics.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    BousygramButton("Back", onBack, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.weight(1f))
                    BousygramButton(
                        text = "About & TMDB Notice",
                        onClick = onOpenAbout,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (adaptive.isCompact) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            BousygramButton("Back", onBack)
                        }
                    }
                }

                // 1. Privacy Mode
                item {
                    EnterprisePanel(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    "Privacy mode",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Blur media posters and thumbnails on screens to protect viewing privacy.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = safeMode,
                                onCheckedChange = {
                                    safeMode = it
                                    EnterprisePreferences.setSafeMode(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF071014),
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }

                // 2. Subtitle Language
                item {
                    EnterprisePanel(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Subtitle language",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Preferred language for automatic subtitle selection in video playback.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(subtitleLanguages.size) { idx ->
                                        val (code, label) = subtitleLanguages[idx]
                                        val selected = subtitleLang.equals(code, ignoreCase = true)
                                        BousygramChip(
                                            text = label,
                                            selected = selected,
                                            onClick = {
                                                subtitleLang = code
                                                EnterprisePreferences.setSubtitleLanguage(code)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Clear Cache
                item {
                    EnterprisePanel(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        "Clear cache",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Local media cache size: ${formatBytes(cacheSize)}. Watched and inactive segments will be deleted.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                BousygramButton(
                                    text = if (isClearingCache) "Clearing…" else "Clear Cache Now",
                                    onClick = {
                                        if (!isClearingCache) {
                                            isClearingCache = true
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    TelegramCachePruner.pruneCacheNow(context, tdLibCacheDir)
                                                }
                                                clearCacheStatus = "Cleared ${formatBytes(result.freedBytes)} (${result.deletedFiles} files)"
                                                isClearingCache = false
                                                storageRefresh++
                                            }
                                        }
                                    }
                                )
                            }
                            if (clearCacheStatus != null) {
                                Text(
                                    text = clearCacheStatus.orEmpty(),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // 4. Export Diagnostics
                item {
                    EnterprisePanel(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    "Export diagnostics",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Export streaming health, system telemetry, and session metrics.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            BousygramButton(
                                text = "Export Diagnostics",
                                onClick = {
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("Bousygram Diagnostics", diagnosticsText)
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, diagnosticsText)
                                        type = "text/plain"
                                    }
                                    val chooser = Intent.createChooser(sendIntent, "Export diagnostics")
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(chooser)
                                    Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // 5. Log Out
                item {
                    EnterprisePanel(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    "Log out",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Sign out of this Telegram account and clear authenticated session tokens.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            BousygramButton(
                                text = "Log Out",
                                onClick = onLogout,
                                modifier = Modifier
                            )
                        }
                    }
                }

                if (adaptive.isCompact) {
                    item {
                        BousygramButton(
                            text = "About & TMDB Attribution",
                            onClick = onOpenAbout,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

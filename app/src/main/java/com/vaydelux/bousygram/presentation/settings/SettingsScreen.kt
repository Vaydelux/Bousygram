package com.vaydelux.bousygram.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.vaydelux.bousygram.data.store.BufferMode
import com.vaydelux.bousygram.data.store.CacheLimitOption
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.data.store.PlaybackMode
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramChip
import com.vaydelux.bousygram.presentation.components.BousygramScaffold
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.components.FeatureRow
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themePreset: BousygramThemePreset,
    onThemePresetChange: (BousygramThemePreset) -> Unit,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adaptive = rememberAdaptiveMetrics()
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val tdLibCacheDir = remember(context) { File(context.filesDir, "tdlib_files") }

    var selectedEngine by remember { mutableStateOf(EnterprisePreferences.selectedPlayerEngine()) }
    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var hideWatched by remember { mutableStateOf(EnterprisePreferences.hideWatched()) }
    var autoPlayNext by remember { mutableStateOf(EnterprisePreferences.autoPlayNext()) }
    var subtitlesDefault by remember { mutableStateOf(EnterprisePreferences.subtitlesDefault()) }
    var speed by remember { mutableFloatStateOf(EnterprisePreferences.preferredSpeed()) }
    var bufferMode by remember { mutableStateOf(EnterprisePreferences.bufferMode()) }
    var playbackMode by remember { mutableStateOf(EnterprisePreferences.playbackMode()) }
    var cacheLimitOption by remember { mutableStateOf(EnterprisePreferences.cacheLimitOption()) }
    var storageRefresh by remember { mutableStateOf(0) }
    var lastPruneResult by remember { mutableStateOf<String?>(null) }
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }

    val storageStats by produceState(
        initialValue = TelegramCacheStorageStats(
            appCacheBytes = 0L,
            appFilesBytes = 0L,
            tdLibCacheBytes = 0L,
            cacheLimitBytes = TelegramCachePruner.DEFAULT_CACHE_CEILING_BYTES
        ),
        context,
        cacheLimitOption,
        storageRefresh
    ) {
        value = withContext(Dispatchers.IO) {
            TelegramCachePruner.storageStats(context, tdLibCacheDir)
        }
    }
    val cacheSize = storageStats.totalBytes
    val diagnostics = remember(
        safeMode,
        hideWatched,
        autoPlayNext,
        subtitlesDefault,
        speed,
        bufferMode,
        cacheLimitOption,
        cacheSize
    ) {
        EnterprisePreferences.diagnosticsText(cacheSize, BuildConfig.VERSION_NAME)
    }
    val playbackReport = remember(storageRefresh) { PlaybackDiagnostics.sessionReport() }

    BousygramScaffold(modifier) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (adaptive.isCompact) 16.dp else 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Sidebar for Non-Compact screen
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
                        "Enterprise Telegram video client configuration and storage management.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    BousygramButton("Back", onBack, modifier = Modifier.fillMaxWidth())

                    EnterprisePanel(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("STATUS", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TDLib MTProto", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF5EE5BE)))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Keystore AES-256", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                Text("Active", style = MaterialTheme.typography.labelSmall, color = Color(0xFF5EE5BE))
                            }
                        }
                    }
                }
            }

            // Main Settings Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header on Compact screen
                if (adaptive.isCompact) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Settings",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            BousygramButton("Back", onBack)
                        }
                    }
                }

                // Section 1: Playback
                item {
                    Section("1. Playback") {
                        Text(
                            "Preferred Media Engine",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            SelectedPlayerEngine.entries.forEach { engine ->
                                BousygramChip(
                                    text = engine.label,
                                    selected = selectedEngine == engine,
                                    onClick = {
                                        selectedEngine = engine
                                        EnterprisePreferences.setSelectedPlayerEngine(engine)
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Default Speed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { value ->
                                BousygramChip(
                                    text = "${value}x",
                                    selected = speed == value,
                                    onClick = {
                                        speed = value
                                        EnterprisePreferences.setPreferredSpeed(value)
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Stream Runway Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            BufferMode.entries.forEach { mode ->
                                BousygramChip(
                                    text = mode.label,
                                    selected = bufferMode == mode,
                                    onClick = {
                                        bufferMode = mode
                                        EnterprisePreferences.setBufferMode(mode)
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Streaming Pipeline",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            PlaybackMode.entries.forEach { mode ->
                                BousygramChip(
                                    text = mode.label,
                                    selected = playbackMode == mode,
                                    onClick = {
                                        playbackMode = mode
                                        EnterprisePreferences.setPlaybackMode(mode)
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        FeatureRow("Auto-play next episode", if (autoPlayNext) "On" else "Off", onClick = {
                            autoPlayNext = !autoPlayNext
                            EnterprisePreferences.setAutoPlayNext(autoPlayNext)
                        })
                        FeatureRow("Subtitles by default", if (subtitlesDefault) "On" else "Off", onClick = {
                            subtitlesDefault = !subtitlesDefault
                            EnterprisePreferences.setSubtitlesDefault(subtitlesDefault)
                        })
                    }
                }

                // Section 2: Appearance
                item {
                    Section("2. Appearance") {
                        Text(
                            "Color Theme",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            BousygramThemePreset.entries.forEach { preset ->
                                ThemePresetCard(
                                    preset = preset,
                                    selected = themePreset == preset,
                                    onClick = { onThemePresetChange(preset) }
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        FeatureRow("Privacy Mode (Blur Posters)", if (safeMode) "Active" else "Off", onClick = {
                            safeMode = !safeMode
                            EnterprisePreferences.setSafeMode(safeMode)
                        })
                        FeatureRow("Hide Watched Media", if (hideWatched) "On" else "Off", onClick = {
                            hideWatched = !hideWatched
                            EnterprisePreferences.setHideWatched(hideWatched)
                        })
                    }
                }

                // Section 3: Storage & Native File Cache
                item {
                    Section("3. Storage & Cache") {
                        FeatureRow("Total Local Footprint", formatBytes(cacheSize), {})
                        LinearProgressIndicator(
                            progress = {
                                if (storageStats.cacheLimitBytes <= 0L) 0f
                                else (cacheSize.toFloat() / storageStats.cacheLimitBytes.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TDLib Cache: ${formatBytes(storageStats.tdLibCacheBytes)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            Text("Limit: ${formatBytes(storageStats.cacheLimitBytes)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Cache Ceiling Limit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            CacheLimitOption.entries.forEach { option ->
                                BousygramChip(
                                    text = option.label,
                                    selected = cacheLimitOption == option,
                                    onClick = {
                                        cacheLimitOption = option
                                        EnterprisePreferences.setCacheLimitOption(option)
                                        storageRefresh++
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BousygramButton(
                                text = "Prune Cache Now",
                                onClick = {
                                    scope.launch {
                                        lastPruneResult = "Pruning..."
                                        val result = withContext(Dispatchers.IO) {
                                            TelegramCachePruner.pruneCacheNow(context, tdLibCacheDir)
                                        }
                                        lastPruneResult = "Freed ${formatBytes(result.freedBytes)} (${result.deletedFiles} files)"
                                        storageRefresh++
                                    }
                                }
                            )
                            if (lastPruneResult != null) {
                                Text(
                                    lastPruneResult!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF5EE5BE)
                                )
                            }
                        }
                    }
                }

                // Section 4: Account & Session
                item {
                    Section("4. Account & Security") {
                        FeatureRow("Connection Protocol", "Direct Telegram MTProto (Native C++)", {})
                        FeatureRow("Local Encryption", "Hardware Keystore AES-256 GCM", {})
                        Spacer(Modifier.height(8.dp))
                        BousygramButton(
                            text = "Sign Out / Switch Account",
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 5: Diagnostics Accordion
                item {
                    Section("5. Diagnostics & Health") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BousygramButton(
                                text = "Copy Diagnostic Report",
                                onClick = {
                                    runCatching {
                                        clipboard?.setPrimaryClip(
                                            ClipData.newPlainText("Bousygram Diagnostics", diagnostics + "\n\n" + playbackReport)
                                        )
                                    }
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                var copyFeedback by remember { mutableStateOf(false) }
                                val context = LocalContext.current
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF243B55),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val json = com.vaydelux.bousygram.data.store.StreamHealthTelemetry.exportJson()
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Bousygram Telemetry", json)
                                            clipboard?.setPrimaryClip(clip)
                                            copyFeedback = true
                                        }
                                ) {
                                    Text(
                                        text = if (copyFeedback) "Copied JSON ✓" else "Export JSON 📋",
                                        color = if (copyFeedback) Color(0xFF5EE5BE) else Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E2E48),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isDiagnosticsExpanded = !isDiagnosticsExpanded }
                                ) {
                                    Text(
                                        text = if (isDiagnosticsExpanded) "Hide Details ▲" else "View Details ▼",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isDiagnosticsExpanded) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0C1420),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .border(1.dp, Color(0xFF1F2E45), RoundedCornerShape(12.dp))
                            ) {
                                Text(
                                    text = diagnostics + "\n\n" + playbackReport + "\n\nStream Telemetry:\n" +
                                        com.vaydelux.bousygram.data.store.StreamHealthTelemetry.exportJson(),
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePresetCard(
    preset: BousygramThemePreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    TvFocusSurface(
        onClick = onClick,
        selected = selected,
        width = 142.dp
    ) { focused ->
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(preset.primary, preset.secondary, preset.tertiary).forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(swatch)
                    )
                }
                if (selected || focused) {
                    Text("✓", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                preset.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            Text(
                preset.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    EnterprisePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

package com.vaydelux.bousygram.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading
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
    var playerEngine by remember { mutableStateOf(EnterprisePreferences.selectedPlayerEngine()) }
    val audioProfile by EnterprisePreferences.audioProcessingProfileFlow.collectAsState()
    var videoDebugHud by remember { mutableStateOf(EnterprisePreferences.videoDebugHudEnabled()) }
    var surfaceType by remember { mutableStateOf(EnterprisePreferences.surfaceTypePreference()) }
    var mpvHwDecMode by remember { mutableStateOf(EnterprisePreferences.mpvHwDecMode()) }
    var storageRefresh by remember { mutableIntStateOf(0) }
    var clearCacheStatus by remember { mutableStateOf<String?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

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
    // The filesDir remainder is not automatically evictable cache.
    // These sampled locations exclude databases, shared preferences and APKs.
    val cacheSize = storageStats.measuredCacheBytes

    val diagnosticsText = remember(safeMode, subtitleLang, playerEngine, audioProfile, cacheSize) {
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
                .padding(if (adaptive.isCompact) 16.dp else 32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!adaptive.isCompact) {
                Column(
                    modifier = Modifier.width(280.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BousygramPageHeading(
                        eyebrow = "Bousygram  /  Preferences",
                        title = "Settings",
                        subtitle = "Appearance, playback and privacy controls.",
                        compact = false
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
                            BousygramPageHeading(
                                eyebrow = "Bousygram  /  Preferences",
                                title = "Settings",
                                subtitle = "Personalize your experience.",
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                            BousygramButton("Back", onBack)
                        }
                    }
                }

                // 1. Theme
                item {
                    EnterprisePanel(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Theme",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Visual appearance and high-contrast display tuning for TV and mobile screens.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // Large text and narrow phones must not clip theme choices.
                            val isNarrow = with(androidx.compose.ui.platform.LocalDensity.current) {
                                androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp() < 480.dp
                            }
                            @Composable
                            fun ThemeChoices() {
                                BousygramThemePreset.entries.forEach { preset ->
                                    BousygramChip(
                                        text = preset.displayName,
                                        selected = themePreset == preset,
                                        onClick = {
                                            onThemePresetChange(preset)
                                            EnterprisePreferences.setVisualThemeId(preset.id)
                                        }
                                    )
                                }
                            }
                            if (isNarrow) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ThemeChoices()
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ThemeChoices()
                                }
                            }
                        }
                    }
                }

                // 2. Playback Engine (default Auto)
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
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        "Playback engine",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Auto uses ExoPlayer/libmpv. Select LibVLC for a third MKV/TS decoder option; it may use more memory on older TVs.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                EnterprisePreferences.supportedPlayerEngines().forEach { engine ->
                                    val isSelected = playerEngine == engine
                                    val chipLabel = when (engine) {
                                        com.vaydelux.bousygram.data.store.SelectedPlayerEngine.AUTO -> "Auto (Default)"
                                        com.vaydelux.bousygram.data.store.SelectedPlayerEngine.ExoPlayer -> "ExoPlayer"
                                        com.vaydelux.bousygram.data.store.SelectedPlayerEngine.MPV -> "libmpv"
                                        com.vaydelux.bousygram.data.store.SelectedPlayerEngine.LIBVLC -> "LibVLC"
                                    }
                                    BousygramChip(
                                        text = chipLabel,
                                        selected = isSelected,
                                        onClick = {
                                            playerEngine = engine
                                            EnterprisePreferences.setSelectedPlayerEngine(engine)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Smart Volume remains; tonal equalizer controls have been retired.
                item { AudioSettingsPanel(isTv = adaptive.isTv) }

                // 3. Subtitle Language
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
                                "Preferred language for automatic subtitle track selection during video playback.",
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

                // 4. Clear Cache (no cache slider)
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
                                        "Cache directories: ${formatBytes(cacheSize)}. " +
                                            "Telegram media target: ${formatBytes(storageStats.cacheLimitBytes)} " +
                                            "(maximum 160 MiB, within the 200 MiB disposable-media budget).",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                BousygramButton(
                                    text = if (isClearingCache) "Pruning…" else "Prune Safe Cache",
                                    onClick = {
                                        if (!isClearingCache) {
                                            isClearingCache = true
                                            scope.launch {
                                                clearCacheStatus = null
                                                try {
                                                    val result = withContext(Dispatchers.IO) {
                                                        TelegramCachePruner.pruneCacheNow(context, tdLibCacheDir)
                                                    }
                                                    clearCacheStatus =
                                                        "Pruned ${formatBytes(result.freedBytes)} " +
                                                            "(${result.deletedFiles} tracked files). ${result.note}"
                                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                    throw cancelled
                                                } catch (failure: Exception) {
                                                    android.util.Log.w(
                                                        "BousygramSettings", "Safe cache pruning failed", failure
                                                    )
                                                    clearCacheStatus =
                                                        "Cache pruning could not complete. " +
                                                            "Pinned and active media were not intentionally removed."
                                                } finally {
                                                    isClearingCache = false
                                                    storageRefresh++
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            Text(
                                text = "Other app files: ${formatBytes(storageStats.appFilesBytes)}. " +
                                    "Measured directories: ${formatBytes(storageStats.totalBytes)} " +
                                    "(not total installed app size; databases and APK excluded). " +
                                    "Pinned or active downloads can exceed the cache target.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
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

                // 5. Advanced Section (holds diagnostics, privacy mode, account, etc.)
                item {
                    EnterprisePanel(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Advanced",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isAdvancedExpanded) "Tap to collapse advanced options" else "Tap to show diagnostics, privacy mode, and account controls",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(
                                    if (isAdvancedExpanded) "▲" else "▼",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            if (isAdvancedExpanded) {
                                Spacer(Modifier.height(4.dp))

                                // Privacy Mode
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                        Text(
                                            "Privacy mode",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Blur media posters and thumbnails to protect viewing privacy.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Switch(
                                        checked = safeMode,
                                        onCheckedChange = {
                                            safeMode = it
                                            EnterprisePreferences.setSafeMode(it)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                if (BuildConfig.DEBUG) {
                                    // Developer Tools Section
                                    Text(
                                        "Developer Tools (In-App Video Debug)",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Live Video Debug HUD Toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                            Text(
                                                "Live Video Debug HUD",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Displays 10-ft readable real-time overlay in the player (Engine, Surface, Decoders, Drop counters, Overlays). Toggle with remote Yellow or Info key.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Switch(
                                            checked = videoDebugHud,
                                            onCheckedChange = {
                                                videoDebugHud = it
                                                EnterprisePreferences.setVideoDebugHudEnabled(it)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )
                                    }

                                    // Player Engine Switcher
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "Playback Engine",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Select player engine without restarting",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            EnterprisePreferences.supportedPlayerEngines().forEach { engine ->
                                                BousygramChip(
                                                    text = engine.label,
                                                    selected = playerEngine == engine,
                                                    onClick = {
                                                        playerEngine = engine
                                                        EnterprisePreferences.setSelectedPlayerEngine(engine)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Surface Type Switcher
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "Surface Presentation Mode",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Switch between SurfaceView (hardware overlay) and TextureView (composited view)",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("auto" to "Auto", "surfaceview" to "SurfaceView", "textureview" to "TextureView").forEach { (type, label) ->
                                                BousygramChip(
                                                    text = label,
                                                    selected = surfaceType == type,
                                                    onClick = {
                                                        surfaceType = type
                                                        EnterprisePreferences.setSurfaceTypePreference(type)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // MPV HWDEC / VO Mode Switcher
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "MPV Hardware Decoder & VO Mode",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "mediacodec-copy works reliably on Mali-G52 GPU. mediacodec is direct embed.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(
                                                "mediacodec-copy" to "vo=gpu (mediacodec-copy)",
                                                "mediacodec" to "vo=mediacodec_embed",
                                                "no" to "vo=gpu (Software)"
                                            ).forEach { (mode, label) ->
                                                BousygramChip(
                                                    text = label,
                                                    selected = mpvHwDecMode == mode,
                                                    onClick = {
                                                        mpvHwDecMode = mode
                                                        EnterprisePreferences.setMpvHwDecMode(mode)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Open Engine Lab Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                            Text(
                                                "Engine Lab Diagnostic Activity",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Live engine comparison with surface and buffer telemetry.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        BousygramButton(
                                            text = "Open Lab",
                                            onClick = {
                                                context.startActivity(Intent(context, com.vaydelux.bousygram.presentation.debug.EngineLabActivity::class.java))
                                            }
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Export Diagnostics
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                            Text(
                                                "Diagnostics & Health",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Export streaming telemetry, player buffer stats, and session logs.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        BousygramButton(
                                            text = "Export",
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

                                    Spacer(Modifier.height(8.dp))

                                }

                                // About
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                        Text(
                                            "About & TMDB Notice",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Metadata and poster attribution under TMDB terms.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    BousygramButton(
                                        text = "View",
                                        onClick = onOpenAbout
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                // Log out
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                        Text(
                                            "Log out",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Sign out of Telegram account and reset authenticated sessions.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    BousygramButton(
                                        text = "Log Out",
                                        onClick = onLogout
                                    )
                                }
                            }
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

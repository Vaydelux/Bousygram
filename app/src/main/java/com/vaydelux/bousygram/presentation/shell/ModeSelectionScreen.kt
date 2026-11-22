package com.vaydelux.bousygram.presentation.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIconRef
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramTooltipBox
import com.vaydelux.bousygram.presentation.components.BousygramTooltipPlacement
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramBackdropPattern

/** Route boundary only. Neither screen creates a Telegram or Extras API client. */
enum class BousygramMode { SELECT, TELEGRAM, EXTRAS_MAINTENANCE }

@Composable
fun BousygramModeSelection(
    onTelegram: () -> Unit,
    onExtras: () -> Unit,
    themePreset: BousygramThemePreset = BousygramThemePreset.DARK,
    designStyle: BousygramDesignStyle = BousygramDesignStyle.CINEMA_HALO,
    backdropPattern: BousygramBackdropPattern = BousygramBackdropPattern.AMBIENT_HALO,
    onThemePresetChange: (BousygramThemePreset) -> Unit = {},
    onDesignStyleChange: (BousygramDesignStyle) -> Unit = {},
    onBackdropPatternChange: (BousygramBackdropPattern) -> Unit = {}
) {
    val metrics = rememberAdaptiveMetrics()
    var appearanceOpen by remember { mutableStateOf(false) }
    if (appearanceOpen) {
        StartupAppearanceDialog(
            themePreset = themePreset,
            designStyle = designStyle,
            backdropPattern = backdropPattern,
            onThemePresetChange = onThemePresetChange,
            onDesignStyleChange = onDesignStyleChange,
            onBackdropPatternChange = onBackdropPatternChange,
            onDismiss = { appearanceOpen = false }
        )
    }
    val compact = metrics.isTv || metrics.widthDp > metrics.heightDp ||
        metrics.heightDp < 620.dp
    val horizontalCards = compact && metrics.widthDp >= 690.dp
    PremiumGridBackdrop {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(
                horizontal = if (metrics.isTv) 32.dp else 18.dp,
                vertical = if (compact) 8.dp else 22.dp
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = if (horizontalCards) 1020.dp else 760.dp)
                    .fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                // One gap between major groups; the hero and cards own their
                // smaller internal gaps instead of inheriting this large gap.
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)
            ) {
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BousygramBrandMark(size = 56.dp, showHalo = true)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Bousygram",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Your screen. Your library.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("startup-hero"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BousygramBrandMark(size = 86.dp, showHalo = true)
                        Text(
                            "Bousygram",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Your screen. Your library.",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Choose a media experience to get started.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                BousygramButton(
                    text = "Change appearance",
                    onClick = { appearanceOpen = true },
                    leadingIcon = BousygramIcons.Theme,
                    tooltip = "Choose a theme, design style and background for every screen",
                    modifier = Modifier.widthIn(min = 232.dp).testTag("startup-change-appearance")
                )
                if (horizontalCards) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ModeOption(
                            title = "Telegram Media",
                            subtitle = "Saved messages, channels, groups and your media",
                            onClick = onTelegram,
                            primary = true,
                            modeTag = "mode-telegram",
                            icon = BousygramIcons.Telegram,
                            tooltip = "Browse and stream videos from your Telegram chats and channels",
                            modifier = Modifier.weight(1f)
                        )
                        ModeOption(
                            title = "Movies, TV Shows & Anime",
                            subtitle = "Explore catalogs and optional external playback",
                            onClick = onExtras,
                            primary = false,
                            modeTag = "mode-extras",
                            icon = BousygramIcons.Anime,
                            tooltip = "Discover trending Movies, TV Shows, and Anime catalogs",
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("startup-mode-cards"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    ModeOption(
                        title = "Telegram Media",
                        subtitle = "Saved messages, channels, groups and your media library",
                        onClick = onTelegram,
                        primary = true,
                        modeTag = "mode-telegram",
                        icon = BousygramIcons.Telegram,
                        tooltip = "Browse and stream videos from your Telegram chats and channels"
                    )
                    ModeOption(
                        title = "Movies, TV Shows & Anime",
                        subtitle = "Explore catalogs and optional external web playback",
                        onClick = onExtras,
                        primary = false,
                        modeTag = "mode-extras",
                        icon = BousygramIcons.Anime,
                        tooltip = "Discover trending Movies, TV Shows, and Anime catalogs"
                    )
                    }
                }
                if (!compact) {
                    Text(
                        "TMDB and AniList provide metadata. External video playback is separate from Telegram.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    primary: Boolean,
    modeTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: BousygramIconRef = if (primary) BousygramIcons.Telegram else BousygramIcons.Anime,
    tooltip: String = subtitle
) {
    var focused by remember { mutableStateOf(false) }
    val motion = rememberBousygramMotionPolicy()
    val isShortWindow = rememberAdaptiveMetrics().isShortWindow
    // Keep both mode cards the same text-row height even when one title wraps.
    // Font scaling can still expand the cards instead of clipping the content.
    val subtitleLines = if (isShortWindow) 2 else 3
    val halo = LocalBousygramHaloColors.current
    val focusScale by animateFloatAsState(
        targetValue = if (focused && motion.animateFocusScale) BousygramUiTokens.focusScale else 1f,
        animationSpec = tween(
            durationMillis = motion.duration(BousygramUiTokens.focusDurationMs),
            easing = FastOutSlowInEasing
        ),
        label = "mode-focus"
    )
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(BousygramUiTokens.panelCorner)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(modeTag)
            .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = when {
                    focused -> halo.focusRing
                    primary -> halo.haloStart.copy(alpha = 0.55f)
                    else -> halo.glassBorder
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = if (focused) colors.surfaceContainerHighest else colors.surfaceContainer.copy(alpha = 0.94f),
        tonalElevation = if (focused) 6.dp else 1.dp
    ) {
        BousygramTooltipBox(
            tooltip = title,
            detail = tooltip,
            icon = icon,
            focused = focused,
            placement = BousygramTooltipPlacement.AUTO_BELOW
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = if (isShortWindow) 112.dp else 140.dp)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(BousygramUiTokens.cardCorner),
                    color = if (primary) colors.primaryContainer else colors.secondaryContainer,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (primary) halo.haloStart.copy(alpha = 0.45f) else halo.glassBorder
                    )
                ) {
                    Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        BousygramIcon(
                            icon = icon,
                            contentDescription = null,
                            tint = if (primary) colors.onPrimaryContainer else colors.onSecondaryContainer,
                            size = 26.dp
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        title, style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface, fontWeight = FontWeight.SemiBold,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        minLines = subtitleLines, maxLines = subtitleLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BousygramIcon(
                    icon = BousygramIcons.ChevronRight,
                    contentDescription = null,
                    tint = if (focused) halo.focusRing else colors.onSurfaceVariant,
                    size = 22.dp
                )
            }
        }
    }
}

/** Static offline placeholder: NO WebView, TMDB, AniList, network call, or virtual-mouse dependency. */
@Composable
fun ExtrasMaintenanceScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    PremiumGridBackdrop {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp), contentAlignment = Alignment.Center) {
            EnterprisePanel(
                modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BousygramBrandMark(size = 80.dp, showHalo = true)
                    Text(
                        "More to Explore",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Movies, TV shows and anime are available through Extras. External source availability varies.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Extras uses separate web sessions and does not access your Telegram account or playback cache.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    BousygramButton(
                        text = "Back to Mode Selection",
                        onClick = onBack,
                        selected = true,
                        leadingIcon = BousygramIcons.Back,
                        tooltip = "Return to experience picker",
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/** Real Telegram authorization readiness, not a fake timed progress bar. */
@Composable
fun TelegramStartupLoadingScreen(connectionStatus: String?) {
    val motion = rememberBousygramMotionPolicy()
    PremiumGridBackdrop {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp), contentAlignment = Alignment.Center) {
            EnterprisePanel(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    BousygramBrandMark(size = 88.dp, showHalo = true)
                    Text(
                        "Preparing Telegram Media",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (motion.animateIndeterminateProgress) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(BousygramUiTokens.pillCorner)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(BousygramUiTokens.pillCorner))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                        )
                    }
                    Text(
                        connectionStatus?.takeIf { it.isNotBlank() } ?: "Connecting securely to your Telegram session…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

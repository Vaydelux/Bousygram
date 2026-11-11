package com.vaydelux.bousygram.presentation.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.vaydelux.bousygram.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Route boundary only. Neither screen creates a Telegram or Extras API client. */
enum class BousygramMode { SELECT, TELEGRAM, EXTRAS_MAINTENANCE }

@Composable
fun BousygramModeSelection(
    onTelegram: () -> Unit,
    onExtras: () -> Unit
) {
    val metrics = rememberAdaptiveMetrics()
    val compact = metrics.isTv || metrics.widthDp > metrics.heightDp ||
        metrics.heightDp < 620.dp
    val horizontalCards = compact && metrics.widthDp >= 690.dp
    PremiumGridBackdrop {
        Box(
            Modifier.fillMaxSize().padding(
                horizontal = if (metrics.isTv) 32.dp else 18.dp,
                vertical = if (compact) 8.dp else 22.dp
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = if (horizontalCards) 1020.dp else 760.dp)
                    .fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)
            ) {
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BousygramBrandMark(size = 56.dp)
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
                    BousygramBrandMark(size = 82.dp)
                    Text(
                        "BOUSYGRAM  /  MEDIA HUB",
                        style = MaterialTheme.typography.labelMedium,
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
                        "Bousygram",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Choose an experience to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
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
                            modifier = Modifier.weight(1f)
                        )
                        ModeOption(
                            title = "Movies, TV Shows & Anime",
                            subtitle = "Explore catalogs and optional external playback",
                            onClick = onExtras,
                            primary = false,
                            modeTag = "mode-extras",
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    ModeOption(
                        title = "Telegram Media",
                        subtitle = "Saved messages, channels, groups and your media library",
                        onClick = onTelegram,
                        primary = true,
                        modeTag = "mode-telegram"
                    )
                    ModeOption(
                        title = "Movies, TV Shows & Anime",
                        subtitle = "Explore catalogs and optional external web playback",
                        onClick = onExtras,
                        primary = false,
                        modeTag = "mode-extras"
                    )
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
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused && android.animation.ValueAnimator.areAnimatorsEnabled())
            BousygramUiTokens.focusScale else 1f,
        animationSpec = tween(
            durationMillis = if (android.animation.ValueAnimator.areAnimatorsEnabled())
                BousygramUiTokens.focusDurationMs else 0,
            easing = FastOutSlowInEasing
        ),
        label = "mode-focus"
    )
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(BousygramUiTokens.cardCorner)
    Surface(
        modifier = modifier.fillMaxWidth().testTag(modeTag)
            .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) colors.primary
                else if (primary) colors.primary.copy(alpha = 0.55f)
                else colors.outlineVariant,
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = if (focused) colors.surfaceContainerHighest else colors.surfaceContainer,
        tonalElevation = if (focused) 4.dp else 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (primary) colors.primaryContainer else colors.secondaryContainer
            ) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (primary) "▶" else "✦",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (primary) colors.onPrimaryContainer
                            else colors.onSecondaryContainer
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title, style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface, fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall,
                color = if (focused) colors.primary else colors.onSurfaceVariant)
        }
    }
}

/** Static offline placeholder: NO WebView, TMDB, AniList, network call, or virtual-mouse dependency. */
@Composable
fun ExtrasMaintenanceScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    PremiumGridBackdrop {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp))
                        .padding(horizontal = 35.dp, vertical = 26.dp)
                ) {
                    BousygramBrandMark(size = 76.dp)
                }
                Text("More to Explore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
                Button(onClick = onBack, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Back to Mode Selection")
                }
            }
        }
    }
}

/** Real Telegram authorization readiness, not a fake timed progress bar. */
@Composable
fun TelegramStartupLoadingScreen(connectionStatus: String?) {
    PremiumGridBackdrop {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                BousygramBrandMark(size = 88.dp)
                Text(
                    "Preparing Telegram Media",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

package com.vaydelux.bousygram.presentation.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.R
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading
import com.vaydelux.bousygram.presentation.components.BousygramScaffold
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramDisplaySpec
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors

/**
 * About screen displaying application information, architectural foundation,
 * and the mandatory TMDB attribution notice as required by The Movie Database API terms.
 * Fully navigable via Android TV D-pad and touch interfaces.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val scrollState = rememberScrollState()
    val displaySpec = LocalBousygramDisplaySpec.current

    BousygramScaffold(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = displaySpec.maxReadingWidth)
                    .verticalScroll(scrollState)
                    .padding(horizontal = displaySpec.horizontalPadding, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Screen Top Header with Back action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BousygramPageHeading(
                        eyebrow = "Bousygram  /  System & Attribution",
                        title = "About BousyGram",
                        subtitle = "System Information & Attribution",
                        compact = displaySpec.isCompact,
                        icon = BousygramIcons.Info,
                        modifier = Modifier.weight(1f)
                    )
                    BousygramButton(
                        text = "Back",
                        onClick = onBack,
                        leadingIcon = BousygramIcons.Back,
                        tooltip = "Return to previous screen"
                    )
                }

                // App Banner Header
                AppHeaderCard()

                // Mandatory TMDB Attribution Notice Card
                TmdbAttributionCard()

                // Technology & Architecture Foundation Card
                ArchitectureDetailsCard()

                // Zero-Configuration Philosophy Notice Card
                ZeroConfigCard()

                // TV Navigation Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    BousygramButton(
                        text = "Return to Previous Screen",
                        onClick = onBack,
                        selected = true,
                        leadingIcon = BousygramIcons.Back,
                        tooltip = "Return to previous screen"
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppHeaderCard() {
    val halo = LocalBousygramHaloColors.current
    val displaySpec = LocalBousygramDisplaySpec.current
    EnterprisePanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (displaySpec.isCompact) 16.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (displaySpec.isCompact) 14.dp else 20.dp)
        ) {
            // Same approved Chow mascot shown by Android launcher, sign-in and README.
            BousygramBrandMark(
                size = if (displaySpec.isCompact) 52.dp else 64.dp,
                showHalo = true
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BousyGram",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(BousygramUiTokens.pillCorner),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, halo.glassBorder)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = halo.haloStart,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "Zero-Configuration Video-First Telegram Client for Android TV & Mobile",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Mandatory TMDB API Attribution Notice.
 * TMDB Terms of Use: "You shall use the TMDB logos to identify the source of the TMDB API and
 * TMDB Data... You must also include the following statement: 'This product uses the TMDB API
 * but is not endorsed or certified by TMDB.'"
 */
@Composable
private fun TmdbAttributionCard() {
    val halo = LocalBousygramHaloColors.current
    EnterprisePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TMDB logo is rendered from the sourced original geometry and gradient.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_tmdb_official),
                    contentDescription = "The Movie Database logo",
                    modifier = Modifier.size(width = 72.dp, height = 52.dp)
                )

                Text(
                    text = "The Movie Database",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Mandatory Legal Attribution Statement
            Surface(
                shape = RoundedCornerShape(BousygramUiTokens.cardCorner),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, halo.glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "All film and television metadata, posters, backdrops, synopses, and ratings displayed within Bousygram are matched automatically against The Movie Database (TMDB).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Caching & Offline Privacy Details
            Text(
                text = "• Zero-Configuration Matching: Filenames and captions are parsed on-device to resolve titles, years, seasons, and episodes without requiring user setup.\n" +
                    "• Offline Disk Cache: Metadata and high-definition posters/backdrops are cached locally to ensure instant rendering without network delays.\n" +
                    "• Seamless Fallback: When no TMDB match exists or while offline, Bousygram automatically falls back to Telegram thumbnails or embedded minithumbnails.",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchitectureDetailsCard() {
    val halo = LocalBousygramHaloColors.current
    EnterprisePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BousygramIcon(
                    icon = BousygramIcons.Decoder,
                    contentDescription = null,
                    tint = halo.haloStart,
                    size = 20.dp
                )
                Text(
                    text = "Engine & Platform Foundation",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FoundationChip(title = "Core", value = "TDLib Native C++")
                FoundationChip(title = "Primary Engine", value = "Media3 ExoPlayer")
                FoundationChip(title = "Fallback Engine", value = "libmpv")
                FoundationChip(title = "Local Server", value = "Ktor HTTP Loopback")
            }

            Text(
                text = "Protected by Android Keystore hardware-backed encryption. Streaming loopback tokens are scoped per-file with strict origin validation.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FoundationChip(title: String, value: String) {
    val halo = LocalBousygramHaloColors.current
    Surface(
        shape = RoundedCornerShape(BousygramUiTokens.tabCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, halo.glassBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, color = halo.haloStart, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ZeroConfigCard() {
    val halo = LocalBousygramHaloColors.current
    EnterprisePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BousygramIcon(
                    icon = BousygramIcons.Shield,
                    contentDescription = null,
                    tint = halo.haloStart,
                    size = 18.dp
                )
                Text(
                    text = "Zero-Configuration Philosophy",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Bousygram makes automatic playback, caching, and enrichment decisions behind the scenes so viewers never have to configure codecs, network timeouts, or API settings. Everything is designed for an effortless leanback TV experience.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
            )
        }
    }
}

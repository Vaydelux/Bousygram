package com.vaydelux.bousygram.presentation.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramScaffold
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.components.TvFocusSurface

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

    BousygramScaffold(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Screen Top Header with Back action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "About Bousygram",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "System Information & Attribution",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BousygramButton(
                    text = "← Back",
                    onClick = onBack
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
                    text = "← Return to Previous Screen",
                    onClick = onBack,
                    selected = true
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AppHeaderCard() {
    val shape = RoundedCornerShape(20.dp)
    EnterprisePanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Color(0xFF23354E), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App Icon Graphic
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF075985))
                        )
                    )
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "B",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Bousygram",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Zero-Configuration Video-First Telegram Client for Android TV & Mobile",
                    color = Color(0xFF94A3B8),
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
    val shape = RoundedCornerShape(20.dp)
    EnterprisePanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f), shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TMDB Logo Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF01B4E4), Color(0xFF90CEA1))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "TMDB",
                        color = Color(0xFF0D253F),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                }

                Text(
                    text = "The Movie Database",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Mandatory Legal Attribution Statement
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                        color = Color(0xFFF1F5F9),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "All film and television metadata, posters, backdrops, synopses, and ratings displayed within Bousygram are matched automatically against The Movie Database (TMDB).",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Caching & Offline Privacy Details
            Text(
                text = "• Zero-Configuration Matching: Filenames and captions are parsed on-device to resolve titles, years, seasons, and episodes without requiring user setup.\n" +
                    "• Offline Disk Cache: Metadata and high-definition posters/backdrops are cached locally to ensure instant rendering without network delays.\n" +
                    "• Seamless Fallback: When no TMDB match exists or while offline, Bousygram automatically falls back to Telegram thumbnails or embedded minithumbnails.",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
            )
        }
    }
}

@Composable
private fun ArchitectureDetailsCard() {
    val shape = RoundedCornerShape(20.dp)
    EnterprisePanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Color(0xFF23354E), shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Engine & Platform Foundation",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FoundationChip(title = "Core", value = "TDLib Native C++")
                FoundationChip(title = "Primary Engine", value = "Media3 ExoPlayer")
                FoundationChip(title = "Fallback Engine", value = "libmpv")
                FoundationChip(title = "Local Server", value = "Ktor HTTP Loopback")
            }

            Text(
                text = "Protected by Android Keystore hardware-backed encryption. Streaming loopback tokens are scoped per-file with strict origin validation.",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FoundationChip(title: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF131D2D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF21324B))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, color = Color(0xFF5EE5BE), style = MaterialTheme.typography.labelSmall)
            Text(value, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ZeroConfigCard() {
    val shape = RoundedCornerShape(20.dp)
    EnterprisePanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Color(0xFF1E293B), shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Zero-Configuration Philosophy",
                color = Color(0xFF38BDF8),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Bousygram makes automatic playback, caching, and enrichment decisions behind the scenes so viewers never have to configure codecs, network timeouts, or API settings. Everything is designed for an effortless leanback TV experience.",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
            )
        }
    }
}

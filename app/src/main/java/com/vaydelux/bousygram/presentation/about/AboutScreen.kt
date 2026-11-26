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
import com.vaydelux.bousygram.presentation.components.BousygramStatusPill
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.theme.LocalBousygramDisplaySpec

/**
 * About screen displaying application version, privacy/storage summary,
 * and the mandatory TMDB attribution notice required by TMDB API terms.
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BousygramPageHeading(
                        eyebrow = "",
                        title = "About Bousygram",
                        subtitle = "Version, privacy, and attribution",
                        compact = displaySpec.isCompact,
                        icon = BousygramIcons.Info,
                        modifier = Modifier.weight(1f)
                    )
                    BousygramButton(
                        text = "Back",
                        onClick = onBack,
                        leadingIcon = BousygramIcons.Back
                    )
                }

                AppHeaderCard()
                TmdbAttributionCard()
                PrivacyAndStorageCard()

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppHeaderCard() {
    val displaySpec = LocalBousygramDisplaySpec.current
    EnterprisePanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (displaySpec.isCompact) 16.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (displaySpec.isCompact) 14.dp else 18.dp)
        ) {
            // Same approved Chow mascot shown by Android launcher, sign-in and README.
            BousygramBrandMark(
                size = if (displaySpec.isCompact) 50.dp else 62.dp,
                squircleFrame = true
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
                        text = "Bousygram",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    BousygramStatusPill(
                        label = "v${BuildConfig.VERSION_NAME}",
                        accent = true
                    )
                }
                Text(
                    text = "Local-first Telegram video player for Android TV, tablet, and phone.",
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
    EnterprisePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
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
                        text = "Film and television metadata, posters, backdrops, synopses, and ratings in Extras are sourced from The Movie Database (TMDB).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Posters and metadata are cached locally for faster browsing. When offline or unmatched, Bousygram falls back to Telegram thumbnails.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
            )
        }
    }
}

@Composable
private fun PrivacyAndStorageCard() {
    EnterprisePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BousygramIcon(
                    icon = BousygramIcons.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 18.dp
                )
                Text(
                    text = "Privacy & local storage",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Telegram Media connects directly from this device using TDLib and stores credentials with Android Keystore encryption. Temporary media cache can be pruned anytime in Settings without removing resume positions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
            )
        }
    }
}

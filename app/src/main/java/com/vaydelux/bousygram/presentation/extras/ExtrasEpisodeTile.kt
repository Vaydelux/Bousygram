package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.runtime.remember
import android.os.Process
import com.vaydelux.bousygram.core.media.PlayerBufferConfig
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec

/**
 * One bounded episode card. Parent LazyColumn controls virtualization while
 * each row can show 1-2 cards depending on available window dp.
 * Playback remains delegated to the caller, never this UI component.
 */
@Composable
internal fun ExtrasEpisodeTile(
    episode: ExtrasEpisode,
    canPlay: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false
) {
    val style = LocalBousygramStyleSpec.current
    val context = LocalContext.current
    val target = remember(context) {
        ExtrasImagePolicy.backdrop(PlayerBufferConfig.isLowRamDevice(context) || !Process.is64Bit())
    }
    val stillRequest = remember(context, episode.still, target) {
        ImageRequest.Builder(context).data(episode.still).size(target.width, target.height)
            .precision(Precision.INEXACT).crossfade(false).build()
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("extras-episode-card-${episode.number}"),
        shape = RoundedCornerShape(style.cardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(if (isTv) 16.dp else 12.dp)) {
            val compactCard = maxWidth < 520.dp
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.width(if (compactCard) 100.dp else 160.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(style.tabCorner))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (episode.still.isNullOrBlank()) {
                            BousygramIcon(icon = BousygramIcons.Film, contentDescription = null,
                                size = 24.dp)
                        } else {
                            AsyncImage(model = stillRequest, contentDescription = null,
                                modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Episode ${episode.number}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            episode.title.ifBlank { "Episode ${episode.number}" },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            episode.overview.ifBlank { "No episode summary available." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (compactCard) 2 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (canPlay) {
                    OutlinedButton(
                        onClick = onPlay,
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (isTv) 56.dp else 48.dp)
                            .extrasFocusOutline().testTag("extras-play-episode")
                    ) {
                        BousygramIcon(icon = BousygramIcons.Play, contentDescription = null, size = 18.dp)
                        Text("Play episode ${episode.number}", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

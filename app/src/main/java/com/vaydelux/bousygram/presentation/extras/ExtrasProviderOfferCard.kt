package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Informational watch-provider tile: focusable to browse by D-pad, but
 * deliberately not clickable because TMDB availability is not a play URL.
 */
@Composable
internal fun ExtrasProviderOfferCard(
    offer: ExtrasWatchOffer,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(172.dp).heightIn(min = 60.dp)
            .extrasFocusOutline().focusable()
            .semantics {
                contentDescription = "${offer.providerName} — ${offer.method}. Availability listing, not a playback link"
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = offer.logo,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                offer.providerName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

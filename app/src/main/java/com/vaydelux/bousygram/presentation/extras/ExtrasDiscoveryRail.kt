package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Rails share the normal poster surface, bounded artwork and native D-pad focus. */
@Composable
internal fun ExtrasDiscoveryRail(model: ExtrasDiscoveryRailModel, isTv: Boolean,
    onOpen: (ExtrasItem) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("extras-discovery-rail-${model.key}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(model.label, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        LazyRow(Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(model.items, key = { "${it.type}:${it.id}" }) { item ->
                // Distinct rail namespace avoids ambiguous catalog automation tags.
                Column(Modifier.width(if (isTv) 152.dp else 132.dp)) {
                    ExtrasPosterCard(item, tagPrefix = "extras-rail-${model.key}", onClick = { onOpen(item) })
                }
            }
        }
    }
}

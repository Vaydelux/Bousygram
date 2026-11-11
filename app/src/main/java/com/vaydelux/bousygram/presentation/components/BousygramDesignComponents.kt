package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens

/** Shared information hierarchy for Telegram Library, Favorites and Extras. */
@Composable
fun BousygramPageHeading(
    eyebrow: String,
    title: String,
    subtitle: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.width(22.dp).height(3.dp).background(
                    Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )),
                    RoundedCornerShape(3.dp)
                )
            )
            Text(
                eyebrow.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            style = if (compact) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Opaque status token that remains readable in dark, light and high-contrast. */
@Composable
fun BousygramStatusPill(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(BousygramUiTokens.compactSpacing),
        color = if (accent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Shared navigation pill for Telegram, Extras, phone and Android TV.
 * Adapt only target sizing; use identical semantic colors and focus shape.
 */
@Composable
fun BousygramSectionTab(
    label: String,
    active: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = if (isTv) BousygramUiTokens.tvControlHeight
                else BousygramUiTokens.compactControlHeight)
            .semantics { selected = active }
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(BousygramUiTokens.tabCorner),
        border = BorderStroke(
            if (focused) BousygramUiTokens.tvFocusOutline else 1.dp,
            when {
                focused -> colors.primary
                active -> colors.primary.copy(alpha = 0.85f)
                else -> colors.outlineVariant
            }
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = when {
                active -> colors.primaryContainer
                focused -> colors.surfaceContainerHighest
                else -> colors.surfaceContainerLow
            },
            contentColor = if (active) colors.onPrimaryContainer else colors.onSurface
        ),
        contentPadding = PaddingValues(horizontal = if (isTv) 20.dp else 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = if (isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

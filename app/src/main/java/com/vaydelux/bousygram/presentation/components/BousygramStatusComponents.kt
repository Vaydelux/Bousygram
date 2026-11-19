package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens

/** Presentation-only severity; status content and recovery actions belong to the caller. */
enum class BousygramStatusTone { NEUTRAL, WARNING, ERROR }

/**
 * Small in-place banner for recoverable failures and partial-data notices.
 * Retains its caller's action button and native D-pad focus graph.
 * Never starts network requests, navigation, timers, or player operations.
 */
@Composable
fun BousygramInlineStatus(
    message: String,
    modifier: Modifier = Modifier,
    tone: BousygramStatusTone = BousygramStatusTone.NEUTRAL,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val background = when (tone) {
        BousygramStatusTone.NEUTRAL -> colors.surfaceContainerHigh
        BousygramStatusTone.WARNING -> colors.tertiaryContainer
        BousygramStatusTone.ERROR -> colors.errorContainer
    }
    val foreground = when (tone) {
        BousygramStatusTone.NEUTRAL -> colors.onSurface
        BousygramStatusTone.WARNING -> colors.onTertiaryContainer
        BousygramStatusTone.ERROR -> colors.onErrorContainer
    }
    val borderColor = when (tone) {
        BousygramStatusTone.NEUTRAL -> colors.outlineVariant
        BousygramStatusTone.WARNING -> colors.tertiary.copy(alpha = 0.55f)
        BousygramStatusTone.ERROR -> colors.error.copy(alpha = 0.65f)
    }
    val icon = when (tone) {
        BousygramStatusTone.NEUTRAL -> BousygramIcons.Info
        BousygramStatusTone.WARNING -> BousygramIcons.Warning
        BousygramStatusTone.ERROR -> BousygramIcons.Error
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bousygram-inline-status")
            .semantics {
                stateDescription = when (tone) {
                    BousygramStatusTone.NEUTRAL -> "Notice"
                    BousygramStatusTone.WARNING -> "Warning"
                    BousygramStatusTone.ERROR -> "Error"
                }
            },
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(BousygramUiTokens.cardCorner)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BousygramIcon(
                icon = icon,
                contentDescription = null,
                tint = foreground,
                size = 18.dp
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f).testTag("bousygram-inline-status-message"),
                color = foreground,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            trailingAction?.invoke()
        }
    }
}

/** Compact typography + Lucide icon status state. Callers retain their existing buttons and page geometry. */
@Composable
fun BousygramCenteredStatus(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: BousygramStatusTone = BousygramStatusTone.NEUTRAL
) {
    val colors = MaterialTheme.colorScheme
    val icon = when (tone) {
        BousygramStatusTone.NEUTRAL -> BousygramIcons.Info
        BousygramStatusTone.WARNING -> BousygramIcons.Warning
        BousygramStatusTone.ERROR -> BousygramIcons.Error
    }
    val badgeBg = when (tone) {
        BousygramStatusTone.NEUTRAL -> colors.surfaceContainerHigh
        BousygramStatusTone.WARNING -> colors.tertiaryContainer
        BousygramStatusTone.ERROR -> colors.errorContainer
    }
    val badgeFg = when (tone) {
        BousygramStatusTone.NEUTRAL -> colors.primary
        BousygramStatusTone.WARNING -> colors.onTertiaryContainer
        BousygramStatusTone.ERROR -> colors.onErrorContainer
    }
    Column(
        modifier = modifier.testTag("bousygram-centered-status"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            BousygramIcon(
                icon = icon,
                contentDescription = null,
                tint = badgeFg,
                size = 22.dp
            )
        }
        Text(
            text = title,
            color = if (tone == BousygramStatusTone.ERROR) colors.error else colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("bousygram-centered-status-title")
        )
        Text(
            text = message,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("bousygram-centered-status-message")
        )
    }
}

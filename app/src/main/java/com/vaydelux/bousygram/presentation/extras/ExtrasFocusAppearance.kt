package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens

/**
 * Extras uses the same theme tokens and focus outline as Telegram Media.
 *
 * Most Material3 actions are already keyboard-focusable, but their default
 * focus feedback varies between Android TV devices and themes. This modifier
 * draws an explicit ring on the actual focused control without installing a
 * second focus target or consuming D-pad key events.
 */
@Composable
internal fun Modifier.extrasFocusOutline(): Modifier {
    var hasRemoteFocus by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { hasRemoteFocus = it.hasFocus }
        .then(
            if (hasRemoteFocus) Modifier.border(
                width = BousygramUiTokens.tvFocusOutline,
                color = color,
                shape = RoundedCornerShape(BousygramUiTokens.tabCorner)
            ) else Modifier
        )
}

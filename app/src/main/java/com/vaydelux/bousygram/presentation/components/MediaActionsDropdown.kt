package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.data.repository.FavoritesRepository
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics

/** Shared touch, mouse, keyboard and D-pad action; no long-press dependency. */
@Composable
fun MediaActionsDropdown(media: MediaItem, modifier: Modifier = Modifier, privacyMode: Boolean = false,
    onActionFocus: (Boolean) -> Unit = {},
    actionFocusRequester: FocusRequester? = null,
    playbackFocusRequester: FocusRequester? = null) {
    val account by AccountSession.state.collectAsStateWithLifecycle()
    if (account == null) return
    val keys by FavoritesRepository.keys.collectAsStateWithLifecycle()
    val ready by FavoritesRepository.ready.collectAsStateWithLifecycle()
    val favorite = "${media.chatId}:${media.messageId}" in keys
    var expanded by remember(media.chatId, media.messageId, account) { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val localTrigger = remember { FocusRequester() }
    val trigger = actionFocusRequester ?: localTrigger
    val isTv = rememberAdaptiveMetrics().isTv
    val minimum = if (isTv) 56.dp else 48.dp
    LaunchedEffect(expanded, restoreFocus) {
        if (!expanded && restoreFocus) {
            trigger.requestFocus()
            restoreFocus = false
        }
    }
    fun dismiss() { expanded = false; restoreFocus = true }
    Box(modifier) {
        FilledTonalIconButton(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(minimum).focusRequester(trigger)
                .focusProperties { playbackFocusRequester?.let { left = it } }
                .onFocusChanged { focused = it.isFocused; onActionFocus(it.isFocused) }
                .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .semantics {
                contentDescription = if (privacyMode) "Media actions" else "Actions for ${media.title}"
            }
        ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = expanded, onDismissRequest = ::dismiss) {
            DropdownMenuItem(
                text = { Text(if (!ready) "Loading favorites..." else if (favorite) "Remove from favorites" else "Add to favorites") },
                enabled = ready,
                modifier = Modifier.heightIn(min = minimum),
                onClick = {
                    FavoritesRepository.setFavorite(media, !favorite)
                    dismiss()
                }
            )
        }
    }
}

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.data.repository.TelegramCloudFavorites
import com.vaydelux.bousygram.presentation.navigation.CloudFavoritesNavigation
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics

/** Shared touch, mouse, keyboard and D-pad action; no long-press dependency. */
@Composable
fun MediaActionsDropdown(media: MediaItem, modifier: Modifier = Modifier, privacyMode: Boolean = false,
    onActionFocus: (Boolean) -> Unit = {},
    actionFocusRequester: FocusRequester? = null,
    playbackFocusRequester: FocusRequester? = null,
    saveHandler: ((MediaItem, (Result<Unit>) -> Unit) -> Unit)? = null) {
    val account by AccountSession.state.collectAsStateWithLifecycle()
    if (account == null) return
    val pendingKeys by TelegramCloudFavorites.savingKeys.collectAsStateWithLifecycle()
    val recentKeys by TelegramCloudFavorites.recentKeys.collectAsStateWithLifecycle()
    val favoritesChannelId by TelegramCloudFavorites.channelId.collectAsStateWithLifecycle()
    val key = "${media.chatId}:${media.messageId}"
    val pending = key in pendingKeys
    val alreadySaved = key in recentKeys || media.chatId == favoritesChannelId
    var showAddedDialog by remember(media.chatId, media.messageId, account) { mutableStateOf(false) }
    var showAlreadySavedDialog by remember(media.chatId, media.messageId, account) { mutableStateOf(false) }
    var saveError by remember(media.chatId, media.messageId, account) { mutableStateOf<String?>(null) }
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
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft)
                        playbackFocusRequester?.requestFocus() ?: false
                    else false
                }
                .onFocusChanged { focused = it.isFocused; onActionFocus(it.isFocused) }
                .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .semantics {
                contentDescription = if (privacyMode) "Media actions" else "Actions for ${media.title}"
            }
        ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = expanded, onDismissRequest = ::dismiss) {
            DropdownMenuItem(
                text = { Text(when {
                    pending -> "Adding to Favorites..."
                    alreadySaved -> "Already in Favorites"
                    else -> "Add to favorites"
                }) },
                enabled = !pending && !alreadySaved,
                modifier = Modifier.heightIn(min = minimum),
                onClick = {
                    dismiss()
                    val callback: (Result<Unit>) -> Unit = { result ->
                        if (result.isSuccess) showAddedDialog = true
                        else saveError = result.exceptionOrNull()?.message ?: "Telegram could not save this video"
                    }
                    val injected = saveHandler
                    if (injected != null) injected(media, callback)
                    else TelegramCloudFavorites.enqueueSave(media) { receipt ->
                        if (receipt.getOrNull()?.alreadyExisted == true) showAlreadySavedDialog = true
                        else callback(receipt.map { Unit })
                    }
                }
            )
        }
    }
    if (showAddedDialog) {
        AlertDialog(
            onDismissRequest = { showAddedDialog = false },
            title = { Text("Added to Favorites") },
            text = { Text("Your media has been saved to your private Bousygram Favorites Telegram channel.") },
            confirmButton = {
                TextButton(onClick = {
                    showAddedDialog = false
                    CloudFavoritesNavigation.viewFavorites()
                }) { Text("View") }
            },
            dismissButton = {
                TextButton(onClick = { showAddedDialog = false }) { Text("Done") }
            }
        )
    }
    if (showAlreadySavedDialog) {
        AlertDialog(
            onDismissRequest = { showAlreadySavedDialog = false },
            title = { Text("Already in Favorites") },
            text = { Text("This media is already saved in your private Telegram Favorites channel.") },
            confirmButton = {
                TextButton(onClick = {
                    showAlreadySavedDialog = false
                    CloudFavoritesNavigation.viewFavorites()
                }) { Text("View") }
            },
            dismissButton = {
                TextButton(onClick = { showAlreadySavedDialog = false }) { Text("Done") }
            }
        )
    }
    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("Couldn't add to Favorites") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { saveError = null }) { Text("OK") }
            }
        )
    }
}

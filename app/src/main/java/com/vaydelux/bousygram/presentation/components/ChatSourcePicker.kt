package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.domain.model.ChatSourceKind
import com.vaydelux.bousygram.domain.model.SAVED_MESSAGES_TITLE
import com.vaydelux.bousygram.domain.model.VideoChatItem

/** Horizontal source tabs with a searchable full picker for accounts with many chats. */
@Composable
fun ChatSourceTabs(
    chats: List<VideoChatItem>,
    savedSelected: Boolean,
    selectedChatId: Long?,
    onSelectSavedMessages: () -> Unit,
    onSelectChat: (VideoChatItem) -> Unit,
    onOpenAllChats: () -> Unit,
    modifier: Modifier = Modifier,
    pinnedChatIds: Set<Long> = emptySet()
) {
    val otherChats = remember(chats, pinnedChatIds) {
        chats.asSequence()
            .filterNot { it.isSavedMessages }
            .distinctBy { it.chatId }
            .sortedWith(compareByDescending<VideoChatItem> { pinnedChatIds.contains(it.chatId) }
                .thenBy { it.isArchived })
            .toList()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(vertical = 5.dp)
        ) {
            item(key = "saved-messages-source") {
                SourceTab(
                    title = "Saved Messages",
                    category = "PERSONAL",
                    selected = savedSelected,
                    pinned = false,
                    onClick = onSelectSavedMessages
                )
            }
            items(otherChats, key = { it.chatId }) { chat ->
                SourceTab(
                    title = chat.title,
                    category = chat.kind.label.uppercase(),
                    selected = !savedSelected && selectedChatId == chat.chatId,
                    pinned = pinnedChatIds.contains(chat.chatId),
                    archived = chat.isArchived,
                    onClick = { onSelectChat(chat) }
                )
            }
        }
        SourceTab(
            title = "All chats",
            category = "PICKER",
            selected = false,
            pinned = false,
            onClick = onOpenAllChats,
            emphasized = true
        )
    }
}

@Composable
private fun SourceTab(
    title: String,
    category: String,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    archived: Boolean = false,
    emphasized: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val borderColor = when {
        focused -> MaterialTheme.colorScheme.tertiary
        selected -> MaterialTheme.colorScheme.primary
        emphasized -> MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.56f)
    }
    Surface(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$title, $category" +
                    if (selected) ", selected" else ""
                this.selected = selected
            }
            .clip(shape)
            .border(if (focused || selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        shape = shape,
        color = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            focused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
            emphasized -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pinned) Text("★", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 210.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = if (archived) "ARCHIVED · $category" else category,
                color = if (selected || focused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

private enum class SourceFilter(val title: String) {
    ALL("All"), CHANNELS("Channels"), GROUPS("Groups"), PRIVATE("Private chats"), ARCHIVED("Archived")
}

@Composable
fun AllChatsPickerDialog(
    chats: List<VideoChatItem>,
    selectedChatId: Long?,
    savedSelected: Boolean,
    pinnedChatIds: Set<Long>,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onSelectSavedMessages: () -> Unit,
    onSelectChat: (VideoChatItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SourceFilter.ALL) }
    // Keep scroll position and D-pad focus stable when TDLib adds chats during loading.
    val gridState = rememberLazyGridState()
    val visibleChats = remember(chats, query, filter, pinnedChatIds) {
        chats.asSequence()
            .filter { chat ->
                when (filter) {
                    SourceFilter.ALL -> true
                    SourceFilter.CHANNELS -> chat.kind == ChatSourceKind.CHANNEL
                    SourceFilter.GROUPS -> chat.kind == ChatSourceKind.GROUP
                    SourceFilter.PRIVATE -> chat.kind == ChatSourceKind.PRIVATE || chat.kind == ChatSourceKind.SECRET
                    SourceFilter.ARCHIVED -> chat.isArchived
                }
            }
            .filter { it.title.contains(query.trim(), ignoreCase = true) }
            .distinctBy { it.chatId }
            .sortedWith(compareByDescending<VideoChatItem> { pinnedChatIds.contains(it.chatId) }
                .thenBy { it.isArchived }
                .thenBy { it.title.lowercase() })
            .toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose a video source", style = MaterialTheme.typography.headlineSmall)
                Text("Saved Messages, private chats, groups, and channels", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search chats") },
                    placeholder = { Text("Type a chat, group, or channel name") }
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SourceFilter.entries, key = { it.name }) { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.title) }
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 206.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoading) {
                        item(key = "loading-sources") {
                            Text(
                                "Loading Telegram chats…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    if (filter == SourceFilter.ALL && SAVED_MESSAGES_TITLE.contains(query, ignoreCase = true)) {
                        item(key = "saved-messages-dialog") {
                            SourcePickerRow(
                                title = "Saved Messages",
                                subtitle = "PERSONAL LIBRARY",
                                selected = savedSelected,
                                pinned = false,
                                onClick = onSelectSavedMessages
                            )
                        }
                    }
                    gridItems(visibleChats.filterNot { it.isSavedMessages }, key = { it.chatId }) { chat ->
                        SourcePickerRow(
                            title = chat.title,
                            subtitle = buildString {
                                append(chat.kind.label.uppercase())
                                if (chat.isArchived) append(" · ARCHIVED")
                            },
                            selected = !savedSelected && selectedChatId == chat.chatId,
                            pinned = pinnedChatIds.contains(chat.chatId),
                            onClick = { onSelectChat(chat) }
                        )
                    }
                    if (visibleChats.isEmpty() &&
                        (filter != SourceFilter.ALL || !SAVED_MESSAGES_TITLE.contains(query, ignoreCase = true))
                    ) {
                        item(key = "empty-chats") {
                            Text(
                                "No matching chats",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}


@Composable
private fun SourcePickerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$title, $subtitle" +
                    (if (pinned) ", pinned" else "") +
                    (if (selected) ", selected" else "")
                this.selected = selected
            }
            .clip(shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.56f), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        color = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            focused -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            if (pinned) Text("★", color = MaterialTheme.colorScheme.tertiary)
            if (selected) Text("Selected", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

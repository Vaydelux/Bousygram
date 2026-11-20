package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.core.media.MediaMetadataParser
import com.vaydelux.bousygram.data.repository.TelegramMetadataCorrections
import com.vaydelux.bousygram.data.repository.TmdbCorrectionCandidate
import com.vaydelux.bousygram.data.repository.TmdbCorrectionSearch
import com.vaydelux.bousygram.data.repository.TmdbRepository
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicit, opt-in TMDB correction for one Telegram message. Safe on touch,
 * keyboard and D-pad. Searching/choosing never starts media playback.
 */
@Composable
fun TelegramMetadataCorrectionDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = rememberAdaptiveMetrics().isTv
    val minimum = if (isTv) 56.dp else 48.dp
    val parsed = remember(item.chatId, item.messageId) {
        MediaMetadataParser.parse(item.title, fileName = item.fileName)
    }
    var title by remember(item.chatId, item.messageId) {
        mutableStateOf(item.tmdbTitle ?: parsed.title)
    }
    var yearText by remember(item.chatId, item.messageId) {
        mutableStateOf((item.releaseYear ?: parsed.year)?.toString().orEmpty())
    }
    var isSeries by remember(item.chatId, item.messageId) {
        mutableStateOf(parsed.isSeries)
    }
    var results by remember(item.chatId, item.messageId) {
        mutableStateOf(TmdbCorrectionSearch())
    }
    var busy by remember(item.chatId, item.messageId) { mutableStateOf(false) }
    var error by remember(item.chatId, item.messageId) { mutableStateOf<String?>(null) }
    val existing = remember(item.chatId, item.messageId) {
        runCatching { TelegramMetadataCorrections.read(context, item) }
    }
    val hasUnreadableOverride = existing.isFailure

    fun search() {
        if (busy) return
        val year = yearText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (yearText.isNotBlank() && (year == null || year !in 1900..2099)) {
            error = "Enter a valid release year, or leave the year empty."
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                results = TmdbRepository.searchCorrectionCandidates(title.trim(), year, isSeries)
                error = results.message
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = "Metadata search failed. Your original video is unaffected."
            } finally {
                busy = false
            }
        }
    }

    fun save(candidate: TmdbCorrectionCandidate) {
        if (busy || hasUnreadableOverride) return
        busy = true
        error = null
        scope.launch {
            try {
                // A provider outage must not invalidate an explicitly chosen
                // search result. The candidate includes partial public fields.
                val detail = TmdbRepository.resolveCorrectionCandidate(candidate)
                val saved = withContext(Dispatchers.IO) {
                    TelegramMetadataCorrections.choose(context, item, candidate, detail)
                }
                if (saved) onChanged()
                else error = "Could not save this selection. Check account and storage."
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = "Unable to save the correction. Existing metadata was preserved."
            } finally {
                busy = false
            }
        }
    }

    fun change(skip: Boolean) {
        if (busy || hasUnreadableOverride) return
        busy = true
        error = null
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    if (skip) TelegramMetadataCorrections.skip(context, item)
                    else TelegramMetadataCorrections.clear(context, item)
                }
                if (saved) onChanged()
                else error = "Unable to change this correction. Check account and storage."
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = "Metadata changes could not be saved."
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text("Identify or correct this video",
                style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = if (isTv) 470.dp else 410.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("telegram-metadata-correction"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Searches send only the title and year you enter to TMDB after you enable " +
                        "discovery in Settings. Your Telegram file, filename, message and chat IDs stay private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasUnreadableOverride) {
                    Text("Stored correction is unreadable. It has not been erased.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("telegram-metadata-storage-error"))
                } else if (existing.getOrNull()?.skip == true) {
                    Text("Automatic matching is disabled for this video.",
                        style = MaterialTheme.typography.labelMedium)
                } else if (existing.getOrNull() != null) {
                    Text("Selected: " + existing.getOrNull()?.title.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    modifier = Modifier.fillMaxWidth().testTag("telegram-metadata-title"),
                    singleLine = true,
                    label = { Text("Movie or series title") }
                )
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth().testTag("telegram-metadata-year"),
                    singleLine = true,
                    label = { Text("Release year (optional)") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isSeries,
                        onClick = { isSeries = false; results = TmdbCorrectionSearch() },
                        label = { Text("Movie") },
                        modifier = Modifier.heightIn(min = minimum)
                    )
                    FilterChip(
                        selected = isSeries,
                        onClick = { isSeries = true; results = TmdbCorrectionSearch() },
                        label = { Text("TV series") },
                        modifier = Modifier.heightIn(min = minimum)
                    )
                }
                Button(
                    onClick = ::search,
                    enabled = !busy && !hasUnreadableOverride && title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = minimum)
                        .testTag("telegram-metadata-search")
                ) { Text(if (busy) "Working…" else "Find TMDB matches") }
                if (busy) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(6.dp))
                        Text("Please keep this screen open", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (error != null) {
                    Text(error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("telegram-metadata-error"))
                }
                if (results.candidates.isNotEmpty()) {
                    Text("Choose the correct title — nothing is auto-selected",
                        fontWeight = FontWeight.SemiBold)
                    results.candidates.forEach { match ->
                        OutlinedButton(
                            onClick = { save(match) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = minimum)
                                .testTag("telegram-metadata-candidate-" + match.id)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(match.title, maxLines = 2)
                                Text(
                                    listOfNotNull(match.year?.toString(),
                                        if (match.isSeries) "TV" else "Movie")
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { change(skip = true) },
                    enabled = !busy && !hasUnreadableOverride,
                    modifier = Modifier.fillMaxWidth().heightIn(min = minimum)
                        .testTag("telegram-metadata-do-not-match")
                ) { Text("Don't match this video") }
                if (existing.getOrNull() != null) {
                    TextButton(
                        onClick = { change(skip = false) },
                        enabled = !busy && !hasUnreadableOverride,
                        modifier = Modifier.fillMaxWidth().heightIn(min = minimum)
                            .testTag("telegram-metadata-clear-correction")
                    ) { Text("Restore original metadata") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") }
        }
    )
}

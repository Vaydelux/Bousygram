package com.vaydelux.bousygram.presentation.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Route boundary only. Neither screen creates a Telegram or Extras API client. */
enum class BousygramMode { SELECT, TELEGRAM, EXTRAS_MAINTENANCE }

@Composable
fun BousygramModeSelection(
    onTelegram: () -> Unit,
    onExtras: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("BOUSYGRAM", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    "Your media. Your way.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Choose how you want to explore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                ModeOption(
                    title = "Telegram Media",
                    subtitle = "Saved messages, channels, groups and your media library",
                    onClick = onTelegram,
                    primary = true
                )
                ModeOption(
                    title = "Movies, TV Shows & Anime",
                    subtitle = "Extras — coming soon",
                    onClick = onExtras,
                    primary = false
                )
                Text(
                    "Extras is not available yet. Telegram Media remains separate and fully supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ModeOption(title: String, subtitle: String, primary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .border(
            if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            shape
        )
    if (primary) {
        Button(
            modifier = modifier,
            shape = shape,
            onClick = onClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        OutlinedButton(
            modifier = modifier,
            shape = shape,
            onClick = onClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Static offline placeholder: NO WebView, TMDB, AniList, network call, or virtual-mouse dependency. */
@Composable
fun ExtrasMaintenanceScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp))
                        .padding(horizontal = 35.dp, vertical = 26.dp)
                ) {
                    Text("✦", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text("More to Explore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    "Movies, TV shows and anime experiences are currently in progress.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Telegram Media is ready to use. This upcoming section is isolated from your Telegram account and playback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBack, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Back to Mode Selection")
                }
            }
        }
    }
}

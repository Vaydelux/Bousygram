package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** User-initiated site check, available directly on Android TV without ADB. */
@Composable
internal fun ExtrasServerConnectionCheck(
    url: String,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    val checker = remember { ExtrasEndpointProbe() }
    val scope = rememberCoroutineScope()
    var pending by remember(url) { mutableStateOf(false) }
    var observation by remember(url) { mutableStateOf<ExtrasEndpointObservation?>(null) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            enabled = !pending,
            onClick = {
                if (!pending) {
                    pending = true
                    observation = null
                    scope.launch {
                        try {
                            observation = checker.check(url)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // The source URL is already public/validated by the
                            // provider catalog. Never log it or expose exceptions.
                            observation = ExtrasEndpointObservation.INCONCLUSIVE
                        } finally {
                            pending = false
                        }
                    }
                }
            },
            modifier = Modifier
                .heightIn(min = if (isTv) 48.dp else 40.dp)
                .testTag("extras-server-check")
        ) {
            Text(if (pending) "Checking…" else "Check server")
        }
        if (pending) {
            Text(
                "Checking this site's HTTPS response…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        } else observation?.let { result ->
            Text(
                describeExtrasEndpointObservation(result),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("extras-server-check-result")
            )
        }
    }
}

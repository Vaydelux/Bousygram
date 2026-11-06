package com.vaydelux.bousygram.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/**
 * Safe TV overscan margin tokens (5% minimum safe margins for 10-foot 1080p/4K TVs).
 */
object TvOverscanMargins {
    val horizontal: Dp = 32.dp
    val vertical: Dp = 24.dp
    val compactHorizontal: Dp = 16.dp
    val compactVertical: Dp = 12.dp
}

/**
 * Centralized Focus Coordinator that tracks and restores last-focused stable keys across screens.
 * Solves:
 * 1. Lost focus when returning from dialogs, menus, or nested screens.
 * 2. Focus traps where D-pad gets stuck on unhandled composables.
 * 3. Inconsistent focus landing on initial screen load.
 */
class FocusCoordinator {
    private val lastFocusedByKey = LinkedHashMap<String, String>(64, 0.75f, true)
    // FocusRequesters must remain stable while active Compose nodes are attached.
    private val requesters = ConcurrentHashMap<String, FocusRequester>()

    internal fun rememberedScreenCount(): Int =
        synchronized(lastFocusedByKey) { lastFocusedByKey.size }

    private companion object {
        const val MAX_RESTORED_SCREENS = 64
    }

    fun recordFocus(screenKey: String, itemKey: String) {
        synchronized(lastFocusedByKey) {
            lastFocusedByKey[screenKey] = itemKey
            while (lastFocusedByKey.size > MAX_RESTORED_SCREENS) {
                lastFocusedByKey.remove(lastFocusedByKey.keys.first())
            }
        }
    }

    fun getLastFocusedKey(screenKey: String, fallbackKey: String): String =
        synchronized(lastFocusedByKey) { lastFocusedByKey[screenKey] ?: fallbackKey }

    fun getOrCreateRequester(key: String): FocusRequester {
        return requesters.computeIfAbsent(key) { FocusRequester() }
    }

    fun requestFocus(key: String): Boolean {
        // Missing or unattached focus nodes are not successful D-pad focus.
        return runCatching { requesters[key]?.requestFocus() == true }.getOrDefault(false)
    }

    fun clearScreen(screenKey: String) {
        synchronized(lastFocusedByKey) { lastFocusedByKey.remove(screenKey) }
    }
}

@Composable
fun rememberFocusCoordinator(): FocusCoordinator {
    return remember { FocusCoordinator() }
}

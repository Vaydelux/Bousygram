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
    private val lastFocusedByKey = ConcurrentHashMap<String, String>()
    private val requesters = ConcurrentHashMap<String, FocusRequester>()

    fun recordFocus(screenKey: String, itemKey: String) {
        lastFocusedByKey[screenKey] = itemKey
    }

    fun getLastFocusedKey(screenKey: String, fallbackKey: String): String {
        return lastFocusedByKey[screenKey] ?: fallbackKey
    }

    fun getOrCreateRequester(key: String): FocusRequester {
        return requesters.computeIfAbsent(key) { FocusRequester() }
    }

    fun requestFocus(key: String): Boolean {
        return runCatching {
            requesters[key]?.requestFocus()
            true
        }.getOrDefault(false)
    }

    fun clearScreen(screenKey: String) {
        lastFocusedByKey.remove(screenKey)
    }
}

@Composable
fun rememberFocusCoordinator(): FocusCoordinator {
    return remember { FocusCoordinator() }
}

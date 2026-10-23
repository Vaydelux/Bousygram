package com.vaydelux.bousygram.core.tdlib.cache

/**
 * Delegating stub retained for any legacy binary references.
 * Prefer using [TelegramNativeFileGc] directly.
 */
object TelegramStorageOptimizerLegacy {
    fun enableStorageOptimizer() = TelegramNativeFileGc.enableStorageOptimizer()
    fun optimizeStorage() = TelegramNativeFileGc.optimizeStorage()
}

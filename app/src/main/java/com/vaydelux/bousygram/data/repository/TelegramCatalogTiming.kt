package com.vaydelux.bousygram.data.repository

import android.util.Log
import com.vaydelux.bousygram.BuildConfig

/**
 * Lightweight, redacted diagnostics for signed-in-device performance review.
 * Stages are compile-time enum values; logs cannot contain chat IDs, titles,
 * file names, source URLs, phone numbers, credentials or raw TDLib errors.
 * Log only on debug builds; no persistent telemetry or background uploads.
 */
internal enum class TelegramCatalogStage {
    SOURCE_FIRST_PAGE,
    SOURCE_SCAN_DONE,
    TOPIC_FIRST_PAGE,
    TOPIC_NEXT_PAGE,
    MEDIA_FIRST_CARD
}

internal enum class TelegramCatalogOutcome { READY, EMPTY, PARTIAL, FAILED }

internal object TelegramCatalogTiming {
    private const val TAG = "TelegramCatalogTiming"

    fun nowNanos(): Long = System.nanoTime()

    fun format(
        stage: TelegramCatalogStage,
        elapsedMs: Long,
        resultCount: Int,
        outcome: TelegramCatalogOutcome
    ): String = "stage=${stage.name} elapsedMs=${elapsedMs.coerceIn(0, 600_000)} " +
        "resultCount=${resultCount.coerceIn(0, 10_000)} outcome=${outcome.name}"

    fun record(
        stage: TelegramCatalogStage,
        startedNanos: Long,
        resultCount: Int,
        outcome: TelegramCatalogOutcome
    ) {
        if (!BuildConfig.DEBUG) return
        val elapsedMs = ((nowNanos() - startedNanos).coerceAtLeast(0L) / 1_000_000L)
        try {
            Log.d(TAG, format(stage, elapsedMs, resultCount, outcome))
        } catch (_: RuntimeException) {
            // JVM fixture runners don't have android.util.Log.
        }
    }
}

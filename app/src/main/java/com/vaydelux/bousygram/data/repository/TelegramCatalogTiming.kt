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

    private const val WINDOW_SIZE = 64
    private const val REPORT_INTERVAL = 20
    private val windows = TelegramCatalogStage.entries.associateWith { ArrayDeque<Long>() }
    private val observations = TelegramCatalogStage.entries.associateWith { 0 }.toMutableMap()

    /** In-memory, bounded and identifier-free diagnostic summary. */
    @Synchronized
    internal fun observe(stage: TelegramCatalogStage, elapsedMs: Long): String? {
        val window = windows.getValue(stage)
        if (window.size == WINDOW_SIZE) window.removeFirst()
        window.addLast(elapsedMs.coerceIn(0, 600_000))
        val count = observations.getValue(stage) + 1
        observations[stage] = count
        if (count % REPORT_INTERVAL != 0) return null
        val sorted = window.sorted()
        fun percentile(percent: Int): Long =
            sorted[((sorted.size - 1) * percent + 99) / 100]
        return "stage=${stage.name} samples=${sorted.size} " +
            "p50Ms=${percentile(50)} p95Ms=${percentile(95)}"
    }

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
            observe(stage, elapsedMs)?.let { Log.d(TAG, it) }
        } catch (_: RuntimeException) {
            // JVM fixture runners don't have android.util.Log.
        }
    }
}

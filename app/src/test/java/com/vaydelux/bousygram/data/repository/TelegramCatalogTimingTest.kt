package com.vaydelux.bousygram.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramCatalogTimingTest {
    @Test fun diagnosticsContainOnlyFixedStageNamesAndBoundedCounts() {
        val output = TelegramCatalogTiming.format(
            stage = TelegramCatalogStage.TOPIC_FIRST_PAGE,
            elapsedMs = 900_000,
            resultCount = 30_000,
            outcome = TelegramCatalogOutcome.PARTIAL
        )
        assertEquals(
            "stage=TOPIC_FIRST_PAGE elapsedMs=600000 resultCount=10000 outcome=PARTIAL",
            output
        )
        assertFalse(output.contains("chat"))
        assertFalse(output.contains("file"))
        assertFalse(output.contains("token"))
    }

    @Test fun stagesUseOnlyFixedEnumsAndNoSensitiveInput() {
        for (stage in TelegramCatalogStage.entries) {
            val output = TelegramCatalogTiming.format(
                stage, elapsedMs = -5, resultCount = -1,
                outcome = TelegramCatalogOutcome.READY
            )
            assertTrue(output.startsWith("stage=${stage.name} "))
            assertTrue(output.contains("elapsedMs=0 resultCount=0"))
        }
    }
    @Test fun latencySummaryIsBoundedAndRedacted() {
        val stage = TelegramCatalogStage.MEDIA_FIRST_CARD
        repeat(19) { index ->
            assertEquals(null, TelegramCatalogTiming.observe(stage, index.toLong()))
        }
        val summary = TelegramCatalogTiming.observe(stage, 900_000)
        assertEquals("stage=MEDIA_FIRST_CARD samples=20 p50Ms=10 p95Ms=600000", summary)
        assertFalse(summary!!.contains("chat"))
        assertFalse(summary.contains("file"))
        assertFalse(summary.contains("token"))
    }

}

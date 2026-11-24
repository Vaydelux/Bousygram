import com.vaydelux.bousygram.core.memory.mergeBoundedOrderedWindow
import com.vaydelux.bousygram.core.media.runIndependentStreamingLockActions
import com.vaydelux.bousygram.core.tdlib.utils.boundedPreviewBucketMs
import com.vaydelux.bousygram.data.repository.ChatDirectoryProgress
import com.vaydelux.bousygram.presentation.extras.*
import com.vaydelux.bousygram.ui.adaptive.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

private data class Item(val id: Int, val revision: Int)

fun main() = runBlocking {
    var checks = 0
    fun verify(ok: Boolean) { check(ok) { "Assertion ${checks + 1} failed" }; checks++ }
    val random = Random(233)
    repeat(400) {
        val limit = random.nextInt(1, 241)
        val existing = List(random.nextInt(0, 400)) { Item(random.nextInt(300), it) }
        val incoming = List(random.nextInt(0, 600)) { Item(random.nextInt(300), it + 1000) }
        val reference = linkedMapOf<Int, Item>()
        (existing + incoming).forEach { reference[it.id] = it }
        val merged = mergeBoundedOrderedWindow(existing, incoming, limit) { it.id }
        verify(merged.items == reference.values.toList().takeLast(limit))
        verify(merged.removedFromFront == (reference.size - limit).coerceAtLeast(0))
    }
    val windows = listOf(320 to 568, 360 to 800, 412 to 915, 640 to 360,
        844 to 390, 600 to 960, 768 to 1024, 1024 to 768, 960 to 540,
        1280 to 720, 1280 to 800, 1920 to 1080)
    var configurations = 0
    for ((w, h) in windows) for (tv in listOf(false, true)) for (scale in listOf(1f, 1.15f, 1.3f, 1.6f, 2f)) {
        val grid = ExtrasLayoutPolicy.resolve(w.toFloat(), h.toFloat(), tv, scale)
        val detail = ExtrasDetailLayoutPolicy.resolve(w.toFloat(), h.toFloat(), tv, scale)
        val adaptive = AdaptiveUi.resolve(w, h, tv, isHighContrast = true)
        verify(grid.minControlHeightDp == if (tv) 56 else 48)
        verify(grid.horizontalGutterDp >= 12 && grid.searchFilterGapDp >= 8)
        verify(grid.gridCardMinWidthDp > 0 && grid.gridGapDp >= 8)
        verify(!tv || (!grid.landscapeToolbar && !grid.compactHeader))
        verify(detail.controlHeightDp >= if (tv) 56 else 48)
        verify(detail.posterWidthDp > 0 && detail.backdropHeightDp > 0)
        verify(detail.episodeColumns in 1..2)
        verify(!tv || adaptive.navigationMode == NavigationMode.TV_RAIL)
        verify(adaptive.mediaColumns in 1..(if (tv) 6 else 5))
        verify(!adaptive.allowDecorativePatterns)
        configurations++
    }
    val progress = ChatDirectoryProgress()
    repeat(3) { verify(progress.allowLoadMore(1)) }
    verify(!progress.allowLoadMore(1))
    verify(progress.allowLoadMore(2))
    verify(catalogCacheEntryKiB(256, 257) == 2)
    verify(catalogCacheEntryKiB(Int.MAX_VALUE, Int.MAX_VALUE) == 8_388_608)
    verify(catalogDeadline(Long.MAX_VALUE - 1, 2) == Long.MAX_VALUE)
    verify(boundedPreviewBucketMs(-1, 0) == 0L)
    verify(boundedPreviewBucketMs(29_999, 25_000) == 20_000L)
    verify(boundedPreviewBucketMs(Long.MAX_VALUE, 0) <= Long.MAX_VALUE / 1000)
    var calls = 0
    runIndependentStreamingLockActions({ error("first") }, { calls++ }, { error("reporter") })
    verify(calls == 1)
    var frames = 0
    verify(restoreExtrasFocus({ frames++ }, { frames == 2 }, { error("fallback") }))
    verify(frames == 2)
    frames = 0
    verify(restoreExtrasFocus({ frames++ }, { false }, { true }))
    verify(frames == 4)
    verify(!restoreExtrasFocus({}, { false }, { false }))
    verify(runCatching { restoreExtrasFocus({ throw CancellationException("fixture") }, { true }, { true }) }
        .exceptionOrNull() is CancellationException)
    println("PASS $checks assertions across 400 catalog overlap cases and $configurations viewport/font/device configurations")
}

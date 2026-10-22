"""Source-contract checks for metadata-first TDLib media loading performance."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


class MediaLoadingPerformanceTests(unittest.TestCase):
    def test_first_page_is_progressive_not_blocked_by_slowest_filter(self):
        search = read("app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesMediaSearch.kt")
        self.assertIn("searchMediaProgressiveInternal", search)
        self.assertIn("publish(sequence, snapshot, 0L, aggregatedError, false)", search)
        self.assertIn("synchronized(publishLock)", search)
        self.assertIn("if (sequence <= latestPublishedSequence) return", search)
        self.assertIn("aggregatedError = firstError.takeIf { snapshot.isEmpty() }", search)
        self.assertIn("error.takeIf { finalItems.isEmpty() }", search)
        self.assertIn("isFinal", search)

    def test_duplicate_photo_and_video_query_is_removed_for_video_only_library(self):
        search = read("app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesMediaSearch.kt")
        self.assertNotIn("TdApi.SearchMessagesFilterPhotoAndVideo()", search)
        for query in (
            "TdApi.SearchMessagesFilterVideo()",
            "TdApi.SearchMessagesFilterDocument()",
            "TdApi.SearchMessagesFilterVideoNote()",
            "TdApi.SearchMessagesFilterAnimation()",
        ):
            self.assertIn(query, search)

    def test_search_query_budget_is_bounded(self):
        plan = read("app/src/main/java/com/vaydelux/bousygram/data/repository/MediaSearchPlan.kt")
        self.assertIn("pageLimit = pageLimit", plan)
        self.assertIn("documentLimit = (pageLimit * 2).coerceIn(pageLimit, 100)", plan)
        self.assertIn("filterCount: Int = 4", plan)

    def test_thumbnail_downloads_are_bounded_low_priority_and_async(self):
        repository = read("app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesUtils.kt")
        self.assertIn("MAX_CONCURRENT_THUMBNAIL_DOWNLOADS = 3", repository)
        self.assertIn("THUMBNAIL_DOWNLOAD_PRIORITY = 1", repository)
        self.assertIn("TdApi.DownloadFile(fileId, THUMBNAIL_DOWNLOAD_PRIORITY, 0, 0, false)", repository)
        self.assertIn("THUMBNAIL_DOWNLOAD_TIMEOUT_SECONDS = 12L", repository)
        self.assertIn("Coalesce requests", repository)
        self.assertIn("if (completed.get()) return@send", repository)
        self.assertIn("TdLibClient.removeUpdateHandler(handler)", repository)

    def test_tv_focus_no_longer_downloads_video_bytes(self):
        vm = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/vm/MediaViewModel.kt")
        start = vm.index("fun onItemFocused(item: MediaItem)")
        end = vm.index("private fun updateThumbnailPathForFile", start)
        focused = vm[start:end]
        self.assertIn("Do not download the first 2 MiB", focused)
        self.assertNotIn("LocalStreamingServer.warmUp", focused)

    def test_chat_source_scan_is_delayed_after_media_search_starts(self):
        vm = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/vm/MediaViewModel.kt")
        browse = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt")
        self.assertIn("CHAT_SOURCE_LOAD_DELAY_MS = 300L", vm)
        self.assertIn("MEDIA_FIRST_PAGE_PRIORITY_MAX_WAIT_MS = 3_000L", vm)
        self.assertIn("while (_uiState.value.isLoading && priorityWaitRemaining > 0L)", vm)
        self.assertIn("scheduleChatSourceLoad()", vm)
        self.assertNotIn("viewModel.loadVideoChatsIfNeeded()", browse)

    def test_repeated_grid_artwork_avoids_subcomposition_and_crossfade(self):
        components = read("app/src/main/java/com/vaydelux/bousygram/presentation/components/EnterpriseComponents.kt")
        tv_card = read("app/src/main/java/com/vaydelux/bousygram/presentation/components/TvGlowMediaCard.kt")
        compact = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt")
        home = read("app/src/main/java/com/vaydelux/bousygram/presentation/layout/TvHybridHomeDashboard.kt")
        self.assertIn("coil.compose.AsyncImage", components)
        self.assertIn(".crossfade(false)", components)
        self.assertIn("val imageRequest = remember(imageData)", components)
        self.assertNotIn("import coil.compose.SubcomposeAsyncImage", components)
        self.assertIn("coil.compose.AsyncImage", tv_card)
        self.assertIn("val imageRequest = remember(imageData)", tv_card)
        self.assertNotIn("import coil.compose.SubcomposeAsyncImage", tv_card)
        self.assertIn("ImageRequest.Builder(context).data(imageData).crossfade(false)", compact)
        self.assertNotIn(".crossfade(true)", compact)
        self.assertNotIn(".crossfade(true)", home)
        self.assertIn("val imageRequest = remember(imageData)", home)

    def test_library_grids_load_next_metadata_page_near_the_end(self):
        browse = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt")
        self.assertIn("snapshotFlow { libraryGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }", browse)
        self.assertIn("snapshotFlow { compactGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }", browse)
        self.assertIn("LIBRARY_PREFETCH_ITEMS_REMAINING = 6", browse)
        self.assertIn("viewModel.loadMoreIfNeeded()", browse)
        self.assertIn("onLoadMore()", browse)

    def test_more_pages_only_use_final_tdlib_cursor(self):
        vm = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/vm/MediaViewModel.kt")
        self.assertIn("hasMore = isFinal && nextFromMessageId != 0L", vm)
        self.assertIn("nextFromMessageId = if (isFinal) nextFromMessageId else 0L", vm)

    def test_selected_video_still_uses_existing_player_stream_preparation(self):
        browse = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt")
        vm = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/vm/MediaViewModel.kt")
        player_factory = read("app/src/main/java/com/vaydelux/bousygram/core/media/TelegramProxyPlayerFactory.kt")
        self.assertIn("viewModel.preparePlayback(item)", browse)
        self.assertIn("repository.prepareStream(item)", vm)
        self.assertIn("ExoPlayer", player_factory)


if __name__ == "__main__":
    unittest.main(verbosity=2)

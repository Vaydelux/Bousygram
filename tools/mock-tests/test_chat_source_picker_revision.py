"""Source-contract tests for the channel/source picker revision.

These assertions protect the intended UI/data contract without requiring Telegram login
or changing the app's playback/authentication implementation.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


class ChatSourcePickerRevisionTests(unittest.TestCase):
    def test_source_tabs_are_integrated_on_home_library_and_compact(self):
        browse = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt")
        dashboard = read("app/src/main/java/com/vaydelux/bousygram/presentation/layout/TvHybridHomeDashboard.kt")
        self.assertGreaterEqual(browse.count("ChatSourceTabs("), 2)
        self.assertIn("ChatSourceTabs(", dashboard)
        self.assertIn("AllChatsPickerDialog(", browse)

    def test_picker_includes_main_and_archived_chat_lists(self):
        chats = read("app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesChats.kt")
        self.assertIn("TdApi.ChatListMain()", chats)
        self.assertIn("TdApi.ChatListArchive()", chats)
        self.assertIn("TdApi.LoadChats(chatList, CHAT_PICKER_LOAD_BATCH)", chats)
        self.assertIn("TdApi.GetChats(chatList, maxVisibleChats)", chats)
        self.assertIn("CHAT_PICKER_DETAIL_BATCH", chats)

    def test_all_common_telegram_chat_types_are_included(self):
        chats = read("app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesChats.kt")
        for kind in (
            "TdApi.ChatTypePrivate",
            "TdApi.ChatTypeBasicGroup",
            "TdApi.ChatTypeSupergroup",
            "TdApi.ChatTypeSecret",
        ):
            self.assertIn(kind, chats)
        self.assertIn("ChatSourceKind.CHANNEL", chats)
        self.assertIn("ChatSourceKind.GROUP", chats)
        self.assertIn("ChatSourceKind.PRIVATE", chats)
        self.assertNotIn("hasVideoInChatInternal(chatId)", chats)

    def test_picker_has_search_and_type_filters(self):
        picker = read("app/src/main/java/com/vaydelux/bousygram/presentation/components/ChatSourcePicker.kt")
        for text in ("Search chats", "Channels", "Groups", "Private chats", "Archived", "All chats"):
            self.assertIn(text, picker)

    def test_media_grid_remains_video_only(self):
        helpers = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/PlaybackHelpers.kt")
        browse = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt")
        self.assertIn("items.filter { it.type == MediaType.Video && it.fileId != null }", helpers)
        self.assertIn("playableVideos(state.filteredAndSortedItems)", browse)

    def test_progressive_video_extensions_from_request_are_recognized(self):
        support = read("app/src/main/java/com/vaydelux/bousygram/core/media/VideoFormatSupport.kt")
        self.assertIn('".fmp4"', support)
        self.assertIn('".ogg"', support)
        self.assertIn('"fmp4", "m4v", "mp4" -> "video/mp4"', support)


if __name__ == "__main__":
    unittest.main(verbosity=2)

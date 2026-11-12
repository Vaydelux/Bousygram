"""Host verification of shipped migration/DAO SQL against Room's exported schema.

Complements Android instrumentation; does not claim Android runtime qualification.
Run: python tools/ci/test_favorites_storage.py
"""
import json
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/java/com/vaydelux/bousygram/data/database"
SCHEMA = ROOT / "app/schemas/com.vaydelux.bousygram.data.database.AppDatabase/3.json"


class FavoritesStorageTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        java = (SOURCE / "AppDatabase.java").read_text(encoding="utf-8")
        migration = java.split("MIGRATION_2_3 =", 1)[1].split("private static final Map", 1)[0]
        for sql in re.findall(r'db.execSQL\("([^"]+)"\)', migration):
            self.db.execute(sql)
        self.queries = re.findall(r'@Query\("([^"]+)"\)', (SOURCE / "FavoriteDao.java").read_text())

    def query(self, prefix):
        return next(sql for sql in self.queries if sql.startswith(prefix))

    def seed(self, count):
        self.db.executemany(
            "INSERT INTO favorites(chatId,messageId,addedAt,title,durationSeconds,"
            "trustedDurationMs,mediaType,fileSizeBytes,unavailable) VALUES(-100,?,?,?,0,0,'Video',0,0)",
            ((index, index, "A long duplicate filename: episode.mkv") for index in range(1, count + 1)),
        )

    def test_migration_matches_room_schema(self):
        entity = next(row for row in json.loads(SCHEMA.read_text())["database"]["entities"]
                      if row["tableName"] == "favorites")
        with sqlite3.connect(":memory:") as expected:
            expected.execute(entity["createSql"].replace("${TABLE_NAME}", "favorites"))
            for index in entity["indices"]:
                expected.execute(index["createSql"].replace("${TABLE_NAME}", "favorites"))
            for pragma in ("table_info(favorites)", "index_list(favorites)"):
                self.assertEqual(expected.execute("PRAGMA " + pragma).fetchall(),
                                 self.db.execute("PRAGMA " + pragma).fetchall())

    def test_empty_and_large_collections_have_bounded_ordered_pages(self):
        sql = self.query("SELECT * FROM favorites ORDER BY")
        for count in (0, 1, 100, 1000, 10000):
            with self.subTest(count=count):
                self.db.execute("DELETE FROM favorites")
                self.seed(count)
                rows = self.db.execute(sql, {"limit": 60, "offset": 0}).fetchall()
                self.assertEqual(min(count, 60), len(rows))
                if count >= 100:
                    later = self.db.execute(sql, {"limit": 60, "offset": 40}).fetchall()
                    self.assertEqual(60, len(later))
                    self.assertEqual(count - 40, later[0][1])

    def test_unavailable_entries_remain_and_late_metadata_cannot_reinsert_removed_items(self):
        self.seed(1)
        identity = {"chatId": -100, "messageId": 1}
        self.db.execute(self.query("UPDATE favorites SET unavailable"), identity)
        self.assertEqual(1, self.db.execute("SELECT unavailable FROM favorites").fetchone()[0])
        remove = self.query("DELETE FROM favorites WHERE")
        self.db.execute(remove, identity)
        self.db.execute(remove, identity)
        self.db.execute(self.query("UPDATE favorites SET title"), dict(
            identity, title="Late response", fileId=42, threadId=None, poster=None,
            miniThumbnail=None, duration=90, size=1024, mediaType="Video",
            fileName="episode.mkv", mimeType="video/x-matroska", trustedDurationMs=90000,
        ))
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM favorites").fetchone()[0])


if __name__ == "__main__":
    unittest.main()

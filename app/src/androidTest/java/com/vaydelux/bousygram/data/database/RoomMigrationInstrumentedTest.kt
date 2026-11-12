package com.vaydelux.bousygram.data.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Source-compatible migration smoke. Version 1 had only videos; version 2
 * creates recent_searches while preserving the original Room table and indexes.
 * This test uses a private disposable database, never a Telegram account DB.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationInstrumentedTest {
    @Test fun versionOneVideoRowsSurviveMigrationToRoomV3Favorites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "room-migration-v1-" + System.nanoTime() + ".db"
        val file = context.getDatabasePath(name)
        check(file.parentFile?.exists() == true || file.parentFile?.mkdirs() == true)

        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL(
                    """CREATE TABLE IF NOT EXISTS videos (
                        chatId INTEGER NOT NULL, topicId INTEGER NOT NULL,
                        messageId INTEGER NOT NULL, fileId INTEGER NOT NULL,
                        title TEXT NOT NULL, duration INTEGER NOT NULL,
                        size INTEGER NOT NULL, minithumbnail BLOB,
                        season INTEGER, episode INTEGER, lastSeen INTEGER NOT NULL,
                        PRIMARY KEY(chatId, messageId)
                    )""".trimIndent()
                )
                old.execSQL("CREATE INDEX index_videos_chatId_topicId ON videos(chatId, topicId)")
                old.execSQL("CREATE INDEX index_videos_lastSeen ON videos(lastSeen)")
                old.execSQL("CREATE INDEX index_videos_title ON videos(title)")
                old.execSQL(
                    "INSERT INTO videos (chatId,topicId,messageId,fileId,title,duration,size,lastSeen) " +
                        "VALUES (101,7,202,303,'Historical episode',90,4096,1234)"
                )
                old.version = 1
            }

            val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
            try {
                val existing = room.videoDao().getVideo(101L, 202L)
                assertNotNull(existing)
                assertEquals("Historical episode", existing.title)
                assertEquals(7L, existing.topicId)
                assertEquals(303, existing.fileId)
                assertEquals(1, room.videoDao().videoCount)

                room.openHelper.writableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='recent_searches'"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("recent_searches", cursor.getString(0))
                }
                room.openHelper.writableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='favorites'"
                ).use { cursor ->
                    assertTrue("Room v3 must create the favorites table", cursor.moveToFirst())
                }
                room.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(3, cursor.getInt(0))
                }
            } finally {
                room.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

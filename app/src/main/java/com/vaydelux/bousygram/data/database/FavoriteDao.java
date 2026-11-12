package com.vaydelux.bousygram.data.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public interface FavoriteDao {
    @Query("SELECT chatId || ':' || messageId FROM favorites")
    Flow<List<String>> observeKeys();
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC, chatId, messageId LIMIT :limit OFFSET :offset")
    Flow<List<FavoriteEntity>> observePage(int limit, int offset);
    @Query("SELECT * FROM favorites WHERE chatId = :chatId AND messageId = :messageId")
    FavoriteEntity find(long chatId, long messageId);
    // Read existing bookmarks in bounded batches for a one-way migration to Telegram.
    // Keyset pagination remains correct while confirmed rows are being removed.
    @Query("SELECT * FROM favorites WHERE chatId > :afterChatId OR " +
           "(chatId = :afterChatId AND messageId > :afterMessageId) " +
           "ORDER BY chatId ASC, messageId ASC LIMIT :limit")
    List<FavoriteEntity> migrationBatch(long afterChatId, long afterMessageId, int limit);
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(FavoriteEntity item);
    @Query("DELETE FROM favorites WHERE chatId = :chatId AND messageId = :messageId")
    void remove(long chatId, long messageId);
    @Query("DELETE FROM favorites")
    void clear();
    @Query("UPDATE favorites SET title = :title, fileId = :fileId, threadId = :threadId, poster = :poster, miniThumbnail = :miniThumbnail, durationSeconds = :duration, fileSizeBytes = :size, mediaType = :mediaType, fileName = :fileName, mimeType = :mimeType, trustedDurationMs = :trustedDurationMs, unavailable = 0 WHERE chatId = :chatId AND messageId = :messageId")
    void updateMetadata(long chatId, long messageId, String title, Integer fileId,
                        Long threadId, String poster, byte[] miniThumbnail, int duration, long size,
                        String mediaType, String fileName, String mimeType, long trustedDurationMs);
    @Query("UPDATE favorites SET unavailable = 1 WHERE chatId = :chatId AND messageId = :messageId")
    void markUnavailable(long chatId, long messageId);
}

package com.vaydelux.bousygram.data.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;

/** Stored in the verified account's database. A missing message remains removable. */
@Entity(tableName = "favorites", primaryKeys = {"chatId", "messageId"},
        indices = {@Index(value = {"addedAt"})})
public class FavoriteEntity {
    public long chatId;
    public long messageId;
    public long addedAt;
    @NonNull public String title = "Saved media";
    @Nullable public Integer fileId;
    @Nullable public Long threadId;
    @Nullable public String poster;
    @Nullable public byte[] miniThumbnail;
    public int durationSeconds;
    public long trustedDurationMs;
    @NonNull public String mediaType = "Video";
    @Nullable public String fileName;
    @Nullable public String mimeType;
    public long fileSizeBytes;
    public boolean unavailable;
}

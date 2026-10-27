package com.vaydelux.bousygram.data.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "recent_searches",
    indices = {
        @Index(value = {"timestampMs"}),
        @Index(value = {"chatId", "timestampMs"})
    }
)
public class RecentSearchEntity {
    @PrimaryKey
    @NonNull
    public String normalizedQuery = "";

    @NonNull
    public String query = "";

    @Nullable
    public Long chatId;

    @Nullable
    public String chatTitle;

    public int resultCount;

    public long timestampMs;

    public RecentSearchEntity() {}

    @Ignore
    public RecentSearchEntity(
        @NonNull String normalizedQuery,
        @NonNull String query,
        @Nullable Long chatId,
        @Nullable String chatTitle,
        int resultCount,
        long timestampMs
    ) {
        this.normalizedQuery = normalizedQuery;
        this.query = query;
        this.chatId = chatId;
        this.chatTitle = chatTitle;
        this.resultCount = resultCount;
        this.timestampMs = timestampMs;
    }
}

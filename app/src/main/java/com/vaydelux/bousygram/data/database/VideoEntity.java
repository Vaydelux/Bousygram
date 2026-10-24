package com.vaydelux.bousygram.data.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "videos",
    primaryKeys = {"chatId", "messageId"},
    indices = {
        @Index(value = {"chatId", "topicId"}),
        @Index(value = {"lastSeen"}),
        @Index(value = {"title"})
    }
)
public class VideoEntity {
    public long chatId;
    public long topicId;
    public long messageId;
    public int fileId;

    @NonNull
    public String title = "";

    public long duration; // in seconds or milliseconds
    public long size;     // in bytes

    @Nullable
    public byte[] minithumbnail;

    @Nullable
    public Integer season;

    @Nullable
    public Integer episode;

    public long lastSeen; // timestamp ms

    public VideoEntity() {}

    @androidx.room.Ignore
    public VideoEntity(
        long chatId,
        long topicId,
        long messageId,
        int fileId,
        @NonNull String title,
        long duration,
        long size,
        @Nullable byte[] minithumbnail,
        @Nullable Integer season,
        @Nullable Integer episode,
        long lastSeen
    ) {
        this.chatId = chatId;
        this.topicId = topicId;
        this.messageId = messageId;
        this.fileId = fileId;
        this.title = title;
        this.duration = duration;
        this.size = size;
        this.minithumbnail = minithumbnail;
        this.season = season;
        this.episode = episode;
        this.lastSeen = lastSeen;
    }
}

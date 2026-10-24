package com.vaydelux.bousygram.data.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY lastSeen DESC")
    List<VideoEntity> getAllVideos();

    @Query("SELECT * FROM videos ORDER BY lastSeen DESC LIMIT :limit OFFSET :offset")
    List<VideoEntity> getRecentVideos(int limit, int offset);

    @Query("SELECT * FROM videos WHERE chatId = :chatId ORDER BY messageId DESC")
    List<VideoEntity> getVideosByChat(long chatId);

    @Query("SELECT * FROM videos WHERE chatId = :chatId AND topicId = :topicId ORDER BY messageId DESC")
    List<VideoEntity> getVideosByChatAndTopic(long chatId, long topicId);

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' ORDER BY lastSeen DESC LIMIT :limit")
    List<VideoEntity> searchVideos(String query, int limit);

    @Query("SELECT * FROM videos WHERE chatId = :chatId AND title LIKE '%' || :query || '%' ORDER BY messageId DESC LIMIT :limit")
    List<VideoEntity> searchVideosInChat(long chatId, String query, int limit);

    @Query("SELECT * FROM videos WHERE chatId = :chatId AND messageId = :messageId LIMIT 1")
    VideoEntity getVideo(long chatId, long messageId);

    @Query("SELECT * FROM videos WHERE fileId = :fileId LIMIT 1")
    VideoEntity getVideoByFileId(int fileId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertVideos(List<VideoEntity> videos);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertVideo(VideoEntity video);

    @Query("DELETE FROM videos WHERE chatId = :chatId AND messageId = :messageId")
    void deleteVideo(long chatId, long messageId);

    @Query("DELETE FROM videos WHERE chatId = :chatId")
    void deleteVideosByChat(long chatId);

    @Query("SELECT COUNT(*) FROM videos")
    int getVideoCount();

    @Query("SELECT COUNT(*) FROM videos WHERE chatId = :chatId")
    int getVideoCountByChat(long chatId);
}

package com.vaydelux.bousygram.data.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import kotlinx.coroutines.flow.Flow;

@Dao
public interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY timestampMs DESC LIMIT :limit")
    Flow<List<RecentSearchEntity>> observeRecentSearches(int limit);

    @Query("SELECT * FROM recent_searches ORDER BY timestampMs DESC LIMIT :limit")
    List<RecentSearchEntity> getRecentSearches(int limit);

    @Query("SELECT * FROM recent_searches WHERE chatId = :chatId OR chatId IS NULL ORDER BY timestampMs DESC LIMIT :limit")
    List<RecentSearchEntity> getRecentSearchesForChat(long chatId, int limit);

    @Query("SELECT * FROM recent_searches WHERE normalizedQuery LIKE :prefix || '%' ORDER BY timestampMs DESC LIMIT :limit")
    List<RecentSearchEntity> findMatchingQueries(String prefix, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecentSearch(RecentSearchEntity search);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecentSearches(List<RecentSearchEntity> searches);

    @Query("DELETE FROM recent_searches WHERE normalizedQuery = :normalizedQuery")
    void deleteRecentSearch(String normalizedQuery);

    @Query("DELETE FROM recent_searches")
    void clearAllRecentSearches();

    @Query("DELETE FROM recent_searches WHERE normalizedQuery NOT IN (SELECT normalizedQuery FROM recent_searches ORDER BY timestampMs DESC LIMIT :maxCount)")
    void trimToMaxCount(int maxCount);

    @Query("SELECT COUNT(*) FROM recent_searches")
    int getRecentSearchCount();
}

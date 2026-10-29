package com.vaydelux.bousygram.data.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.vaydelux.bousygram.core.account.AccountSession;
import com.vaydelux.bousygram.core.account.AccountSnapshot;
import java.util.HashMap;
import java.util.Map;

@Database(entities = {VideoEntity.class, RecentSearchEntity.class}, version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract VideoDao videoDao();
    public abstract RecentSearchDao recentSearchDao();

    // Version 1 at 98bf6f4 shipped this videos table without recent_searches.
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS recent_searches (normalizedQuery TEXT NOT NULL, query TEXT NOT NULL, chatId INTEGER, chatTitle TEXT, resultCount INTEGER NOT NULL, timestampMs INTEGER NOT NULL, PRIMARY KEY(normalizedQuery))");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recent_searches_timestampMs ON recent_searches (timestampMs)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recent_searches_chatId_timestampMs ON recent_searches (chatId, timestampMs)");
        }
    };
    private static final Map<Long, AppDatabase> INSTANCES = new HashMap<>();
    private static AppDatabase testInstance;

    public static AppDatabase getInstance(Context context) {
        AccountSnapshot account = AccountSession.INSTANCE.current();
        if (account == null) throw new IllegalStateException("No verified Telegram account");
        return getInstance(context, account.getAccountId());
    }

    public static synchronized AppDatabase getInstance(Context context, long accountId) {
        if (accountId <= 0) throw new IllegalArgumentException("Invalid Telegram account identity");
        if (testInstance != null) return testInstance;
        AppDatabase existing = INSTANCES.get(accountId);
        if (existing == null) {
            // Keep the old unscoped bousygram_videos.db quarantined and intact.
            existing = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class,
                    "bousygram_account_" + accountId + ".db")
                    .addMigrations(MIGRATION_1_2).build();
            INSTANCES.put(accountId, existing);
        }
        return existing;
    }

    public static synchronized void closeAccountDatabases() {
        for (AppDatabase database : INSTANCES.values()) database.close();
        INSTANCES.clear();
    }

    public static synchronized void setTestInstance(AppDatabase db) { testInstance = db; }
}

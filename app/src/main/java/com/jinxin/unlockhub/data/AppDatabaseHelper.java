package com.jinxin.unlockhub.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class AppDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "safe_ping.db";
    private static final int DATABASE_VERSION = 8;

    public AppDatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS unlock_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "local_date TEXT NOT NULL UNIQUE, " +
                "first_unlock_at INTEGER NOT NULL, " +
                "synced INTEGER NOT NULL DEFAULT 0" +
                ")");
        createMemoTable(db);
        createRoutineTable(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 默认实现会抛异常导致整个应用打不开（例如装了旧版 APK 覆盖新版数据）。
        // 这里改为幂等补齐所有表，数据保留。
        onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 所有迁移幂等化：中间版本混装、半途升级都不能让应用崩溃。
        if (oldVersion < 2) {
            createMemoTable(db);
        }
        if (oldVersion >= 2 && oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE memos ADD COLUMN attachments TEXT NOT NULL DEFAULT ''");
            } catch (Exception ignored) {
                // 列已存在
            }
        }
        if (oldVersion < 4) {
            createRoutineTable(db);
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE memos ADD COLUMN unlock_popup INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
                // 列已存在
            }
        }
        if (oldVersion < 6) {
            try {
                db.execSQL("ALTER TABLE memos ADD COLUMN last_popup_at INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
                // 列已存在
            }
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE memos ADD COLUMN read_at INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
                // 列已存在
            }
        }
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE memos ADD COLUMN plain_record INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
                // 列已存在
            }
        }
    }

    private static void createRoutineTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS routines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL DEFAULT '', " +
                "enabled INTEGER NOT NULL DEFAULT 1, " +
                "trigger_type TEXT NOT NULL, " +           // time | charger_on | charger_off | battery_low | first_unlock | app_open
                "trigger_param TEXT NOT NULL DEFAULT '', " +  // JSON
                "constraint_type TEXT NOT NULL DEFAULT '', " + // '' | time_range | charging | not_charging | weekday
                "constraint_param TEXT NOT NULL DEFAULT '', " +
                "action_type TEXT NOT NULL, " +            // notify | activate_memo | sync_now | send_message | open_app | dnd_on | dnd_off
                "action_param TEXT NOT NULL DEFAULT '', " +
                "last_fired_at INTEGER NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL DEFAULT 0" +
                ")");
    }

    private static void createMemoTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS memos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL DEFAULT '', " +
                "content TEXT NOT NULL DEFAULT '', " +
                "type TEXT NOT NULL DEFAULT 'text', " +          // text | checklist
                "memo_date TEXT NOT NULL DEFAULT '', " +          // yyyy-MM-dd，日历关联日期
                "remind_at INTEGER NOT NULL DEFAULT 0, " +        // 提醒时间（毫秒），0 表示无
                "pinned INTEGER NOT NULL DEFAULT 0, " +
                "done INTEGER NOT NULL DEFAULT 0, " +
                "unread INTEGER NOT NULL DEFAULT 0, " +
                "private_flag INTEGER NOT NULL DEFAULT 0, " +
                "attachments TEXT NOT NULL DEFAULT '', " +        // JSON: [{u:uri, n:name}]
                "unlock_popup INTEGER NOT NULL DEFAULT 0, " +     // 解锁-弹窗加强提醒
                "last_popup_at INTEGER NOT NULL DEFAULT 0, " +    // 上次解锁弹窗时间（去重用）
                "read_at INTEGER NOT NULL DEFAULT 0, " +          // 变为已读的时间（0=未读；用于已读 30 天自动删除）
                "plain_record INTEGER NOT NULL DEFAULT 0, " +     // 纯记录：不提醒、不弹窗、不计待处理、不自动删除
                "created_at INTEGER NOT NULL DEFAULT 0, " +
                "updated_at INTEGER NOT NULL DEFAULT 0" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memos_date ON memos(memo_date)");
    }
}

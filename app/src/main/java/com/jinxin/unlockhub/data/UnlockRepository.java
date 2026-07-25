package com.jinxin.unlockhub.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.jinxin.unlockhub.util.TimeFormat;

import java.util.ArrayList;
import java.util.List;

public final class UnlockRepository {
    public static final int MAX_LOCAL_RECORDS = 30;

    private final AppDatabaseHelper helper;

    public UnlockRepository(Context context) {
        this.helper = new AppDatabaseHelper(context);
    }

    public UnlockEvent recordFirstUnlockIfNeeded(long nowMillis) {
        String date = TimeFormat.localDate(nowMillis);
        UnlockEvent existing = findByDate(date);
        if (existing != null) {
            return existing;
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("local_date", date);
        values.put("first_unlock_at", nowMillis);
        values.put("synced", 0);
        long id = db.insert("unlock_events", null, values);
        pruneToMaxRecords(MAX_LOCAL_RECORDS);
        return new UnlockEvent(id, date, nowMillis, false);
    }

    public void pruneToMaxRecords(int maxRecords) {
        int total = totalCount();
        if (total <= maxRecords) {
            return;
        }
        int toDelete = total - maxRecords;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL(
                "DELETE FROM unlock_events WHERE id IN (" +
                        "SELECT id FROM unlock_events ORDER BY local_date ASC LIMIT ?)",
                new Object[]{toDelete}
        );
    }

    public UnlockEvent findByDate(String localDate) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                "unlock_events",
                null,
                "local_date = ?",
                new String[]{localDate},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return fromCursor(cursor);
        }
    }

    public List<UnlockEvent> latest(int limit) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<UnlockEvent> events = new ArrayList<>();
        try (Cursor cursor = db.query(
                "unlock_events",
                null,
                null,
                null,
                null,
                null,
                "first_unlock_at DESC",
                String.valueOf(limit)
        )) {
            while (cursor.moveToNext()) {
                events.add(fromCursor(cursor));
            }
        }
        return events;
    }

    public List<UnlockEvent> unsynced() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<UnlockEvent> events = new ArrayList<>();
        try (Cursor cursor = db.query(
                "unlock_events",
                null,
                "synced = 0",
                null,
                null,
                null,
                "first_unlock_at ASC"
        )) {
            while (cursor.moveToNext()) {
                events.add(fromCursor(cursor));
            }
        }
        return events;
    }

    public void markSynced(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("synced", 1);
        db.update("unlock_events", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public long lastActivityAt() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT MAX(first_unlock_at) FROM unlock_events", null)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
            return 0L;
        }
    }

    public int totalCount() {
        return countWhere(null, null);
    }

    public int syncedCount() {
        return countWhere("synced = 1", null);
    }

    public int unsyncedCount() {
        return countWhere("synced = 0", null);
    }

    public int countSince(String startDateInclusive) {
        return countWhere("local_date >= ?", new String[]{startDateInclusive});
    }

    public Stats stats() {
        return new Stats(totalCount(), syncedCount(), unsyncedCount(), countSince(TimeFormat.currentWeekStartDate()));
    }

    private int countWhere(String selection, String[] args) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = selection == null
                ? "SELECT COUNT(*) FROM unlock_events"
                : "SELECT COUNT(*) FROM unlock_events WHERE " + selection;
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        }
    }

    public static final class Stats {
        public final int total;
        public final int synced;
        public final int pending;
        public final int thisWeek;

        public Stats(int total, int synced, int pending, int thisWeek) {
            this.total = total;
            this.synced = synced;
            this.pending = pending;
            this.thisWeek = thisWeek;
        }
    }

    private static UnlockEvent fromCursor(Cursor cursor) {
        return new UnlockEvent(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("local_date")),
                cursor.getLong(cursor.getColumnIndexOrThrow("first_unlock_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("synced")) == 1
        );
    }
}

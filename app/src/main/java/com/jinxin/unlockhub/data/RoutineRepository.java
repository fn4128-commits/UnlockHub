package com.jinxin.unlockhub.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public final class RoutineRepository {
    private final AppDatabaseHelper helper;

    public RoutineRepository(Context context) {
        this.helper = new AppDatabaseHelper(context);
    }

    public long save(Routine routine) {
        ContentValues values = new ContentValues();
        values.put("name", routine.name == null ? "" : routine.name);
        values.put("enabled", routine.enabled ? 1 : 0);
        values.put("trigger_type", routine.triggerType);
        values.put("trigger_param", routine.triggerParam == null ? "" : routine.triggerParam);
        values.put("constraint_type", routine.constraintType == null ? "" : routine.constraintType);
        values.put("constraint_param", routine.constraintParam == null ? "" : routine.constraintParam);
        values.put("action_type", routine.actionType);
        values.put("action_param", routine.actionParam == null ? "" : routine.actionParam);
        SQLiteDatabase db = helper.getWritableDatabase();
        if (routine.id > 0) {
            db.update("routines", values, "id = ?", new String[]{String.valueOf(routine.id)});
            return routine.id;
        }
        routine.createdAt = System.currentTimeMillis();
        values.put("created_at", routine.createdAt);
        routine.id = db.insert("routines", null, values);
        return routine.id;
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete("routines", "id = ?", new String[]{String.valueOf(id)});
    }

    public void setEnabled(long id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        helper.getWritableDatabase().update("routines", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void markFired(long id, long firedAt) {
        ContentValues values = new ContentValues();
        values.put("last_fired_at", firedAt);
        helper.getWritableDatabase().update("routines", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public Routine findById(long id) {
        try (Cursor cursor = helper.getReadableDatabase().query(
                "routines", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<Routine> listAll() {
        return query(null, null);
    }

    /** 某触发类型的启用规则。 */
    public List<Routine> enabledByTrigger(String triggerType) {
        return query("enabled = 1 AND trigger_type = ?", new String[]{triggerType});
    }

    private List<Routine> query(String selection, String[] args) {
        List<Routine> routines = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(
                "routines", null, selection, args, null, null, "enabled DESC, id DESC")) {
            while (cursor.moveToNext()) {
                routines.add(fromCursor(cursor));
            }
        }
        return routines;
    }

    private static Routine fromCursor(Cursor cursor) {
        Routine routine = new Routine();
        routine.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        routine.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        routine.enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1;
        routine.triggerType = cursor.getString(cursor.getColumnIndexOrThrow("trigger_type"));
        routine.triggerParam = cursor.getString(cursor.getColumnIndexOrThrow("trigger_param"));
        routine.constraintType = cursor.getString(cursor.getColumnIndexOrThrow("constraint_type"));
        routine.constraintParam = cursor.getString(cursor.getColumnIndexOrThrow("constraint_param"));
        routine.actionType = cursor.getString(cursor.getColumnIndexOrThrow("action_type"));
        routine.actionParam = cursor.getString(cursor.getColumnIndexOrThrow("action_param"));
        routine.lastFiredAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_fired_at"));
        routine.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        return routine;
    }
}

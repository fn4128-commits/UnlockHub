package com.jinxin.unlockhub.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MemoRepository {
    private final AppDatabaseHelper helper;

    public MemoRepository(Context context) {
        this.helper = new AppDatabaseHelper(context);
    }

    public long save(Memo memo) {
        long now = System.currentTimeMillis();
        memo.updatedAt = now;
        // 纯记录在这里就把提醒相关属性清空，而不是只靠编辑页不显示：无论从哪条路径保存
        // （编辑页、自动化规则、以后新增的入口），存进库的都必定是一条不会打扰人的记录。
        if (memo.plainRecord) {
            memo.remindAt = 0L;
            memo.unlockPopup = false;
            memo.unread = false;
            memo.readAt = 0L; // 不是"已读"，只是不参与未读体系；这样也不会进入 30 天删除倒计时
        }
        ContentValues values = new ContentValues();
        values.put("title", memo.title == null ? "" : memo.title);
        values.put("content", memo.content == null ? "" : memo.content);
        values.put("type", memo.type == null ? Memo.TYPE_TEXT : memo.type);
        values.put("memo_date", memo.memoDate == null ? "" : memo.memoDate);
        values.put("remind_at", memo.remindAt);
        values.put("pinned", memo.pinned ? 1 : 0);
        values.put("done", memo.done ? 1 : 0);
        values.put("unread", memo.unread ? 1 : 0);
        values.put("private_flag", memo.isPrivate ? 1 : 0);
        values.put("plain_record", memo.plainRecord ? 1 : 0);
        values.put("unlock_popup", memo.unlockPopup ? 1 : 0);
        values.put("attachments", memo.attachments == null ? "" : memo.attachments);
        // read_at 只在"由未读被真正读过"时写入（见 setUnread）。保存时不能因为 unread=0
        // 就当成已读——否则新建的普通备忘（默认未读=否）一创建就被判为已读、置灰并进入
        // 30 天删除倒计时。这里仅在标为未读时清零。
        if (memo.unread) {
            memo.readAt = 0L;
        }
        values.put("read_at", memo.readAt);
        values.put("updated_at", now);
        SQLiteDatabase db = helper.getWritableDatabase();
        if (memo.id > 0) {
            db.update("memos", values, "id = ?", new String[]{String.valueOf(memo.id)});
            return memo.id;
        }
        memo.createdAt = now;
        values.put("created_at", now);
        memo.id = db.insert("memos", null, values);
        return memo.id;
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete("memos", "id = ?", new String[]{String.valueOf(id)});
    }

    public Memo findById(long id) {
        try (Cursor cursor = helper.getReadableDatabase().query(
                "memos", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return fromCursor(cursor);
        }
    }

    /** 列表：置顶在前，其余按更新时间倒序。includePrivate 为 false 时过滤私密备忘。 */
    public List<Memo> listAll(boolean includePrivate) {
        String selection = includePrivate ? null : "private_flag = 0";
        return query(selection, null);
    }

    /** 某一天的备忘（memo_date 匹配）。 */
    public List<Memo> listByDate(String memoDate, boolean includePrivate) {
        String selection = "memo_date = ?" + (includePrivate ? "" : " AND private_flag = 0");
        return query(selection, new String[]{memoDate});
    }

    /** 某月内有备忘的日期集合，用于日历打点。month 形如 2026-07。 */
    public Set<String> datesInMonth(String month, boolean includePrivate) {
        Set<String> dates = new HashSet<>();
        String selection = "memo_date LIKE ?" + (includePrivate ? "" : " AND private_flag = 0");
        try (Cursor cursor = helper.getReadableDatabase().query(
                "memos", new String[]{"DISTINCT memo_date"}, selection,
                new String[]{month + "-%"}, null, null, null)) {
            while (cursor.moveToNext()) {
                dates.add(cursor.getString(0));
            }
        }
        return dates;
    }

    public int unreadCount() {
        try (Cursor cursor = helper.getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM memos WHERE unread = 1 AND plain_record = 0", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /**
     * 角标统计：未读、未完成、需要关注的总条数（未读或未完成，不重复计数）。
     * 纯记录不计入——它本来就不是待办，不该出现在"还有几条待处理"里。
     */
    public BadgeStats badgeStats() {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT " +
                        "SUM(CASE WHEN unread = 1 THEN 1 ELSE 0 END), " +
                        "SUM(CASE WHEN done = 0 THEN 1 ELSE 0 END), " +
                        "SUM(CASE WHEN unread = 1 OR done = 0 THEN 1 ELSE 0 END) " +
                        "FROM memos WHERE plain_record = 0", null)) {
            if (cursor.moveToFirst()) {
                return new BadgeStats(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2));
            }
            return new BadgeStats(0, 0, 0);
        }
    }

    public static final class BadgeStats {
        public final int unread;
        public final int undone;
        public final int attention;

        public BadgeStats(int unread, int undone, int attention) {
            this.unread = unread;
            this.undone = undone;
            this.attention = attention;
        }
    }

    public int privateCount() {
        try (Cursor cursor = helper.getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM memos WHERE private_flag = 1", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** 所有未来的提醒（用于开机后重建闹钟）。纯记录不会有提醒，一并排除。 */
    public List<Memo> pendingReminders(long nowMillis) {
        return query("remind_at > ? AND plain_record = 0", new String[]{String.valueOf(nowMillis)});
    }

    /** 解锁-弹窗加强提醒：开启该标记且仍未读的备忘。纯记录永远不参与。 */
    public List<Memo> unlockPopupPending() {
        return query("unlock_popup = 1 AND unread = 1 AND plain_record = 0", null);
    }

    public void setLastPopupAt(long id, long popupAt) {
        ContentValues values = new ContentValues();
        values.put("last_popup_at", popupAt);
        helper.getWritableDatabase().update("memos", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void setUnread(long id, boolean unread) {
        ContentValues values = new ContentValues();
        values.put("unread", unread ? 1 : 0);
        // 变已读 → 记录时间点开始 30 天计时；变未读 → 清零。
        values.put("read_at", unread ? 0L : System.currentTimeMillis());
        helper.getWritableDatabase().update("memos", values, "id = ?", new String[]{String.valueOf(id)});
    }

    /**
     * 已读满 30 天自动删除（不删置顶，不删仍有未来提醒的，也不删纯记录）。返回删除条数。
     * 纯记录是"存着的东西"，不能因为放久了就自己消失——这正是它和备忘的区别。
     */
    public static final long READ_RETENTION_MS = 30L * 24 * 60 * 60 * 1000L;

    public int pruneExpiredRead() {
        long now = System.currentTimeMillis();
        long cutoff = now - READ_RETENTION_MS;
        return helper.getWritableDatabase().delete(
                "memos",
                "unread = 0 AND pinned = 0 AND plain_record = 0 AND read_at > 0 AND read_at < ? "
                        + "AND (remind_at = 0 OR remind_at <= ?)",
                new String[]{String.valueOf(cutoff), String.valueOf(now)});
    }

    /**
     * 勾选/取消清单里的某一项，把结果写回 content。
     * 供列表页直接勾选用——不必为了打一个勾就进编辑页。
     */
    public void setChecklistItemChecked(long id, int index, boolean checked) {
        Memo memo = findById(id);
        if (memo == null || !memo.isChecklist()) {
            return;
        }
        List<Memo.Item> items = memo.checklistItems();
        if (index < 0 || index >= items.size()) {
            return; // 列表与库不同步（例如另一处刚删过条目），忽略这次点击
        }
        items.get(index).checked = checked;
        ContentValues values = new ContentValues();
        values.put("content", Memo.encodeChecklist(items));
        values.put("updated_at", System.currentTimeMillis());
        helper.getWritableDatabase().update("memos", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void setDone(long id, boolean done) {
        ContentValues values = new ContentValues();
        values.put("done", done ? 1 : 0);
        helper.getWritableDatabase().update("memos", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void setPinned(long id, boolean pinned) {
        ContentValues values = new ContentValues();
        values.put("pinned", pinned ? 1 : 0);
        helper.getWritableDatabase().update("memos", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void clearReminder(long id) {
        ContentValues values = new ContentValues();
        values.put("remind_at", 0);
        helper.getWritableDatabase().update("memos", values, "id = ?", new String[]{String.valueOf(id)});
    }

    private List<Memo> query(String selection, String[] args) {
        List<Memo> memos = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(
                "memos", null, selection, args, null, null,
                // 置顶最前；已读(read_at>0)下沉到最后；中间按未读(活跃)在前、完成状态、更新时间。
                "pinned DESC, (read_at > 0) ASC, unread DESC, done ASC, updated_at DESC")) {
            while (cursor.moveToNext()) {
                memos.add(fromCursor(cursor));
            }
        }
        return memos;
    }

    private static Memo fromCursor(Cursor cursor) {
        Memo memo = new Memo();
        memo.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        memo.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        memo.content = cursor.getString(cursor.getColumnIndexOrThrow("content"));
        memo.type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
        memo.memoDate = cursor.getString(cursor.getColumnIndexOrThrow("memo_date"));
        memo.remindAt = cursor.getLong(cursor.getColumnIndexOrThrow("remind_at"));
        memo.pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1;
        memo.done = cursor.getInt(cursor.getColumnIndexOrThrow("done")) == 1;
        memo.unread = cursor.getInt(cursor.getColumnIndexOrThrow("unread")) == 1;
        memo.isPrivate = cursor.getInt(cursor.getColumnIndexOrThrow("private_flag")) == 1;
        int plainIndex = cursor.getColumnIndex("plain_record");
        memo.plainRecord = plainIndex >= 0 && cursor.getInt(plainIndex) == 1;
        int unlockPopupIndex = cursor.getColumnIndex("unlock_popup");
        memo.unlockPopup = unlockPopupIndex >= 0 && cursor.getInt(unlockPopupIndex) == 1;
        int lastPopupIndex = cursor.getColumnIndex("last_popup_at");
        memo.lastPopupAt = lastPopupIndex >= 0 ? cursor.getLong(lastPopupIndex) : 0L;
        int readAtIndex = cursor.getColumnIndex("read_at");
        memo.readAt = readAtIndex >= 0 ? cursor.getLong(readAtIndex) : 0L;
        int attachmentsIndex = cursor.getColumnIndex("attachments");
        memo.attachments = attachmentsIndex >= 0 && cursor.getString(attachmentsIndex) != null
                ? cursor.getString(attachmentsIndex) : "";
        memo.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        memo.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return memo;
    }
}

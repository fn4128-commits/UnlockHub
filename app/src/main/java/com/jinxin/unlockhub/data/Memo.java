package com.jinxin.unlockhub.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Memo {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_CHECKLIST = "checklist";

    public long id;
    public String title = "";
    public String content = "";
    public String type = TYPE_TEXT;
    public String memoDate = "";
    public long remindAt;
    public boolean pinned;
    public boolean done;
    public boolean unread;
    public boolean isPrivate;
    public boolean unlockPopup; // 解锁-弹窗加强提醒
    public long lastPopupAt;    // 上次解锁弹窗时间（去重）
    public long readAt;         // 变为已读的时间戳（0=未读）；用于已读 30 天后自动删除
    public String attachments = "";
    public long createdAt;
    public long updatedAt;

    public boolean isChecklist() {
        return TYPE_CHECKLIST.equals(type);
    }

    /** 清单项，仅当 type 为 checklist 时使用；content 存 JSON。 */
    public static final class Item {
        public String text;
        public boolean checked;

        public Item(String text, boolean checked) {
            this.text = text;
            this.checked = checked;
        }
    }

    public List<Item> checklistItems() {
        List<Item> items = new ArrayList<>();
        if (!isChecklist() || content == null || content.isEmpty()) {
            return items;
        }
        try {
            JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                items.add(new Item(object.optString("t", ""), object.optBoolean("d", false)));
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    // 附件功能已移除（旧版遗留、无实际意义）。attachments 列仍保留以兼容旧数据库，恒为空。

    public static String encodeChecklist(List<Item> items) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject object = new JSONObject();
                object.put("t", item.text);
                object.put("d", item.checked);
                array.put(object);
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }

    /** 列表页预览文本。 */
    public String preview() {
        if (isChecklist()) {
            List<Item> items = checklistItems();
            if (items.isEmpty()) {
                return "";
            }
            int doneCount = 0;
            StringBuilder builder = new StringBuilder();
            int shown = 0;
            for (Item item : items) {
                if (item.checked) {
                    doneCount++;
                }
                if (shown < 3) {
                    if (shown > 0) {
                        builder.append('\n');
                    }
                    builder.append(item.checked ? "☑ " : "☐ ").append(item.text);
                    shown++;
                }
            }
            if (items.size() > 3) {
                builder.append("\n…");
            }
            return doneCount + "/" + items.size() + "\n" + builder;
        }
        if (content == null || content.isEmpty()) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() > 80) {
            return trimmed.substring(0, 80) + "…";
        }
        return trimmed;
    }
}

package com.jinxin.unlockhub.sync;

import android.content.Context;

import com.jinxin.unlockhub.data.Memo;
import com.jinxin.unlockhub.data.MemoRepository;
import com.jinxin.unlockhub.network.ApiClient;
import com.jinxin.unlockhub.util.NetworkUtil;
import com.jinxin.unlockhub.util.Prefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * 备忘录云同步：全量上传非私密备忘到状态页。
 * 私密备忘和附件永不上传。失败静默（下次改动时会再试）。
 */
public final class MemoSync {
    private MemoSync() {
    }

    public static void syncAsync(Context context) {
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                sync(appContext);
            } catch (Exception ignored) {
                // 无网或后端不可用时静默失败，不打扰用户。
            }
        }, "memo-sync").start();
    }

    public static void sync(Context context) throws IOException, JSONException {
        if (!Prefs.isAccountBound(context) || Prefs.backendUrl(context).isEmpty()) {
            return;
        }
        if (!NetworkUtil.isOnline(context)) {
            return;
        }
        List<Memo> memos = new MemoRepository(context).listAll(false); // 不含私密
        JSONArray array = new JSONArray();
        for (Memo memo : memos) {
            JSONObject object = new JSONObject();
            object.put("clientId", memo.id);
            object.put("title", memo.title);
            object.put("content", memo.content);
            object.put("type", memo.type);
            object.put("memoDate", memo.memoDate);
            object.put("pinned", memo.pinned);
            object.put("done", memo.done);
            object.put("updatedAt", memo.updatedAt);
            array.put(object);
        }
        new ApiClient(context).syncMemos(array.toString());
    }
}

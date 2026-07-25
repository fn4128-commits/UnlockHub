package com.jinxin.unlockhub.util;

import android.content.Context;

public final class BoundAppMonitor {
    private BoundAppMonitor() {
    }

    public static String statusLine(Context context) {
        int count = Prefs.boundAppCount(context);
        if (count <= 0) {
            return context.getString(com.jinxin.unlockhub.R.string.ba_none);
        }
        return context.getString(com.jinxin.unlockhub.R.string.ba_count, count);
    }
}

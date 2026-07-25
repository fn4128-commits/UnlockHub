package com.jinxin.unlockhub.util;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃日志：把最后一次未捕获异常写到应用私有目录，
 * 便于在主页长按版本号查看，快速定位闪退原因。
 */
public final class CrashLogger {
    private static final String FILE_NAME = "last_crash.txt";

    private CrashLogger() {
    }

    public static void install(Context context) {
        final Context appContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                write(appContext, thread, throwable);
            } catch (Exception ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private static void write(Context context, Thread thread, Throwable throwable) throws Exception {
        StringWriter stackWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackWriter));
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(time + "  thread=" + thread.getName() + "\n\n" + stackWriter);
        }
    }

    public static String readLast(Context context) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) {
                return "";
            }
            byte[] data = new byte[(int) Math.min(file.length(), 16_384)];
            try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                int read = input.read(data);
                return read <= 0 ? "" : new String(data, 0, read, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }
}

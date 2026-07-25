package com.jinxin.unlockhub.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class TimeFormat {
    private TimeFormat() {
    }

    public static String localDate(long epochMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(new Date(epochMillis));
    }

    public static String isoOffset(long epochMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(new Date(epochMillis));
    }

    public static String humanDateTime(long epochMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat("M/d HH:mm", Locale.getDefault());
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(new Date(epochMillis));
    }

    public static String currentWeekStartDate() {
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek + 5) % 7;
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
        return localDate(calendar.getTimeInMillis());
    }

    public static long startOfDayMillis(String localDate) {
        String[] parts = localDate.split("-");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, Integer.parseInt(parts[0]));
        calendar.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
        calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[2]));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static String formatUploadRange(String periodStart, String periodEnd) {
        String[] startParts = periodStart.split("-");
        String[] endParts = periodEnd.split("-");
        if (startParts.length == 3 && endParts.length == 3
                && startParts[0].equals(endParts[0])
                && startParts[1].equals(endParts[1])) {
            return startParts[0] + "-" + startParts[1] + "-" + startParts[2]
                    + "-" + endParts[2];
        }
        return periodStart + " - " + periodEnd;
    }

    public static String humanCountdown(long millis) {
        long days = millis / (24L * 60L * 60L * 1000L);
        long hours = (millis / (60L * 60L * 1000L)) % 24L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h";
        }
        long minutes = Math.max(1L, millis / (60L * 1000L));
        return minutes + "min";
    }
}

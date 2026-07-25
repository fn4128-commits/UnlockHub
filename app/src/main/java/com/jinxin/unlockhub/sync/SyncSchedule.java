package com.jinxin.unlockhub.sync;

import android.content.Context;

import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.TimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class SyncSchedule {
    public static final int DEFAULT_INTERVAL_DAYS = 7;
    public static final int MAX_INTERVAL_DAYS = 180;
    public static final int WEEKDAY_PERIOD_DAYS = 7;
    public static final int[] INTERVAL_PRESETS = {1, 2, 3, 7, 15, 30};
    public static final int DEFAULT_WEEKDAY_MASK = weekdayBit(DayOfWeek.MONDAY);

    private SyncSchedule() {
    }

    public static boolean isWeekdayMode(Context context) {
        return Prefs.SYNC_MODE_WEEKDAY.equals(Prefs.syncMode(context));
    }

    public static boolean isSyncDue(Context context) {
        String dueDate = currentDueDate(context);
        if (dueDate == null) {
            return false;
        }
        String lastSynced = Prefs.lastSyncedPeriodDueDate(context);
        return lastSynced == null || lastSynced.isEmpty() || lastSynced.compareTo(dueDate) < 0;
    }

    public static boolean isTodaySyncDay(Context context) {
        String today = TimeFormat.localDate(System.currentTimeMillis());
        String due = currentDueDate(context);
        return due != null && today.equals(due);
    }

    public static boolean hasPendingUpload(Context context) {
        return isSyncDue(context);
    }

    public static String nextScheduledSyncDate(Context context) {
        String today = TimeFormat.localDate(System.currentTimeMillis());
        if (isWeekdayMode(context)) {
            LocalDate start = LocalDate.parse(today);
            String lastSynced = Prefs.lastSyncedPeriodDueDate(context);
            for (int offset = 0; offset < 14; offset++) {
                LocalDate candidate = start.plusDays(offset);
                if (!isWeekdaySelected(context, candidate.getDayOfWeek())) {
                    continue;
                }
                String candidateText = candidate.toString();
                if (offset == 0 && candidateText.equals(lastSynced)) {
                    continue;
                }
                return candidateText;
            }
            return today;
        }

        String anchor = syncAnchorDate(context);
        int interval = syncIntervalDays(context);
        String candidate = anchor;
        while (candidate.compareTo(today) < 0) {
            candidate = addDays(candidate, interval);
        }
        return candidate;
    }

    public static String nextSyncDate(Context context) {
        return nextScheduledSyncDate(context);
    }

    public static long millisUntilNextSync(Context context) {
        String nextDate = nextScheduledSyncDate(context);
        long target = TimeFormat.startOfDayMillis(nextDate);
        return Math.max(0L, target - System.currentTimeMillis());
    }

    public static String formatCountdown(Context context) {
        long millis = millisUntilNextSync(context);
        long dayMs = 24L * 60L * 60L * 1000L;
        long days = millis / dayMs;
        if (days > 0) {
            return context.getString(com.jinxin.unlockhub.R.string.sy_days, (int) days);
        }
        if (millis > 0) {
            return context.getString(com.jinxin.unlockhub.R.string.sy_less_day);
        }
        return context.getString(com.jinxin.unlockhub.R.string.sy_today);
    }

    public static String formatNextSyncStatus(Context context, boolean online, boolean hasTodayRecord, int pendingDays) {
        if (isTodaySyncDay(context) && hasPendingUpload(context)) {
            if (!online) {
                return context.getString(com.jinxin.unlockhub.R.string.sy_wait_net);
            }
            if (!hasTodayRecord) {
                return context.getString(com.jinxin.unlockhub.R.string.sy_wait_unlock);
            }
            if (pendingDays > 0) {
                return context.getString(com.jinxin.unlockhub.R.string.sy_wait_upload, pendingDays);
            }
            return context.getString(com.jinxin.unlockhub.R.string.sy_wait_gen);
        }
        return context.getString(com.jinxin.unlockhub.R.string.sy_next, nextScheduledSyncDate(context), formatCountdown(context));
    }

    public static String periodStartForDueDate(Context context, String dueDate) {
        if (isWeekdayMode(context)) {
            return addDays(dueDate, -(WEEKDAY_PERIOD_DAYS - 1));
        }
        return addDays(dueDate, -syncIntervalDays(context));
    }

    public static String periodEndForDueDate(Context context, String dueDate) {
        return dueDate;
    }

    public static int expectedPeriodDays(Context context) {
        return isWeekdayMode(context) ? WEEKDAY_PERIOD_DAYS : syncIntervalDays(context);
    }

    public static String syncAnchorDate(Context context) {
        String value = Prefs.syncAnchorDate(context);
        if (value == null || value.isEmpty()) {
            return TimeFormat.localDate(System.currentTimeMillis());
        }
        return value;
    }

    public static int syncIntervalDays(Context context) {
        int value = Prefs.syncIntervalDays(context);
        return value > 0 ? value : DEFAULT_INTERVAL_DAYS;
    }

    public static String currentDueDate(Context context) {
        String today = TimeFormat.localDate(System.currentTimeMillis());
        if (isWeekdayMode(context)) {
            LocalDate date = LocalDate.parse(today);
            return isWeekdaySelected(context, date.getDayOfWeek()) ? today : null;
        }
        return latestDueDateOnOrBeforeToday(syncAnchorDate(context), syncIntervalDays(context));
    }

    public static String latestDueDateOnOrBeforeToday(String anchorDate, int intervalDays) {
        if (anchorDate == null || anchorDate.isEmpty() || intervalDays <= 0) {
            return null;
        }
        LocalDate anchor = LocalDate.parse(anchorDate);
        LocalDate today = LocalDate.parse(TimeFormat.localDate(System.currentTimeMillis()));
        if (today.isBefore(anchor)) {
            return null;
        }
        long daysBetween = ChronoUnit.DAYS.between(anchor, today);
        long periods = daysBetween / intervalDays;
        return anchor.plusDays(periods * intervalDays).toString();
    }

    public static boolean isWeekdaySelected(Context context, DayOfWeek dayOfWeek) {
        int mask = Prefs.syncWeekdaysMask(context);
        return (mask & weekdayBit(dayOfWeek)) != 0;
    }

    public static int weekdayBit(DayOfWeek dayOfWeek) {
        return 1 << (dayOfWeek.getValue() - 1);
    }

    public static String weekdayLabel(Context context, DayOfWeek dayOfWeek) {
        String[] names = context.getResources().getStringArray(com.jinxin.unlockhub.R.array.sy_weekdays);
        int idx = dayOfWeek.getValue() - 1;
        return (idx >= 0 && idx < names.length) ? names[idx] : names[6];
    }

    public static String formatWeekdaySelection(Context context) {
        StringBuilder builder = new StringBuilder();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (!isWeekdaySelected(context, day)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(weekdayLabel(context, day));
        }
        return builder.length() == 0 ? context.getString(com.jinxin.unlockhub.R.string.sy_none) : builder.toString();
    }

    public static String addDays(String date, int days) {
        return LocalDate.parse(date).plusDays(days).toString();
    }
}

package com.jinxin.unlockhub.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Lists apps the user can launch from the home screen, with icons. */
public final class BoundAppCatalog {
    private BoundAppCatalog() {
    }

    public static List<Entry> loadLaunchableApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        String selfPackage = context.getPackageName();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolves = packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_ALL
        );

        Map<String, Entry> entries = new LinkedHashMap<>();
        for (ResolveInfo info : resolves) {
            if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (selfPackage.equals(packageName)) {
                continue;
            }
            CharSequence label = info.loadLabel(packageManager);
            if (label == null || label.toString().trim().isEmpty()) {
                label = info.activityInfo.applicationInfo.loadLabel(packageManager);
            }
            if (label == null || label.toString().trim().isEmpty()) {
                continue;
            }
            entries.put(packageName, new Entry(
                    packageName,
                    label.toString(),
                    info.activityInfo.applicationInfo
            ));
        }

        List<Entry> result = new ArrayList<>(entries.values());
        sortEntries(result, Collections.emptySet());
        return result;
    }

    public static List<Entry> filter(List<Entry> source, String query, Set<String> selectedPackages) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> filtered = new ArrayList<>();
        for (Entry entry : source) {
            if (normalized.isEmpty()
                    || entry.label.toLowerCase(Locale.ROOT).contains(normalized)
                    || entry.packageName.toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(entry);
            }
        }
        sortEntries(filtered, selectedPackages);
        return filtered;
    }

    private static void sortEntries(List<Entry> entries, Set<String> selectedPackages) {
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                boolean leftSelected = selectedPackages.contains(left.packageName);
                boolean rightSelected = selectedPackages.contains(right.packageName);
                if (leftSelected != rightSelected) {
                    return leftSelected ? -1 : 1;
                }
                return left.label.compareToIgnoreCase(right.label);
            }
        });
    }

    public static final class Entry {
        public final String packageName;
        public final String label;
        public final ApplicationInfo applicationInfo;

        public Entry(String packageName, String label, ApplicationInfo applicationInfo) {
            this.packageName = packageName;
            this.label = label;
            this.applicationInfo = applicationInfo;
        }
    }
}

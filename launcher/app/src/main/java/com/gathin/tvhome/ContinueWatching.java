package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ContinueWatching {

    public static final class Item {
        public final String packageName;
        public final String title;
        public final String subtitle;
        public final String intentUri;
        public final Uri posterUri;
        public final long lastEngagementTime;
        public final Drawable icon;
        public final Drawable banner;

        public Item(String packageName, String title, String subtitle, String intentUri,
                    Uri posterUri, long lastEngagementTime, Drawable icon, Drawable banner) {
            this.packageName = packageName;
            this.title = title;
            this.subtitle = subtitle;
            this.intentUri = intentUri;
            this.posterUri = posterUri;
            this.lastEngagementTime = lastEngagementTime;
            this.icon = icon;
            this.banner = banner;
        }
    }

    private static final Uri WATCH_NEXT_URI = Uri.parse("content://android.media.tv/watch_next_program");

    public static List<Item> load(Context context, Set<String> enabledPackages) {
        if (enabledPackages == null || enabledPackages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Item> items = new ArrayList<>();
        Set<String> seenPackagesWithPrograms = new HashSet<>();
        PackageManager pm = context.getPackageManager();

        // 1. Try querying TvContract.WatchNextPrograms if permission is present
        try {
            if (context.checkSelfPermission("android.permission.READ_TV_LISTINGS") == PackageManager.PERMISSION_GRANTED) {
                // Must query without selection per docs/watch-next.md to avoid SecurityException
                try (Cursor c = context.getContentResolver().query(WATCH_NEXT_URI, null, null, null, null)) {
                    if (c != null) {
                        int pkgIdx = c.getColumnIndex("package_name");
                        int titleIdx = c.getColumnIndex("title");
                        int epIdx = c.getColumnIndex("episode_title");
                        int intentIdx = c.getColumnIndex("intent_uri");
                        int posterIdx = c.getColumnIndex("poster_art_uri");
                        int timeIdx = c.getColumnIndex("last_engagement_time_utc_millis");

                        while (c.moveToNext()) {
                            String pkg = pkgIdx >= 0 ? c.getString(pkgIdx) : null;
                            if (pkg == null || !enabledPackages.contains(pkg)) continue;

                            String title = titleIdx >= 0 ? c.getString(titleIdx) : null;
                            String epTitle = epIdx >= 0 ? c.getString(epIdx) : null;
                            String intentUri = intentIdx >= 0 ? c.getString(intentIdx) : null;
                            String posterStr = posterIdx >= 0 ? c.getString(posterIdx) : null;
                            long time = timeIdx >= 0 ? c.getLong(timeIdx) : 0;

                            Uri posterUri = posterStr != null ? Uri.parse(posterStr) : null;
                            Drawable banner = loadBanner(pm, pkg);
                            Drawable icon = loadIcon(pm, pkg);

                            String appName = pkg;
                            try {
                                appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                            } catch (Exception ignored) {}

                            String mainTitle = title != null && !title.isEmpty() ? title : appName;
                            String subTitle = epTitle != null && !epTitle.isEmpty() ? epTitle : appName;

                            items.add(new Item(pkg, mainTitle, subTitle, intentUri, posterUri, time, icon, banner));
                            seenPackagesWithPrograms.add(pkg);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. For any enabled package that didn't have WatchNext programs, fallback to LastOpened
        for (String pkg : enabledPackages) {
            if (seenPackagesWithPrograms.contains(pkg)) continue;
            try {
                String appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                long when = LastOpened.get(context, pkg);
                String sub = when > 0 ? LastOpened.describe(context, when) : context.getString(R.string.continue_watching_title);
                Drawable banner = loadBanner(pm, pkg);
                Drawable icon = loadIcon(pm, pkg);
                items.add(new Item(pkg, appName, sub, null, null, when, icon, banner));
            } catch (Exception ignored) {
            }
        }

        // Sort by last engagement / opened time descending
        Collections.sort(items, (a, b) -> Long.compare(b.lastEngagementTime, a.lastEngagementTime));
        return items;
    }

    private static Drawable loadBanner(PackageManager pm, String pkg) {
        try {
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationBanner(ai);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Drawable loadIcon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void launch(Context context, Item item) {
        if (item.intentUri != null && !item.intentUri.isEmpty()) {
            try {
                Intent intent = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(item.packageName);
        if (launch != null) {
            try {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }
}

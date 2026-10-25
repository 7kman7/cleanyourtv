package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** The standard settings intent, or, when nothing usable answers it, the first vendor settings entry
 *  from settings_fallback_components that is installed (Xiaomi China, TCL / FFALCON, Fire OS). */
final class SystemSettings {
    private SystemSettings() {}

    static Intent intent(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        Intent standard = new Intent(action);
        ResolveInfo resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY);
        // Fire OS resolves the standard actions to a CTS compliance stub or dummy handler that opens nothing;
        // treat it as missing so the vendor fallback runs.
        boolean isDummy = resolved != null && (resolved.activityInfo.name.contains("CTSDummy")
                || resolved.activityInfo.name.contains("Dummy")
                || resolved.activityInfo.name.contains("CTS")
                || resolved.activityInfo.packageName.equals("com.amazon.tv.launcher"));
        if (resolved != null && !isDummy) return standard;
        Intent vendor = vendorIntent(context);
        return vendor != null ? vendor : standard;
    }

    /** The first vendor settings entry from settings_fallback_components that is installed, or null
     *  when none is. China-market firmware keeps pages the AOSP TV settings has no row for -
     *  Xiaomi's Account & Security, where the ADB switch lives - in the vendor's own settings app;
     *  MainActivity's Settings menu offers it as "Native settings" while one is installed. */
    static Intent vendorIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) continue;
            // Explicit resolveActivity() always succeeds, so check the package exists instead.
            try {
                pm.getPackageInfo(component.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            return new Intent().setComponent(component);
        }
        return null;
    }

    /** The TV's App info page for pkg, or null where the firmware has none (CVTE's settings app, #1202). */
    static Intent appInfo(Context context, String pkg) {
        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        return context.getPackageManager().resolveActivity(info, PackageManager.MATCH_DEFAULT_ONLY) != null ? info : null;
    }

    /** Opens the TV's application manager (where non-responding apps can be force-stopped, uninstalled, etc.). */
    static Intent manageAppsIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent manage = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        if (pm.resolveActivity(manage, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return manage;
        }
        Intent appSettings = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
        if (pm.resolveActivity(appSettings, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return appSettings;
        }
        String[] fireTvAppManagers = {
                "com.amazon.tv.settings.v2/com.amazon.tv.settings.v2.application.ApplicationsActivity",
                "com.amazon.tv.launcher/com.amazon.tv.launcher.ui.ApplicationsActivity"
        };
        for (String s : fireTvAppManagers) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (cn != null) {
                try {
                    pm.getPackageInfo(cn.getPackageName(), 0);
                    return new Intent().setComponent(cn);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        }
        return intent(context, Settings.ACTION_SETTINGS);
    }

    /**
     * Checks if an intent can be safely called by an external application without SecurityException
     * or ActivityNotFoundException.
     */
    static boolean isIntentCallable(PackageManager pm, Intent intent) {
        if (intent == null) return false;
        try {
            List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (list == null || list.isEmpty()) {
                list = pm.queryIntentActivities(intent, 0);
            }
            if (list == null || list.isEmpty()) return false;
            for (ResolveInfo ri : list) {
                if (ri.activityInfo != null && ri.activityInfo.exported) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Returns the launch intent for PhairPlay if installed, or null. */
    static Intent phairPlayIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        String[] packages = {
                "com.phairplay.firetv",
                "com.phairplay.googletv",
                "com.phairplay",
                "com.github.phairplay",
                "io.github.phairplay",
                "com.phairplay.app"
        };
        for (String p : packages) {
            Intent launch = pm.getLaunchIntentForPackage(p);
            if (launch != null) return launch;
        }
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            if (ri.activityInfo != null && ri.activityInfo.packageName.toLowerCase(java.util.Locale.ROOT).contains("phairplay")) {
                Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if (launch != null) return launch;
                return new Intent().setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            }
        }
        Intent queryNormal = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(queryNormal, 0)) {
            if (ri.activityInfo != null && ri.activityInfo.packageName.toLowerCase(java.util.Locale.ROOT).contains("phairplay")) {
                Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if (launch != null) return launch;
                return new Intent().setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            }
        }
        try {
            for (android.content.pm.PackageInfo pi : pm.getInstalledPackages(0)) {
                if (pi.packageName.toLowerCase(java.util.Locale.ROOT).contains("phairplay")) {
                    Intent launch = pm.getLaunchIntentForPackage(pi.packageName);
                    if (launch != null) return launch;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Ensures AirPlay receiver services (PhairPlay, native Apple AirPlay, Amazon Cast sink)
     * are awake and running in the background.
     */
    static void ensureNativeAirPlayRunning(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent phairplay = phairPlayIntent(context);
        if (phairplay != null) {
            String phairPkg = phairplay.getComponent() != null ? phairplay.getComponent().getPackageName()
                    : (phairplay.getPackage() != null ? phairplay.getPackage() : "com.phairplay.firetv");
            try {
                android.content.pm.PackageInfo pi = pm.getPackageInfo(phairPkg, PackageManager.GET_SERVICES);
                if (pi.services != null) {
                    for (android.content.pm.ServiceInfo si : pi.services) {
                        if (si.name != null) {
                            try {
                                context.startService(new Intent().setComponent(new ComponentName(phairPkg, si.name)));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        String[] airplayPackages = {
                "com.apple.airplay.amazon.airplay",
                "com.amazon.cast.sink"
        };
        for (String pkg : airplayPackages) {
            try {
                android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES);
                if (pi.services != null) {
                    for (android.content.pm.ServiceInfo si : pi.services) {
                        if (si.name != null) {
                            try {
                                Intent svc = new Intent().setComponent(new ComponentName(pkg, si.name));
                                context.startService(svc);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        // Wake Apple AirPlay mDNS daemon and Amazon Cast sink
        try {
            context.sendBroadcast(new Intent("com.amazon.device.settings.ACTION_SETTINGS_CHANGED")
                    .setPackage("com.apple.airplay.amazon.airplay"));
        } catch (Exception ignored) {
        }
        try {
            context.sendBroadcast(new Intent("com.apple.airplay.amazon.airplay.START")
                    .setPackage("com.apple.airplay.amazon.airplay"));
        } catch (Exception ignored) {
        }
        try {
            context.sendBroadcast(new Intent("com.amazon.cast.sink.START")
                    .setPackage("com.amazon.cast.sink"));
        } catch (Exception ignored) {
        }
    }

    private static final String[] DISPLAY_COMPONENTS = {
            "com.amazon.tv.settings.v2/com.amazon.tv.settings.v2.display.DisplayActivity",
            "com.amazon.tv.settings/com.amazon.tv.settings.tv.BuellerDisplayAndSoundsSettingsActivity",
            "com.amazon.tv.settings/com.amazon.tv.settings.display.DisplayActivity",
            "com.amazon.tv.launcher/com.amazon.tv.launcher.ui.MainSettingsActivity",
            "com.amazon.tv.settings.v2/com.amazon.tv.settings.v2.MainActivity"
    };

    /**
     * Checks whether PhairPlay, native Fire TV AirPlay, Amazon Cast Sink, or Cast Settings
     * is available on the TV.
     */
    static boolean isAirPlayAvailable(Context context) {
        if (phairPlayIntent(context) != null) return true;
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo("com.apple.airplay.amazon.airplay", 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        try {
            pm.getPackageInfo("com.amazon.cast.sink", 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        Intent cast = new Intent("android.settings.CAST_SETTINGS");
        return pm.resolveActivity(cast, PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    /**
     * Reliably launches PhairPlay or native Fire TV AirPlay / Display settings.
     */
    static boolean launchAirPlay(Context context) {
        // Priority 1: PhairPlay
        Intent phairplay = phairPlayIntent(context);
        if (phairplay != null) {
            try {
                HomeKeyService.allowStockLauncherUntil = android.os.SystemClock.uptimeMillis() + 15000;
                context.startActivity(phairplay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Exception ignored) {
            }
        }

        PackageManager pm = context.getPackageManager();
        List<Intent> intents = new ArrayList<>();

        // 1. Query any activity in com.apple.airplay.amazon.airplay
        try {
            android.content.pm.PackageInfo pi = pm.getPackageInfo("com.apple.airplay.amazon.airplay", PackageManager.GET_ACTIVITIES);
            if (pi.activities != null) {
                for (android.content.pm.ActivityInfo ai : pi.activities) {
                    Intent actIntent = new Intent().setComponent(new ComponentName("com.apple.airplay.amazon.airplay", ai.name));
                    if (ai.exported) {
                        intents.add(actIntent);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Intent appleLaunch = pm.getLaunchIntentForPackage("com.apple.airplay.amazon.airplay");
        if (appleLaunch != null) intents.add(appleLaunch);

        // 2. Direct AirPlay actions on Fire OS
        String[] airplayActions = {
                "com.amazon.tv.settings.AIRPLAY_SETTINGS",
                "com.amazon.device.settings.ACTION_AIRPLAY_SETTINGS",
                "amazon.settings.DISPLAY_AND_SOUND_SETTINGS",
                "com.amazon.tv.settings.DISPLAY_SETTINGS"
        };
        for (String act : airplayActions) {
            Intent actIntent = new Intent(act);
            if (isIntentCallable(pm, actIntent)) {
                intents.add(actIntent);
            }
        }

        // 3. Fire OS Display & Sounds settings activities (where native AirPlay & HomeKit lives)
        for (String c : DISPLAY_COMPONENTS) {
            ComponentName cn = ComponentName.unflattenFromString(c);
            if (cn != null) {
                try {
                    pm.getPackageInfo(cn.getPackageName(), 0);
                    Intent testIntent = new Intent().setComponent(cn);
                    testIntent.putExtra("category", "display");
                    testIntent.putExtra(":settings:show_fragment", "com.amazon.tv.settings.v2.display.AirPlaySettingsFragment");
                    intents.add(testIntent);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        }

        // 4. Amazon Cast Sink
        Intent castLaunch = pm.getLaunchIntentForPackage("com.amazon.cast.sink");
        if (castLaunch != null) intents.add(castLaunch);

        // 5. Standard Android Cast Settings & Display Settings
        Intent castSettings = new Intent("android.settings.CAST_SETTINGS");
        if (isIntentCallable(pm, castSettings)) intents.add(castSettings);

        Intent displaySettings = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        if (isIntentCallable(pm, displaySettings)) intents.add(displaySettings);

        // 6. Vendor Settings fallback
        Intent vendor = vendorIntent(context);
        if (vendor != null) intents.add(vendor);

        Intent systemSettings = new Intent(Settings.ACTION_SETTINGS);
        if (isIntentCallable(pm, systemSettings)) intents.add(systemSettings);

        // Try launching each candidate until one succeeds
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (intent.getComponent() != null && "com.amazon.tv.launcher".equals(intent.getComponent().getPackageName())) {
                    HomeKeyService.allowStockLauncherUntil = android.os.SystemClock.uptimeMillis() + 8000;
                }
                context.startActivity(intent);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** Opens native Fire TV AirPlay configuration or Display & Sounds settings. */
    static Intent airPlayIntent(Context context) {
        PackageManager pm = context.getPackageManager();

        Intent appleLaunch = pm.getLaunchIntentForPackage("com.apple.airplay.amazon.airplay");
        if (appleLaunch != null) return appleLaunch;

        for (String c : DISPLAY_COMPONENTS) {
            ComponentName cn = ComponentName.unflattenFromString(c);
            if (cn != null) {
                Intent testIntent = new Intent().setComponent(cn);
                if (isIntentCallable(pm, testIntent)) {
                    return testIntent;
                }
            }
        }

        Intent castSettings = new Intent("android.settings.CAST_SETTINGS");
        if (isIntentCallable(pm, castSettings)) {
            return castSettings;
        }

        Intent displaySettings = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        if (isIntentCallable(pm, displaySettings)) {
            return displaySettings;
        }

        return intent(context, Settings.ACTION_SETTINGS);
    }
}

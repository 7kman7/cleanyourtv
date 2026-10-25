package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

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

    /** Returns the launch intent for PhairPlay if installed, or null. */
    static Intent phairPlayIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        String[] packages = {
                "com.phairplay.firetv",
                "com.phairplay.googletv",
                "com.phairplay"
        };
        for (String p : packages) {
            Intent launch = pm.getLaunchIntentForPackage(p);
            if (launch != null) return launch;
        }
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            if (ri.activityInfo != null && ri.activityInfo.packageName.toLowerCase().contains("phairplay")) {
                Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if (launch != null) return launch;
                return new Intent().setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            }
        }
        Intent queryNormal = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(queryNormal, 0)) {
            if (ri.activityInfo != null && ri.activityInfo.packageName.toLowerCase().contains("phairplay")) {
                Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if (launch != null) return launch;
                return new Intent().setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            }
        }
        return null;
    }

    /** Attempts to keep PhairPlay's receiver service running in the background. */
    static void ensureAirPlayRunning(Context context) {
        Intent phairplay = phairPlayIntent(context);
        if (phairplay != null) {
            String pkg = phairplay.getComponent() != null ? phairplay.getComponent().getPackageName()
                    : (phairplay.getPackage() != null ? phairplay.getPackage() : "com.phairplay.firetv");
            String[] serviceClasses = {
                    "com.phairplay.service.PhairPlayService",
                    "com.phairplay.PhairPlayService"
            };
            for (String cls : serviceClasses) {
                Intent svc = new Intent().setComponent(new ComponentName(pkg, cls));
                try {
                    context.startService(svc);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Checks whether an AirPlay receiver or casting service is installed / available on the TV. */
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

    /** Opens AirPlay configuration or the AirPlay receiver. */
    static Intent airPlayIntent(Context context) {
        PackageManager pm = context.getPackageManager();

        Intent phairplay = phairPlayIntent(context);
        if (phairplay != null) return phairplay;

        Intent appleLaunch = pm.getLaunchIntentForPackage("com.apple.airplay.amazon.airplay");
        if (appleLaunch != null) return appleLaunch;

        Intent castSink = pm.getLaunchIntentForPackage("com.amazon.cast.sink");
        if (castSink != null) return castSink;

        String[] airPlayComponents = {
                "com.amazon.tv.settings.v2/com.amazon.tv.settings.v2.display.DisplayActivity",
                "com.amazon.tv.settings.v2/com.amazon.tv.settings.v2.MainActivity",
                "com.amazon.tv.launcher/com.amazon.tv.launcher.ui.AirPlaySettingsActivity",
                "com.apple.airplay.amazon.airplay/com.apple.airplay.amazon.airplay.AirPlayActivity",
                "com.amazon.cast.sink/com.amazon.cast.sink.MainActivity"
        };
        for (String c : airPlayComponents) {
            ComponentName cn = ComponentName.unflattenFromString(c);
            if (cn != null) {
                Intent testIntent = new Intent().setComponent(cn);
                if (pm.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    return testIntent;
                }
            }
        }

        Intent castSettings = new Intent("android.settings.CAST_SETTINGS");
        if (pm.resolveActivity(castSettings, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return castSettings;
        }

        Intent displaySettings = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        if (pm.resolveActivity(displaySettings, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return displaySettings;
        }

        return intent(context, Settings.ACTION_SETTINGS);
    }
}

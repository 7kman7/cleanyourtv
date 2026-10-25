package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView themeChoice;
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private TextView clockScreensaverChoice;
    private TextView adbChoice;
    private TextView overrideLauncherChoice;
    private TextView startAtBootChoice;
    private TextView moduleCardsChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;
    private boolean clockScreensaver;
    private boolean overrideLauncher;
    private boolean startAtBoot;
    private boolean moduleCards;
    private String chosenTheme;

    private static final String[] THEME_KEYS = {
            Config.THEME_DEFAULT,
            Config.THEME_SKY,
            Config.THEME_SCENES,
            Config.THEME_AURORA,
            Config.THEME_GLASS,
            Config.THEME_OLED,
            Config.THEME_FRUTIGER_AERO,
            Config.THEME_WINDOWS_CLASSIC,
            Config.THEME_CYBERPUNK,
            Config.THEME_FOREST,
            Config.THEME_SUNSET,
            Config.THEME_VAPORWAVE,
            Config.THEME_MATRIX
    };

    private static final int[] THEME_RES = {
            R.string.theme_default,
            R.string.theme_sky,
            R.string.theme_scenes,
            R.string.theme_aurora,
            R.string.theme_glass,
            R.string.theme_oled,
            R.string.theme_frutiger_aero,
            R.string.theme_windows_classic,
            R.string.theme_cyberpunk,
            R.string.theme_forest,
            R.string.theme_sunset,
            R.string.theme_vaporwave,
            R.string.theme_matrix
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);

        View root = findViewById(R.id.settingsRoot);
        if (root != null) root.setBackgroundColor(config.backgroundColor);

        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        chosenTheme = config.chosenTheme;
        recentApps = config.recentApps;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }

        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setOnClickListener(v -> showThemePicker());

        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }

        clockScreensaver = choices.getBoolean("clock_screensaver", config.homeIdleClockMinutes > 0);
        clockScreensaverChoice = findViewById(R.id.clockScreensaverChoice);
        clockScreensaverChoice.setOnClickListener(v -> {
            clockScreensaver = !clockScreensaver;
            choices.edit().putBoolean("clock_screensaver", clockScreensaver).apply();
            changed();
            refresh();
        });

        adbChoice = findViewById(R.id.adbChoice);
        adbChoice.setOnClickListener(v -> {
            boolean current = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
            boolean target = !current;
            try {
                Settings.Global.putInt(getContentResolver(), Settings.Global.ADB_ENABLED, target ? 1 : 0);
                Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", target ? 1 : 0);
                refresh();
            } catch (Exception e) {
                openDeveloperSettings();
            }
        });

        overrideLauncher = choices.getBoolean("override_launcher", true);
        startAtBoot = choices.getBoolean("start_at_boot", true);
        overrideLauncherChoice = findViewById(R.id.overrideLauncherChoice);
        overrideLauncherChoice.setOnClickListener(v -> {
            overrideLauncher = !overrideLauncher;
            choices.edit().putBoolean("override_launcher", overrideLauncher).apply();
            changed();
            refresh();
        });
        startAtBootChoice = findViewById(R.id.startAtBootChoice);
        startAtBootChoice.setOnClickListener(v -> {
            startAtBoot = !startAtBoot;
            choices.edit().putBoolean("start_at_boot", startAtBoot).apply();
            changed();
            refresh();
        });
        moduleCards = choices.getBoolean("module_cards", config.moduleCards);
        moduleCardsChoice = findViewById(R.id.moduleCardsChoice);
        moduleCardsChoice.setOnClickListener(v -> {
            moduleCards = !moduleCards;
            choices.edit().putBoolean("module_cards", moduleCards).apply();
            changed();
            refresh();
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(OpenOnStart.KEY)
                    .remove("override_launcher").remove("start_at_boot").remove("module_cards")
                    .remove("clock_screensaver").remove(Config.THEME_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            chosenTheme = current.chosenTheme;
            clockScreensaver = current.homeIdleClockMinutes > 0;
            View r = findViewById(R.id.settingsRoot);
            if (r != null) r.setBackgroundColor(current.backgroundColor);
            changed();
            refresh();
        });
            View r = findViewById(R.id.settingsRoot);
            if (r != null) r.setBackgroundColor(current.backgroundColor);
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Intent homeSettings = new Intent(Settings.ACTION_HOME_SETTINGS);
            if (getPackageManager().resolveActivity(homeSettings, 0) != null) {
                Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
                open(homeSettings);
            } else {
                Intent stockHome = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage("com.amazon.tv.launcher");
                if (getPackageManager().resolveActivity(stockHome, 0) == null) {
                    stockHome = getPackageManager().getLaunchIntentForPackage("com.amazon.tv.launcher");
                }
                if (stockHome != null) {
                    HomeKeyService.allowStockLauncherUntil = android.os.SystemClock.uptimeMillis() + 3000;
                    open(stockHome);
                } else {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
    }

    private String themeDisplayName(String key) {
        for (int i = 0; i < THEME_KEYS.length; i++) {
            if (THEME_KEYS[i].equals(key)) return getString(THEME_RES[i]);
        }
        return getString(R.string.theme_default);
    }

    private void showThemePicker() {
        String[] displayNames = new String[THEME_KEYS.length];
        int checked = 0;
        for (int i = 0; i < THEME_KEYS.length; i++) {
            displayNames[i] = themeDisplayName(THEME_KEYS[i]);
            if (THEME_KEYS[i].equals(chosenTheme)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(displayNames, checked, (dialog, which) -> {
                    dialog.dismiss();
                    chosenTheme = THEME_KEYS[which];
                    choices.edit().putString(Config.THEME_KEY, chosenTheme).apply();
                    Config cfg = Config.load(this);
                    View root = findViewById(R.id.settingsRoot);
                    if (root != null) root.setBackgroundColor(cfg.backgroundColor);
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        for (TextView v : views) row.removeView(v);
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> loadedImages = WallpaperRotation.images(dir != null ? new File(dir, "wallpapers") : null);
        File single = dir != null ? new File(dir, Config.WALLPAPER_NAME) : null;
        final List<File> images;
        if (single != null && single.isFile() && single.length() > 0 && !loadedImages.contains(single)) {
            List<File> withSingle = new ArrayList<>(loadedImages);
            withSingle.add(0, single);
            images = withSingle;
        } else {
            images = loadedImages;
        }
        String[] labels = new String[images.size() + 2];
        labels[0] = getString(R.string.wallpaper_theme_default);
        labels[1] = getString(R.string.wallpaper_none);
        for (int i = 0; i < images.size(); i++) labels[i + 2] = images.get(i).getName();
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = current == null ? 0 : "none".equals(current) ? 1 : indexOf(images, current) + 2;
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    else if (which == 1) choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    else choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, images.get(which - 2).getName()).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        themeChoice.setText(getString(R.string.theme_state, themeDisplayName(chosenTheme)));
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        clockScreensaverChoice.setText(getString(R.string.clock_screensaver_state,
                getString(clockScreensaver ? R.string.on : R.string.off)));
        boolean adbOn = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        adbChoice.setText(getString(R.string.adb_state,
                getString(adbOn ? R.string.on : R.string.off)));
        overrideLauncherChoice.setText(getString(R.string.override_launcher_state,
                getString(overrideLauncher ? R.string.on : R.string.off)));
        startAtBootChoice.setText(getString(R.string.run_at_startup_state,
                getString(startAtBoot ? R.string.on : R.string.off)));
        moduleCardsChoice.setText(getString(R.string.module_cards_state,
                getString(moduleCards ? R.string.on : R.string.off)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void openDeveloperSettings() {
        HomeKeyService.allowStockLauncherUntil = android.os.SystemClock.uptimeMillis() + 8000;
        Intent[] intents = {
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                new Intent("com.amazon.device.settings.DEVELOPMENT_SETTINGS"),
                new Intent().setComponent(new ComponentName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.development.DevelopmentActivity")),
                new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.device.apps.specialaccess.DeveloperOptionsActivity")),
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        };
        for (Intent it : intents) {
            try {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(OpenOnStart.KEY) || choices.contains("override_launcher")
                || choices.contains("start_at_boot") || choices.contains("module_cards")
                || choices.contains("clock_screensaver") || choices.contains(Config.THEME_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}

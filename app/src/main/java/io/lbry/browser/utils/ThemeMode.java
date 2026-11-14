package io.lbry.browser.utils;

import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeMode {
    public static final String KEY = "io.lbry.browser.preference.userinterface.ThemeMode";
    private static final String LEGACY_KEY = "io.lbry.browser.preference.userinterface.DarkMode";

    public static void migrate(SharedPreferences preferences) {
        if (!preferences.contains(KEY)) {
            Boolean previous = preferences.contains(LEGACY_KEY)
                    ? preferences.getBoolean(LEGACY_KEY, false) : null;
            preferences.edit().putString(KEY, resolve(null, previous)).apply();
        }
    }

    public static String resolve(String selected, Boolean legacyDark) {
        if ("light".equals(selected) || "dark".equals(selected) || "system".equals(selected)) return selected;
        if (selected == null && legacyDark != null) return legacyDark ? "dark" : "light";
        return "system";
    }

    public static int nightMode(String selected) {
        switch (resolve(selected, null)) {
            case "light": return AppCompatDelegate.MODE_NIGHT_NO;
            case "dark": return AppCompatDelegate.MODE_NIGHT_YES;
            default: return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    private ThemeMode() { }
}

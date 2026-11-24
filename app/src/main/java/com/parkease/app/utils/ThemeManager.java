package com.parkease.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Remembers the user's theme choice (Light / Dark / follow the phone) and applies it.
 * The choice is stored in SharedPreferences, so it survives app restarts.
 * Applied once at startup in ParkEaseApp, and again whenever the user changes it.
 */
public final class ThemeManager {

    public static final int MODE_LIGHT = 0;
    public static final int MODE_DARK = 1;
    public static final int MODE_SYSTEM = 2;

    private static final String PREFS = "parkease_settings";
    private static final String KEY_THEME = "theme_mode";

    private ThemeManager() { }

    /** Call once in Application.onCreate(). */
    public static void applySaved(Context context) {
        apply(getMode(context));
    }

    public static int getMode(Context context) {
        return prefs(context).getInt(KEY_THEME, MODE_SYSTEM);
    }

    /** Saves and applies a new mode. Open screens are recreated automatically. */
    public static void setMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_THEME, mode).apply();
        apply(mode);
    }

    private static void apply(int mode) {
        int nightMode;
        if (mode == MODE_LIGHT) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (mode == MODE_DARK) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

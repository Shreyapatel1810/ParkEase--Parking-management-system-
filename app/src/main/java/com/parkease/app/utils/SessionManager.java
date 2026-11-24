package com.parkease.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.models.User;

/**
 * Small values the app remembers between launches, stored in SharedPreferences
 * (a private file on the phone: /data/data/com.parkease.app/shared_prefs/parkease_session.xml).
 *
 * What is stored:
 *  - onboarding completed (Step 4)
 *  - "Keep me signed in" choice
 *  - a cached copy of the logged-in user's uid, name, email and role, so the app can
 *    show the right dashboard even when it starts offline
 *
 * What is NEVER stored: passwords. Firebase Authentication keeps the login session
 * itself (a secure token), so the password is not needed after logging in.
 */
public final class SessionManager {

    private static final String PREFS = "parkease_session";

    private static final String KEY_ONBOARDING_DONE = "onboarding_completed";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_UID = "user_uid";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_ROLE = "user_role";

    private SessionManager() { }

    // ------------------------------------------------------------------ onboarding

    public static boolean isOnboardingCompleted(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public static void setOnboardingCompleted(Context context, boolean completed) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply();
    }

    // ------------------------------------------------------------------ remember me

    /** True = stay logged in after the app is closed. False = log out on next launch. */
    public static boolean isRememberMe(Context context) {
        return prefs(context).getBoolean(KEY_REMEMBER_ME, true);
    }

    public static void setRememberMe(Context context, boolean remember) {
        prefs(context).edit().putBoolean(KEY_REMEMBER_ME, remember).apply();
    }

    // ------------------------------------------------------------------ cached user

    public static void saveUser(Context context, @NonNull User user) {
        prefs(context).edit()
                .putString(KEY_UID, user.getUid())
                .putString(KEY_NAME, user.getFullName())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_ROLE, user.getRole())
                .apply();
    }

    @Nullable
    public static String getCachedUid(Context context) {
        return prefs(context).getString(KEY_UID, null);
    }

    @Nullable
    public static String getCachedRole(Context context) {
        return prefs(context).getString(KEY_ROLE, null);
    }

    @Nullable
    public static String getCachedName(Context context) {
        return prefs(context).getString(KEY_NAME, null);
    }

    /** Removes the cached user on logout. The onboarding flag is kept. */
    public static void clearUser(Context context) {
        prefs(context).edit()
                .remove(KEY_UID)
                .remove(KEY_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_ROLE)
                .remove(KEY_REMEMBER_ME)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

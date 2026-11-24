package com.parkease.app.utils;

import android.content.Context;

import com.google.firebase.FirebaseApp;

/** Small Firebase checks used before any Firebase call. */
public final class FirebaseHelper {

    private FirebaseHelper() { }

    /**
     * True when app/google-services.json was added and Firebase started.
     * Calling FirebaseAuth or FirebaseDatabase without it would crash the app,
     * so screens check this first and show a clear message instead.
     */
    public static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
    }
}

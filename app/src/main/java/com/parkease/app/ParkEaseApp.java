package com.parkease.app;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.parkease.app.utils.ThemeManager;

/**
 * Application class – runs once when the app process starts, before any screen.
 * Registered in AndroidManifest.xml with android:name=".ParkEaseApp".
 */
public class ParkEaseApp extends Application {

    private static final String TAG = "ParkEaseApp";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySaved(this);   // Light / Dark / System choice
        setupFirebase();
    }

    /**
     * Turns on Firebase offline caching: data already loaded stays available
     * without internet, and writes are sent when the connection returns.
     * Must be called once, before any other Realtime Database call.
     */
    private void setupFirebase() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            // google-services.json has not been added yet
            Log.w(TAG, "Firebase is not configured. Add app/google-services.json.");
            return;
        }
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Could not enable Firebase offline persistence", e);
        }
    }
}

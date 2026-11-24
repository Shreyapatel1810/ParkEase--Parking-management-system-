package com.parkease.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

/**
 * Tells a screen when the internet connection is lost or comes back, so it can show or
 * hide the "You're offline" banner. Call start() in onStart() and stop() in onStop().
 */
public class NetworkMonitor {

    public interface Listener {
        void onConnectionChanged(boolean online);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback callback;

    public NetworkMonitor(@NonNull Context context, @NonNull Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || callback != null) return;
        listener.onConnectionChanged(NetworkUtils.isOnline(context));   // current state
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                main.post(() -> listener.onConnectionChanged(true));
            }

            @Override
            public void onLost(@NonNull Network network) {
                main.post(() -> listener.onConnectionChanged(NetworkUtils.isOnline(context)));
            }
        };
        cm.registerDefaultNetworkCallback(callback);
    }

    public void stop() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
        }
        callback = null;
    }
}

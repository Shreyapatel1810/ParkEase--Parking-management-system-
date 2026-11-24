package com.parkease.app.ui.common;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.R;
import com.parkease.app.models.User;
import com.parkease.app.repository.AuthRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.ui.admin.AdminMainActivity;
import com.parkease.app.ui.auth.LoginActivity;
import com.parkease.app.ui.customer.CustomerMainActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.SessionManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One place that decides which screen opens next.
 *
 * <pre>
 * SplashActivity
 *   ├── onboarding not completed ─────────► OnboardingActivity ─► openEntryScreen()
 *   └── onboarding completed ─► openEntryScreen()
 *
 * openEntryScreen()
 *   ├── nobody logged in ─────────────────► LoginActivity
 *   ├── logged in but "Keep me signed in" off ─► log out ─► LoginActivity
 *   └── logged in ─► load profile from Firebase
 *          ├── role "admin"    ─► AdminMainActivity
 *          ├── role "customer" ─► CustomerMainActivity
 *          └── blocked / missing profile ─► log out ─► LoginActivity (with message)
 * </pre>
 */
public final class AppNavigator {

    /** If the profile can't be loaded in this time (e.g. bad network), use the cached role. */
    private static final long PROFILE_TIMEOUT_MS = 6000;

    private AppNavigator() { }

    /** Called by the splash screen when its animation has finished. */
    public static void openAfterSplash(@NonNull Activity from) {
        if (SessionManager.isOnboardingCompleted(from)) {
            openEntryScreen(from);
        } else {
            go(from, new Intent(from, OnboardingActivity.class));
        }
    }

    /** Login screen, or the right dashboard if a session already exists. */
    public static void openEntryScreen(@NonNull Activity from) {
        AuthRepository auth = new AuthRepository(from);

        if (!auth.isLoggedIn()) {
            openLogin(from, null);
            return;
        }
        if (!SessionManager.isRememberMe(from)) {
            auth.logout();                     // user chose not to stay signed in
            openLogin(from, null);
            return;
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        Handler handler = new Handler(Looper.getMainLooper());

        // Fallback: slow/no network → trust the role saved at the last login
        Runnable timeout = () -> {
            if (finished.getAndSet(true)) return;
            String cachedRole = SessionManager.getCachedRole(from);
            if (cachedRole != null) {
                openHomeForRole(from, cachedRole);
            } else {
                openLogin(from, null);
            }
        };
        handler.postDelayed(timeout, PROFILE_TIMEOUT_MS);

        auth.loadCurrentUserProfile(new DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (finished.getAndSet(true)) return;
                handler.removeCallbacks(timeout);
                openHomeForRole(from, user.getRole());
            }

            @Override
            public void onError(@NonNull String message) {
                if (finished.getAndSet(true)) return;
                handler.removeCallbacks(timeout);
                // Blocked or missing profile → repository already logged out
                if (!auth.isLoggedIn()) {
                    openLogin(from, message);
                    return;
                }
                // Network problem but still logged in → use the cached role if we have one
                String cachedRole = SessionManager.getCachedRole(from);
                if (cachedRole != null) {
                    openHomeForRole(from, cachedRole);
                } else {
                    openLogin(from, message);
                }
            }
        });
    }

    /** Customer → customer dashboard, admin → admin dashboard. */
    public static void openHome(@NonNull Activity from, @NonNull User user) {
        openHomeForRole(from, user.getRole());
    }

    /**
     * Opens the login screen and clears everything behind it.
     * @param message optional message to show on the login screen (e.g. "account blocked")
     */
    public static void openLogin(@NonNull Activity from, @Nullable String message) {
        Intent intent = new Intent(from, LoginActivity.class);
        if (message != null) intent.putExtra(Constants.EXTRA_MESSAGE, message);
        go(from, intent);
    }

    // ------------------------------------------------------------------ helpers

    private static void openHomeForRole(@NonNull Activity from, @Nullable String role) {
        Class<? extends Activity> target = Constants.ROLE_ADMIN.equals(role)
                ? AdminMainActivity.class
                : CustomerMainActivity.class;
        go(from, new Intent(from, target));
    }

    /** Opens a screen with a fade and removes all previous screens (Back won't return). */
    private static void go(@NonNull Activity from, @NonNull Intent intent) {
        if (from.isFinishing() || from.isDestroyed()) return;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ActivityOptions fade = ActivityOptions.makeCustomAnimation(from, R.anim.fade_in, R.anim.fade_out);
        from.startActivity(intent, fade.toBundle());
        from.finish();
    }
}

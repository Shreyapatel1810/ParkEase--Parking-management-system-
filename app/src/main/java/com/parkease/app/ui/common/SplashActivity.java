package com.parkease.app.ui.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivitySplashBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * STEP 4 – Splash screen (the app's launcher screen).
 *
 * 1. Plays an entrance animation: logo pops in, name and tagline slide up,
 *    rings and glow pulse gently.
 * 2. After SPLASH_DURATION_MS it asks AppNavigator where to go:
 *    first launch → Onboarding, later launches → entry screen (Login from Step 5).
 *
 * If the user presses Back during the splash, the app simply closes
 * (the pending navigation is cancelled in onDestroy).
 */
public class SplashActivity extends BaseActivity {

    private static final long SPLASH_DURATION_MS = 2200;

    private ActivitySplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable navigateNext = () -> AppNavigator.openAfterSplash(this);
    private final List<ObjectAnimator> loopingAnimators = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarsPadding(binding.bottomGroup);

        binding.tvVersion.setText(getString(R.string.splash_version, getVersionName()));

        playEntranceAnimation();
        handler.postDelayed(navigateNext, SPLASH_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(navigateNext);
        for (ObjectAnimator a : loopingAnimators) a.cancel();   // stop endless animations (no leaks)
        super.onDestroy();
    }

    /** White navigation bar icons too, because the whole screen is navy. */
    @NonNull
    @Override
    protected SystemBarStyle navigationBarStyle() {
        return SystemBarStyle.dark(Color.TRANSPARENT);
    }

    // ------------------------------------------------------------------ animation

    private void playEntranceAnimation() {
        // Starting state: everything invisible / slightly moved
        binding.imgLogo.setScaleX(0.4f);
        binding.imgLogo.setScaleY(0.4f);
        binding.imgLogo.setAlpha(0f);
        binding.logoGlow.setAlpha(0f);
        hideBelow(binding.tvAppName, 40f);
        hideBelow(binding.tvTagline, 40f);
        binding.bottomGroup.setAlpha(0f);

        // 1. Logo pops in with a small bounce
        binding.imgLogo.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(650)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        // 2. Glow fades in, then breathes slowly
        binding.logoGlow.animate().alpha(1f).setStartDelay(250).setDuration(600)
                .withEndAction(() -> pulse(binding.logoGlow, 1.12f, 1400))
                .start();

        // 3. Name, then tagline, slide up
        slideUp(binding.tvAppName, 450);
        slideUp(binding.tvTagline, 650);

        // 4. Progress + version fade in last
        binding.bottomGroup.animate().alpha(1f).setStartDelay(900).setDuration(500).start();

        // Background rings drift gently the whole time
        pulse(binding.ringLarge, 1.06f, 2400);
        pulse(binding.ringSmall, 1.08f, 2000);
    }

    private void hideBelow(@NonNull View view, float distanceDp) {
        view.setAlpha(0f);
        view.setTranslationY(distanceDp * getResources().getDisplayMetrics().density);
    }

    private void slideUp(@NonNull View view, long delayMs) {
        view.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(550)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
    }

    /** Endless gentle grow-and-shrink. */
    private void pulse(@NonNull View view, float scale, long durationMs) {
        ObjectAnimator x = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, scale);
        ObjectAnimator y = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, scale);
        for (ObjectAnimator a : new ObjectAnimator[]{x, y}) {
            a.setDuration(durationMs);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            a.start();
            loopingAnimators.add(a);
        }
    }

    // ------------------------------------------------------------------ helpers

    @NonNull
    private String getVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "1.0" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }
}

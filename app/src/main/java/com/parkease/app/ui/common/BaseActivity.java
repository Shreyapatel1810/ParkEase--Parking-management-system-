package com.parkease.app.ui.common;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

/**
 * Parent class for every ParkEase screen. Gives all screens the same behaviour:
 * <ul>
 *   <li>Edge-to-edge drawing (required on Android 15): the navy header goes behind
 *       the status bar, with white status icons.</li>
 *   <li>Helpers to keep content clear of the status bar and navigation bar.</li>
 *   <li>A one-line way to show a Snackbar message.</li>
 * </ul>
 * Usage: {@code public class LoginActivity extends BaseActivity { ... }}
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this,
                SystemBarStyle.dark(Color.TRANSPARENT),                       // white status icons
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));   // nav icons follow theme
        super.onCreate(savedInstanceState);
    }

    /** Adds the status bar height to the view's top padding (use on the header). */
    protected void applyStatusBarPadding(@NonNull View view) {
        final int initialTop = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), initialTop + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /**
     * Gives a thin navy strip the height of the status bar. Place it as the last child of a
     * FrameLayout root (gravity top) so scrolling content never shows behind the clock and
     * battery icons.
     */
    protected void applyStatusBarScrim(@NonNull View scrim) {
        ViewCompat.setOnApplyWindowInsetsListener(scrim, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            if (v.getLayoutParams().height != bars.top) {
                v.getLayoutParams().height = bars.top;
                v.requestLayout();
            }
            return insets;
        });
    }

    /** Adds the navigation bar height to the view's bottom padding (use on scrolling content). */
    protected void applyNavigationBarPadding(@NonNull View view) {
        final int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), initialBottom + bars.bottom);
            return insets;
        });
    }

    /** Short message at the bottom of the screen. */
    protected void showMessage(@NonNull String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    protected void showMessage(@StringRes int messageRes) {
        showMessage(getString(messageRes));
    }
}

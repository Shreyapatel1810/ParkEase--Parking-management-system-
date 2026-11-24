package com.parkease.app.utils;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps content clear of the status bar (top) and navigation bar (bottom) when the app
 * draws edge-to-edge. Used by BaseActivity and by fragments (which can't extend it).
 */
public final class InsetsHelper {

    private InsetsHelper() { }

    /** Adds the status bar height to the view's top padding (use on headers). */
    public static void statusBarPadding(@NonNull View view) {
        final int top = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), top + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Adds the navigation bar height to the view's bottom padding. */
    public static void navigationBarPadding(@NonNull View view) {
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Makes a thin navy strip exactly as tall as the status bar. */
    public static void statusBarScrim(@NonNull View scrim) {
        ViewCompat.setOnApplyWindowInsetsListener(scrim, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            if (v.getLayoutParams().height != bars.top) {
                v.getLayoutParams().height = bars.top;
                v.requestLayout();
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(scrim);
    }
}

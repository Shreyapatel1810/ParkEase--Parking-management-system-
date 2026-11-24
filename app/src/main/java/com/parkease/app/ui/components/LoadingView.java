package com.parkease.app.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.parkease.app.R;
import com.parkease.app.databinding.ViewLoadingBinding;

/**
 * Loading indicator with a message.
 * <ul>
 *   <li>Overlay mode (app:lvOverlay="true", default): covers the whole screen with a dark
 *       scrim and blocks taps – use while saving a booking or logging in.
 *       Place it as the LAST child of the screen's root FrameLayout, with visibility="gone".</li>
 *   <li>Inline mode (app:lvOverlay="false"): just the spinner and text inside a section.</li>
 * </ul>
 * Java: {@code loadingView.show();  ...  loadingView.hide();}
 */
public class LoadingView extends FrameLayout {

    private static final long FADE_MS = 200;

    private final ViewLoadingBinding binding;
    private final boolean overlay;

    public LoadingView(@NonNull Context context) {
        this(context, null);
    }

    public LoadingView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        binding = ViewLoadingBinding.inflate(LayoutInflater.from(context), this);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LoadingView);
        String message;
        try {
            message = a.getString(R.styleable.LoadingView_lvMessage);
            overlay = a.getBoolean(R.styleable.LoadingView_lvOverlay, true);
        } finally {
            a.recycle();
        }
        setMessage(message);

        if (overlay) {
            setBackgroundColor(ContextCompat.getColor(context, R.color.scrim));
            setClickable(true);   // swallow taps so the screen behind can't be used
            setFocusable(true);
        } else {
            binding.loadingCard.setCardElevation(0f);
            binding.loadingCard.setCardBackgroundColor(Color.TRANSPARENT);
        }
    }

    public void setMessage(@Nullable CharSequence message) {
        binding.tvLoadingMessage.setText(message);
        binding.tvLoadingMessage.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
    }

    /** Fades the loading view in. */
    public void show() {
        if (getVisibility() == View.VISIBLE) return;
        setAlpha(0f);
        setVisibility(View.VISIBLE);
        animate().alpha(1f).setDuration(FADE_MS).setListener(null).start();
    }

    /** Fades the loading view out, then removes it from the layout. */
    public void hide() {
        if (getVisibility() != View.VISIBLE) return;
        animate().alpha(0f).setDuration(FADE_MS)
                .withEndAction(() -> setVisibility(View.GONE))
                .start();
    }

    public boolean isOverlay() {
        return overlay;
    }
}

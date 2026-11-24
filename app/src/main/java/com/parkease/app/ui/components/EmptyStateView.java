package com.parkease.app.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.R;
import com.parkease.app.databinding.ViewEmptyStateBinding;

/**
 * Friendly "nothing here yet" message with an icon and an optional action button.
 * Show it when a list is empty (no bookings, no favourites, no search results).
 *
 * XML:
 * <pre>
 * &lt;com.parkease.app.ui.components.EmptyStateView
 *     app:esIcon="@drawable/ic_bookings"
 *     app:esTitle="@string/..."
 *     app:esMessage="@string/..."
 *     app:esActionText="@string/..." /&gt;
 * </pre>
 * Java: {@code emptyView.setOnActionClickListener(v -> openSearch());}
 */
public class EmptyStateView extends LinearLayout {

    private final ViewEmptyStateBinding binding;

    public EmptyStateView(@NonNull Context context) {
        this(context, null);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        int pad = getResources().getDimensionPixelSize(R.dimen.space_xl);
        setPadding(pad, pad, pad, pad);

        binding = ViewEmptyStateBinding.inflate(LayoutInflater.from(context), this);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EmptyStateView);
        try {
            setIcon(a.getResourceId(R.styleable.EmptyStateView_esIcon, R.drawable.ic_search));
            setTitle(a.getString(R.styleable.EmptyStateView_esTitle));
            setMessage(a.getString(R.styleable.EmptyStateView_esMessage));
            setActionText(a.getString(R.styleable.EmptyStateView_esActionText));
        } finally {
            a.recycle();
        }
    }

    public void setIcon(@DrawableRes int iconRes) {
        binding.imgEmptyIcon.setImageResource(iconRes);
    }

    public void setTitle(@Nullable CharSequence title) {
        binding.tvEmptyTitle.setText(title);
    }

    public void setMessage(@Nullable CharSequence message) {
        binding.tvEmptyMessage.setText(message);
        binding.tvEmptyMessage.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
    }

    /** Button text. The button is hidden when the text is empty. */
    public void setActionText(@Nullable CharSequence text) {
        binding.btnEmptyAction.setText(text);
        binding.btnEmptyAction.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    public void setOnActionClickListener(@Nullable View.OnClickListener listener) {
        binding.btnEmptyAction.setOnClickListener(listener);
    }
}

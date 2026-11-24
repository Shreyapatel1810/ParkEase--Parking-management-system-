package com.parkease.app.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.R;

/**
 * One view for the three "not content" states of a list or section:
 * <ul>
 *   <li>{@link #showLoading()} – spinner + "Loading…"</li>
 *   <li>{@link #showEmpty} – icon, title, message, optional button</li>
 *   <li>{@link #showError} – error message + "Try again" button</li>
 *   <li>{@link #hide()} – the real content (e.g. a RecyclerView next to it) is visible</li>
 * </ul>
 * Put it next to the content, e.g. in the same FrameLayout, and switch between them.
 */
public class StateView extends FrameLayout {

    private final LoadingView loadingView;
    private final EmptyStateView emptyView;

    public StateView(@NonNull Context context) {
        this(context, null);
    }

    public StateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        loadingView = new LoadingView(context, null);
        loadingView.setMessage(context.getString(R.string.loading));
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(loadingView, lp);

        emptyView = new EmptyStateView(context, null);
        addView(emptyView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // LoadingView defaults to "overlay" mode – make it a plain inline spinner here
        loadingView.makeInline();
        hide();
    }

    public void showLoading() {
        setVisibility(VISIBLE);
        loadingView.setAlpha(1f);
        loadingView.setVisibility(VISIBLE);
        emptyView.setVisibility(GONE);
    }

    public void showEmpty(@DrawableRes int icon, @NonNull CharSequence title, @Nullable CharSequence message,
                          @Nullable CharSequence actionText, @Nullable View.OnClickListener action) {
        setVisibility(VISIBLE);
        loadingView.setVisibility(GONE);
        emptyView.setVisibility(VISIBLE);
        emptyView.setIcon(icon);
        emptyView.setTitle(title);
        emptyView.setMessage(message);
        emptyView.setActionText(action == null ? null : actionText);
        emptyView.setOnActionClickListener(action);
    }

    public void showError(@NonNull CharSequence message, @Nullable View.OnClickListener retry) {
        showEmpty(R.drawable.ic_wifi_off, getContext().getString(R.string.state_error_title), message,
                getContext().getString(R.string.action_retry), retry);
    }

    /** Hides all states so the real content shows. */
    public void hide() {
        setVisibility(GONE);
    }
}

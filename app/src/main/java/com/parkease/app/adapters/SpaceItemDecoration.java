package com.parkease.app.adapters;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Equal gaps between RecyclerView items (no gap after the last one). */
public class SpaceItemDecoration extends RecyclerView.ItemDecoration {

    private final int spacePx;
    private final boolean horizontal;

    public SpaceItemDecoration(int spacePx, boolean horizontal) {
        this.spacePx = spacePx;
        this.horizontal = horizontal;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        int last = state.getItemCount() - 1;
        if (position == RecyclerView.NO_POSITION || position == last) return;
        if (horizontal) outRect.right = spacePx;
        else outRect.bottom = spacePx;
    }
}

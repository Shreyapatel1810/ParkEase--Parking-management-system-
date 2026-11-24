package com.parkease.app.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

/**
 * A live Firebase listener that keeps sending updates until you call {@link #cancel()}.
 * Screens start listening in onStart() and cancel in onStop(), so nothing runs in the
 * background when the screen is hidden (saves battery and data).
 */
public final class Subscription {

    /** Used when there is nothing to cancel (e.g. Firebase not configured). */
    public static final Subscription EMPTY = new Subscription(null, null);

    @Nullable private final Query query;
    @Nullable private final ValueEventListener listener;

    Subscription(@Nullable Query query, @Nullable ValueEventListener listener) {
        this.query = query;
        this.listener = listener;
    }

    public void cancel() {
        if (query != null && listener != null) {
            query.removeEventListener(listener);
        }
    }

    /** Starts listening and returns the handle used to stop. */
    @NonNull
    static Subscription listen(@NonNull Query query, @NonNull ValueEventListener listener) {
        query.addValueEventListener(listener);
        return new Subscription(query, listener);
    }
}

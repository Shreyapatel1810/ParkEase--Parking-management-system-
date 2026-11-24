package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.models.Review;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Reviews of one parking place:  reviews/{locationId}/{reviewerUid}.
 * Writing reviews (after a completed booking) comes in the reviews module; this step reads them.
 */
public class ReviewRepository {

    /** How many of the newest reviews to download for the details screen. */
    public static final int MAX_REVIEWS = 20;

    private final Context context;

    public ReviewRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** Newest reviews first, updated live. */
    @NonNull
    public Subscription observeReviews(@NonNull String locationId, @NonNull DataCallback<List<Review>> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(
                FirebaseDatabase.getInstance().getReference(Constants.NODE_REVIEWS).child(locationId)
                        .orderByChild("createdAt").limitToLast(MAX_REVIEWS),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Review> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Review r = child.getValue(Review.class);
                            if (r != null) {
                                r.setReviewerId(child.getKey());
                                list.add(r);
                            }
                        }
                        list.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                        callback.onSuccess(list);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
                    }
                });
    }

    /** Counts reviews per star (index 1..5) for the rating bars. */
    @NonNull
    public static int[] starCounts(@NonNull List<Review> reviews) {
        int[] counts = new int[6];
        for (Review r : reviews) {
            int stars = Math.max(1, Math.min(5, r.getRating()));
            counts[stars]++;
        }
        return counts;
    }
}

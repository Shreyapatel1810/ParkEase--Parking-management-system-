package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.models.Promotion;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;

/** The current offer for the home screen:  promotions/{id}  (highest priority active one). */
public class PromotionRepository {

    private final Context context;

    public PromotionRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** Sends the best active promotion, or null when there is none. */
    @NonNull
    public Subscription observeTopPromotion(@NonNull DataCallback<Promotion> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(
                FirebaseDatabase.getInstance().getReference(Constants.NODE_PROMOTIONS),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Promotion best = null;
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Promotion p = child.getValue(Promotion.class);
                            if (p != null && p.isActive() && isBetter(p, best)) {
                                p.setId(child.getKey());
                                best = p;
                            }
                        }
                        callback.onSuccess(best);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
                    }
                });
    }

    private static boolean isBetter(@NonNull Promotion p, @Nullable Promotion current) {
        return current == null || p.getPriority() > current.getPriority();
    }
}

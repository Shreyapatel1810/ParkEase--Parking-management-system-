package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * The customer's favourite locations:  favourites/{uid}/{locationId} = time added.
 *
 * Toggling works even offline: Firebase shows the change immediately on this phone and
 * uploads it automatically when the connection returns.
 */
public class FavouriteRepository {

    private final Context context;

    public FavouriteRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** IDs of my favourite locations, updated live. */
    @NonNull
    public Subscription observeFavouriteIds(@NonNull DataCallback<Set<String>> callback) {
        DatabaseReference ref = myFavourites();
        if (ref == null) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(ref, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> ids = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) ids.add(child.getKey());
                callback.onSuccess(ids);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
            }
        });
    }

    /** Adds (true) or removes (false) a favourite. */
    public void setFavourite(@NonNull String locationId, boolean favourite,
                             @NonNull DataCallback<Void> callback) {
        DatabaseReference ref = myFavourites();
        if (ref == null) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return;
        }
        DatabaseReference item = ref.child(locationId);
        (favourite ? item.setValue(ServerValue.TIMESTAMP) : item.removeValue())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    private DatabaseReference myFavourites() {
        if (!FirebaseHelper.isConfigured(context)) return null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        return FirebaseDatabase.getInstance().getReference(Constants.NODE_FAVOURITES).child(user.getUid());
    }
}

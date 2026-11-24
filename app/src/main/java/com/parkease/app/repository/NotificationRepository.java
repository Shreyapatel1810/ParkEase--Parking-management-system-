package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.models.AppNotification;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

/** My notifications:  notifications/{uid}/{notifId}. Newest first, updated live. */
public class NotificationRepository {

    private final Context context;

    public NotificationRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public Subscription observeMyNotifications(@NonNull DataCallback<List<AppNotification>> callback) {
        DatabaseReference ref = myNotifications();
        if (ref == null) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(ref, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<AppNotification> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    AppNotification n = child.getValue(AppNotification.class);
                    if (n != null) {
                        n.setId(child.getKey());
                        list.add(n);
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

    /** Marks one notification as read (only the "isRead" field may be changed by the user). */
    public void markRead(@NonNull String notificationId) {
        DatabaseReference ref = myNotifications();
        if (ref != null) ref.child(notificationId).child("isRead").setValue(true);
    }

    public static int countUnread(@NonNull List<AppNotification> list) {
        int count = 0;
        for (AppNotification n : list) if (!n.isRead()) count++;
        return count;
    }

    private DatabaseReference myNotifications() {
        if (!FirebaseHelper.isConfigured(context)) return null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        return FirebaseDatabase.getInstance().getReference(Constants.NODE_NOTIFICATIONS).child(user.getUid());
    }
}

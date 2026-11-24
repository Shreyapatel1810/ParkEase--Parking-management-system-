package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.models.Booking;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The logged-in customer's bookings (Firebase  bookings/  where userId == my uid).
 * Creating bookings comes in the booking module; this step reads them.
 */
public class BookingRepository {

    private final Context context;

    public BookingRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * All my bookings, newest start time first, updated live.
     * The database rules only allow this exact query (orderByChild userId = my uid),
     * so a customer can never read other people's bookings.
     */
    @NonNull
    public Subscription observeMyBookings(@NonNull DataCallback<List<Booking>> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError(context.getString(R.string.err_session_expired));
            return Subscription.EMPTY;
        }
        return Subscription.listen(
                FirebaseDatabase.getInstance().getReference(Constants.NODE_BOOKINGS)
                        .orderByChild("userId").equalTo(user.getUid()),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Booking> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Booking booking = child.getValue(Booking.class);
                            if (booking != null) {
                                booking.setId(child.getKey());
                                list.add(booking);
                            }
                        }
                        list.sort((a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));
                        callback.onSuccess(list);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
                    }
                });
    }

    /** The next confirmed / active booking that hasn't ended, or null. */
    @Nullable
    public static Booking findNextUpcoming(@NonNull List<Booking> bookings) {
        long now = System.currentTimeMillis();
        Booking next = null;
        for (Booking b : bookings) {
            if (b.isUpcoming(now) && (next == null || b.getStartTime() < next.getStartTime())) {
                next = b;
            }
        }
        return next;
    }

    /** Splits bookings for the three tabs of "My bookings". */
    @NonNull
    public static List<Booking> filter(@NonNull List<Booking> all, @NonNull String tab) {
        long now = System.currentTimeMillis();
        List<Booking> result = new ArrayList<>();
        for (Booking b : all) {
            boolean upcoming = b.isUpcoming(now);
            switch (tab) {
                case TAB_UPCOMING:
                    if (upcoming) result.add(b);
                    break;
                case TAB_CANCELLED:
                    if (b.isCancelled()) result.add(b);
                    break;
                default: // TAB_PAST: completed, expired, or confirmed but already ended
                    if (!upcoming && !b.isCancelled()
                            && !Constants.BOOKING_PENDING_PAYMENT.equals(b.getStatus())) result.add(b);
                    break;
            }
        }
        if (TAB_UPCOMING.equals(tab)) {
            Collections.sort(result, Comparator.comparingLong(Booking::getStartTime)); // soonest first
        }
        return result;
    }

    public static final String TAB_UPCOMING = "upcoming";
    public static final String TAB_PAST = "past";
    public static final String TAB_CANCELLED = "cancelled";
}

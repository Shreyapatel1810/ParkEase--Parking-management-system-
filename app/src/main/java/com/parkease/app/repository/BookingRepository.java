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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.parkease.app.models.Booking;
import com.parkease.app.models.CancellationPolicy;
import com.parkease.app.models.TimeWindow;
import com.parkease.app.utils.NetworkUtils;

import java.util.HashMap;
import java.util.Map;
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

    /** One booking, updated live (status changes from confirm / cancel / gate scans). */
    @NonNull
    public Subscription observeBooking(@NonNull String bookingId, @NonNull DataCallback<Booking> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(db().child(Constants.NODE_BOOKINGS).child(bookingId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Booking b = snapshot.getValue(Booking.class);
                if (b != null) b.setId(snapshot.getKey());
                callback.onSuccess(b);             // null = deleted (e.g. hold released)
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
            }
        });
    }

    // ================================================================== confirm / cancel / expire

    /**
     * CONFIRM: PENDING_PAYMENT → CONFIRMED and every 30-minute block HOLD → BOOKED,
     * in ONE atomic update. The rules accept it only if the hold has not expired and
     * belongs to this user – so an expired hold that someone else re-booked can never be
     * confirmed (no double booking).
     */
    public void confirmBooking(@NonNull Booking booking, @NonNull DataCallback<Void> callback) {
        if (!ready(callback)) return;
        if (!booking.isHoldActive(System.currentTimeMillis())) {
            callback.onError(context.getString(R.string.booking_err_hold_expired));
            return;
        }
        Map<String, Object> u = new HashMap<>();
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId() + "/status", Constants.BOOKING_CONFIRMED);
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId() + "/confirmedAt", ServerValue.TIMESTAMP);
        for (String path : blockPaths(booking)) u.put(path + "/status", Constants.SCHEDULE_BOOKED);

        db().updateChildren(u)
                .addOnSuccessListener(x -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(isPermission(e)
                        ? context.getString(R.string.booking_err_hold_expired)
                        : FirebaseErrorMapper.fromDatabase(context, e)));
    }

    /**
     * CANCEL: CONFIRMED → CANCELLED with the refund from the cancellation policy, and the
     * slot's blocks are deleted so others can book that time. Only before the start time.
     */
    public void cancelBooking(@NonNull Booking booking, @NonNull CancellationPolicy policy,
                              @NonNull DataCallback<Double> callback) {
        if (!ready(callback)) return;
        long now = System.currentTimeMillis();
        if (!policy.canCancel(booking, now)) {
            callback.onError(context.getString(R.string.booking_err_cannot_cancel));
            return;
        }
        double refund = policy.refundFor(booking, now);
        Map<String, Object> u = new HashMap<>();
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId() + "/status", Constants.BOOKING_CANCELLED);
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId() + "/cancelledAt", ServerValue.TIMESTAMP);
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId() + "/refundAmount", refund);
        for (String path : blockPaths(booking)) u.put(path, null);     // free the slot

        db().updateChildren(u)
                .addOnSuccessListener(x -> callback.onSuccess(refund))
                .addOnFailureListener(e -> callback.onError(isPermission(e)
                        ? context.getString(R.string.booking_err_cannot_cancel)
                        : FirebaseErrorMapper.fromDatabase(context, e)));
    }

    /** Releases an unconfirmed hold: deletes the pending booking and its blocks. */
    public void releaseHold(@NonNull Booking booking, @NonNull DataCallback<Void> callback) {
        if (!ready(callback)) return;
        Map<String, Object> u = new HashMap<>();
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId(), null);
        for (String path : blockPaths(booking)) u.put(path, null);
        db().updateChildren(u)
                .addOnSuccessListener(x -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    /**
     * EXPIRE (automatic): marks my stale bookings EXPIRED and frees their blocks –
     * holds that ran out and confirmed bookings nobody arrived for (no-show after the grace
     * period). Called when bookings are loaded; harmless if it fails (e.g. offline).
     */
    public void expireStaleBookings(@NonNull List<Booking> bookings) {
        if (!FirebaseHelper.isConfigured(context) || !NetworkUtils.isOnline(context)) return;
        long now = System.currentTimeMillis();
        for (Booking b : bookings) {
            if (!b.shouldExpire(now)) continue;
            Map<String, Object> u = new HashMap<>();
            u.put(Constants.NODE_BOOKINGS + "/" + b.getId() + "/status", Constants.BOOKING_EXPIRED);
            // No-show: free its BOOKED blocks. (An expired HOLD's blocks are already treated as
            // free – and may since belong to someone else – so they are left alone.)
            if (b.isConfirmed()) {
                for (String path : blockPaths(b)) u.put(path, null);
            }
            db().updateChildren(u);
        }
    }

    /** Reads appConfig/cancellation (or uses the defaults if it isn't set). */
    public void loadCancellationPolicy(@NonNull DataCallback<CancellationPolicy> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onSuccess(new CancellationPolicy());
            return;
        }
        db().child(Constants.NODE_APP_CONFIG).child("cancellation").get()
                .addOnSuccessListener(s -> {
                    CancellationPolicy p = s.getValue(CancellationPolicy.class);
                    callback.onSuccess(p != null ? p : new CancellationPolicy());
                })
                .addOnFailureListener(e -> callback.onSuccess(new CancellationPolicy()));
    }

    // ================================================================== helpers

    /** Database paths of the booking's 30-minute blocks in slotSchedule. */
    @NonNull
    private static List<String> blockPaths(@NonNull Booking b) {
        TimeWindow w = TimeWindow.ofMillis(b.getStartTime(), b.getEndTime());
        List<String> paths = new ArrayList<>();
        for (String key : w.blockKeys()) {
            paths.add(Constants.NODE_SLOT_SCHEDULE + "/" + b.getLocationId() + "/" + w.dateKey()
                    + "/" + b.getSlotId() + "/" + key);
        }
        return paths;
    }

    private boolean ready(@NonNull DataCallback<?> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return false;
        }
        if (!NetworkUtils.isOnline(context)) {
            callback.onError(context.getString(R.string.err_no_internet));
            return false;
        }
        return true;
    }

    private static boolean isPermission(@NonNull Exception e) {
        return e.getMessage() != null && e.getMessage().toLowerCase().contains("permission");
    }

    private DatabaseReference db() {
        return FirebaseDatabase.getInstance().getReference();
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

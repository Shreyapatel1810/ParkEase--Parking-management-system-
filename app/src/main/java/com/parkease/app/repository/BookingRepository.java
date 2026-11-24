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

    // Confirming a booking happens through payment – see PaymentRepository.pay (Step 12).

    /**
     * CANCEL: CONFIRMED → CANCELLED with the refund from the cancellation policy, and the
     * slot's blocks are deleted so others can book that time. Only before the start time.
     * The linked payment changes in the same update: a successful (demo) payment becomes
     * REFUNDED; an unpaid cash payment becomes FAILED (nothing was charged, refund ₹0).
     */
    public void cancelBooking(@NonNull Booking booking, @NonNull CancellationPolicy policy,
                              @NonNull DataCallback<Double> callback) {
        if (!ready(callback)) return;
        long now = System.currentTimeMillis();
        if (!policy.canCancel(booking, now)) {
            callback.onError(context.getString(R.string.booking_err_cannot_cancel));
            return;
        }
        double refund = booking.isCashPayment() ? 0 : policy.refundFor(booking, now);
        Map<String, Object> u = new HashMap<>();
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId() + "/status", Constants.BOOKING_CANCELLED);
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId() + "/cancelledAt", ServerValue.TIMESTAMP);
        u.put(Constants.NODE_BOOKINGS + "/" + booking.getId() + "/refundAmount", refund);
        for (String path : blockPaths(booking)) u.put(path, null);     // free the slot
        if (booking.getPaymentId() != null) {
            String p = Constants.NODE_PAYMENTS + "/" + booking.getPaymentId();
            if (booking.isCashPayment()) {
                u.put(p + "/status", Constants.PAYMENT_FAILED);
                u.put(p + "/failureReason", context.getString(R.string.pay_fail_reason_cancelled));
            } else {
                u.put(p + "/status", Constants.PAYMENT_REFUNDED);
                u.put(p + "/refundAmount", refund);
                u.put(p + "/refundedAt", ServerValue.TIMESTAMP);
            }
        }

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
                // Unpaid cash booking nobody used → its payment is closed as FAILED
                if (b.isCashPayment() && b.getPaymentId() != null) {
                    String p = Constants.NODE_PAYMENTS + "/" + b.getPaymentId();
                    u.put(p + "/status", Constants.PAYMENT_FAILED);
                    u.put(p + "/failureReason", context.getString(R.string.pay_fail_reason_no_show));
                }
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
    static List<String> blockPaths(@NonNull Booking b) {
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

    // ================================================================== My Bookings tabs (Step 14)

    public static final String TAB_UPCOMING = "upcoming";
    public static final String TAB_ACTIVE = "active";
    public static final String TAB_HISTORY = "history";

    /**
     * Which tab a booking belongs in (or null = not listed).
     * <ul>
     *   <li>UPCOMING – confirmed and not started yet, or a hold still waiting for payment</li>
     *   <li>ACTIVE   – vehicle is parked (ACTIVE), or a confirmed booking whose time is running now</li>
     *   <li>HISTORY  – completed, cancelled, expired, or confirmed but already ended (no-show)</li>
     * </ul>
     * Abandoned holds (never paid, then expired) are left out – they were never real bookings.
     */
    @Nullable
    public static String tabOf(@NonNull Booking b, long now) {
        String status = b.getStatus() == null ? "" : b.getStatus();
        switch (status) {
            case Constants.BOOKING_PENDING_PAYMENT:
                return b.isHoldActive(now) && b.getEndTime() > now ? TAB_UPCOMING : null;
            case Constants.BOOKING_ACTIVE:
                return TAB_ACTIVE;
            case Constants.BOOKING_CONFIRMED:
                if (b.getEndTime() <= now) return TAB_HISTORY;
                return b.getStartTime() > now ? TAB_UPCOMING : TAB_ACTIVE;
            case Constants.BOOKING_EXPIRED:
                return b.getPaymentId() == null && b.getConfirmedAt() == 0 ? null : TAB_HISTORY;
            case Constants.BOOKING_COMPLETED:
            case Constants.BOOKING_CANCELLED:
                return TAB_HISTORY;
            default:
                return null;
        }
    }

    /** The bookings of one tab, in the order shown. */
    @NonNull
    public static List<Booking> filter(@NonNull List<Booking> all, @NonNull String tab) {
        long now = System.currentTimeMillis();
        List<Booking> result = new ArrayList<>();
        for (Booking b : all) {
            if (tab.equals(tabOf(b, now))) result.add(b);
        }
        if (TAB_UPCOMING.equals(tab)) {
            Collections.sort(result, Comparator.comparingLong(Booking::getStartTime));   // soonest first
        } else if (TAB_ACTIVE.equals(tab)) {
            Collections.sort(result, Comparator.comparingLong(Booking::getEndTime));     // ends soonest first
        } else {
            Collections.sort(result, (a, b) -> Long.compare(b.getStartTime(), a.getStartTime())); // newest first
        }
        return result;
    }

    /** History filter chips. */
    public static final String HISTORY_ALL = "all";

    /**
     * Search + status filter for the History tab.
     * Matches the booking code / ID, parking name, slot and vehicle number (spaces ignored).
     *
     * @param status HISTORY_ALL or a booking status (COMPLETED / CANCELLED / EXPIRED)
     */
    @NonNull
    public static List<Booking> search(@NonNull List<Booking> list, @Nullable String query, @NonNull String status) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        String qCompact = q.replaceAll("[^a-z0-9]", "");
        List<Booking> result = new ArrayList<>();
        for (Booking b : list) {
            if (!HISTORY_ALL.equals(status)) {
                // "Expired" also covers confirmed bookings that ended without entry
                String s = b.isConfirmed() ? Constants.BOOKING_EXPIRED : b.getStatus();
                if (!status.equals(s)) continue;
            }
            if (q.isEmpty() || contains(b.getBookingCode(), q) || contains(b.getId(), q)
                    || contains(b.getLocationName(), q) || contains(b.getSlotNumber(), q)
                    || (!qCompact.isEmpty() && contains(compact(b.getVehicleNumber()), qCompact))
                    || (!qCompact.isEmpty() && contains(compact(b.getBookingCode()), qCompact))) {
                result.add(b);
            }
        }
        return result;
    }

    private static boolean contains(@Nullable String text, @NonNull String q) {
        return text != null && text.toLowerCase(java.util.Locale.ROOT).contains(q);
    }

    @Nullable
    private static String compact(@Nullable String text) {
        return text == null ? null : text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}

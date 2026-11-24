package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.parkease.app.R;
import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.NetworkUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * STEP 13 – Gate operations for authorised staff (role "admin"): find a booking and record
 * vehicle ENTRY and EXIT.
 *
 * Each operation is ONE atomic multi-path update:
 * <pre>
 * ENTRY: bookings/{id}  status CONFIRMED → ACTIVE, entryTime, entryBy
 *        slots/{loc}/{slot}/status = OCCUPIED
 *        entryExitLogs/{logId} = { bookingId, type: ENTRY, at, by, … }
 * EXIT:  bookings/{id}  status ACTIVE → COMPLETED, exitTime, exitBy
 *        slots/{loc}/{slot}/status = AVAILABLE
 *        payments/{txn} PENDING → SUCCESS (cash collected)
 *        entryExitLogs/{logId} = { bookingId, type: EXIT, … }
 * </pre>
 * Duplicate scans can't record twice: the database rules only allow entryTime / exitTime to
 * be written ONCE and only with the right status change, so if two staff phones scan the
 * same ticket at the same moment, the second update is rejected by the server.
 */
public class GateRepository {

    private final Context context;

    public GateRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** Manual fallback: find a booking by its printed code (e.g. PE-7K3D9Q). */
    public void findByCode(@NonNull String code, @NonNull DataCallback<Booking> callback) {
        if (!ready(callback)) return;
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("PE-") && normalized.length() == 6) normalized = "PE-" + normalized;
        db().child(Constants.NODE_BOOKINGS).orderByChild("bookingCode").equalTo(normalized).limitToFirst(1).get()
                .addOnSuccessListener(snapshot -> {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Booking b = child.getValue(Booking.class);
                        if (b != null) {
                            b.setId(child.getKey());
                            callback.onSuccess(b);
                            return;
                        }
                    }
                    callback.onError(context.getString(R.string.gate_err_code_not_found));
                })
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    /** Records that the vehicle has entered. */
    public void recordEntry(@NonNull Booking booking, @NonNull DataCallback<Void> callback) {
        if (!ready(callback)) return;
        String uid = staffUid();
        String b = Constants.NODE_BOOKINGS + "/" + booking.getId();
        Map<String, Object> u = new HashMap<>();
        u.put(b + "/status", Constants.BOOKING_ACTIVE);
        u.put(b + "/entryTime", ServerValue.TIMESTAMP);
        u.put(b + "/entryBy", uid);
        u.put(slotStatusPath(booking), "OCCUPIED");
        u.put(newLogPath(), log(booking, Constants.GATE_ENTRY, uid, false));
        apply(u, R.string.gate_err_entry_duplicate, callback);
    }

    /**
     * Records that the vehicle has left.
     * @param payment the booking's payment – a cash payment still PENDING is marked collected
     */
    public void recordExit(@NonNull Booking booking, @Nullable Payment payment, @NonNull DataCallback<Void> callback) {
        if (!ready(callback)) return;
        String uid = staffUid();
        String b = Constants.NODE_BOOKINGS + "/" + booking.getId();
        boolean collectCash = payment != null && payment.isPending()
                && Constants.PAYMENT_CASH.equals(payment.getMethod());
        Map<String, Object> u = new HashMap<>();
        u.put(b + "/status", Constants.BOOKING_COMPLETED);
        u.put(b + "/exitTime", ServerValue.TIMESTAMP);
        u.put(b + "/exitBy", uid);
        u.put(slotStatusPath(booking), "AVAILABLE");
        if (collectCash) {
            String p = Constants.NODE_PAYMENTS + "/" + payment.getTransactionId();
            u.put(p + "/status", Constants.PAYMENT_SUCCESS);
            u.put(p + "/collectedBy", uid);
            u.put(p + "/collectedAt", ServerValue.TIMESTAMP);
        }
        u.put(newLogPath(), log(booking, Constants.GATE_EXIT, uid, collectCash));
        apply(u, R.string.gate_err_exit_duplicate, callback);
    }

    // ================================================================== helpers

    private void apply(@NonNull Map<String, Object> updates, int duplicateMessage,
                       @NonNull DataCallback<Void> callback) {
        db().updateChildren(updates)
                .addOnSuccessListener(x -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    boolean denied = e.getMessage() != null
                            && e.getMessage().toLowerCase(Locale.ROOT).contains("permission");
                    callback.onError(denied ? context.getString(duplicateMessage)
                            : FirebaseErrorMapper.fromDatabase(context, e));
                });
    }

    @NonNull
    private static Map<String, Object> log(@NonNull Booking b, @NonNull String type, @NonNull String uid,
                                           boolean cashCollected) {
        Map<String, Object> log = new HashMap<>();
        log.put("bookingId", b.getId());
        log.put("bookingCode", b.getBookingCode());
        log.put("type", type);
        log.put("at", ServerValue.TIMESTAMP);
        log.put("by", uid);
        log.put("vehicleNumber", b.getVehicleNumber());
        log.put("locationId", b.getLocationId());
        log.put("slotId", b.getSlotId());
        if (cashCollected) log.put("cashCollected", b.getAmount());
        return log;
    }

    @NonNull
    private static String slotStatusPath(@NonNull Booking b) {
        return Constants.NODE_SLOTS + "/" + b.getLocationId() + "/" + b.getSlotId() + "/status";
    }

    @NonNull
    private String newLogPath() {
        String key = db().child(Constants.NODE_ENTRY_EXIT_LOGS).push().getKey();
        return Constants.NODE_ENTRY_EXIT_LOGS + "/" + key;
    }

    @NonNull
    private static String staffUid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? "" : u.getUid();
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
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onError(context.getString(R.string.err_session_expired));
            return false;
        }
        return true;
    }

    private DatabaseReference db() {
        return FirebaseDatabase.getInstance().getReference();
    }
}

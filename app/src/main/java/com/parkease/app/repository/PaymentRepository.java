package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.NetworkUtils;
import com.parkease.app.utils.TicketCodec;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * STEP 12 – SIMULATED payments (college demo – no real money, no payment gateway).
 *
 * How a payment is saved (all in ONE atomic Firebase update):
 * <pre>
 *   payments/{txnId}                 = the payment record (simulated: true)
 *   bookings/{id}/status             = CONFIRMED            (success or cash only)
 *   bookings/{id}/paymentId          = txnId                (links booking → payment)
 *   slotSchedule/.../{block}/status  = BOOKED               (the slot is now booked)
 * </pre>
 * The database rules only accept a SUCCESS (or cash PENDING) payment when, in the same
 * update, the booking moves from PENDING_PAYMENT to CONFIRMED and points to this payment.
 * A booking can only make that move once – so there can never be two successful payments
 * for one booking, even if the button is pressed twice or on two phones.
 */
public class PaymentRepository {

    private static final String ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Context context;

    public PaymentRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Unique transaction ID: "TXN" + date-time + 10 random characters,
     * e.g. TXN20261124101530A7K2Q9MX4T (32^10 ≈ 10^15 combinations per second).
     */
    @NonNull
    public static String newTransactionId() {
        return newTransactionId(System.currentTimeMillis());
    }

    @VisibleForTesting
    @NonNull
    static String newTransactionId(long now) {
        StringBuilder sb = new StringBuilder("TXN");
        sb.append(new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date(now)));
        for (int i = 0; i < 10; i++) sb.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
        return sb.toString();
    }

    /**
     * Makes a simulated payment for a booking that is waiting for payment.
     *
     * @param method          UPI / CARD / CASH
     * @param simulateSuccess demo switch: true = the "bank" approves, false = it declines
     *                        (ignored for cash, which is always recorded as PENDING)
     * @param callback        gets the saved payment – check {@link Payment#isFailed()}
     */
    public void pay(@NonNull Booking booking, @NonNull String method, boolean simulateSuccess,
                    @NonNull DataCallback<Payment> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return;
        }
        if (!NetworkUtils.isOnline(context)) {
            callback.onError(context.getString(R.string.err_no_internet));
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError(context.getString(R.string.err_session_expired));
            return;
        }
        if (booking.getPaymentId() != null || !booking.isPending()) {
            callback.onError(context.getString(R.string.pay_err_already_paid));
            return;
        }
        if (!booking.isHoldActive(System.currentTimeMillis())) {
            callback.onError(context.getString(R.string.booking_err_hold_expired));
            return;
        }

        boolean cash = Constants.PAYMENT_CASH.equals(method);
        String status = cash ? Constants.PAYMENT_PENDING
                : simulateSuccess ? Constants.PAYMENT_SUCCESS : Constants.PAYMENT_FAILED;

        Payment payment = new Payment();
        payment.setTransactionId(newTransactionId());
        payment.setBookingId(booking.getId());
        payment.setBookingCode(booking.getBookingCode());
        payment.setUserId(user.getUid());
        payment.setLocationName(booking.getLocationName());
        payment.setSlotNumber(booking.getSlotNumber());
        payment.setDurationMinutes(booking.getDurationMinutes());
        payment.setAmount(booking.getAmount());
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setSimulated(true);
        payment.setCreatedAt(System.currentTimeMillis());   // shown locally; server time is saved
        if (payment.isFailed()) {
            payment.setFailureReason(context.getString(R.string.pay_fail_reason_demo));
        }

        String txn = payment.getTransactionId();
        Map<String, Object> record = new HashMap<>();
        record.put("transactionId", txn);
        record.put("bookingId", payment.getBookingId());
        record.put("bookingCode", payment.getBookingCode());
        record.put("userId", payment.getUserId());
        record.put("locationName", payment.getLocationName());
        record.put("slotNumber", payment.getSlotNumber());
        record.put("durationMinutes", payment.getDurationMinutes());
        record.put("amount", payment.getAmount());
        record.put("method", method);
        record.put("status", status);
        record.put("simulated", true);
        record.put("createdAt", ServerValue.TIMESTAMP);
        if (payment.getFailureReason() != null) record.put("failureReason", payment.getFailureReason());

        Map<String, Object> u = new HashMap<>();
        u.put(Constants.NODE_PAYMENTS + "/" + txn, record);
        if (!payment.isFailed()) {
            // Link payment ↔ booking and confirm it, in the same atomic update
            String b = Constants.NODE_BOOKINGS + "/" + booking.getId();
            u.put(b + "/status", Constants.BOOKING_CONFIRMED);
            u.put(b + "/confirmedAt", ServerValue.TIMESTAMP);
            u.put(b + "/paymentId", txn);
            u.put(b + "/paymentMethod", method);
            u.put(b + "/ticketToken", TicketCodec.newToken());   // Step 13: secret part of the QR ticket
            for (String path : BookingRepository.blockPaths(booking)) {
                u.put(path + "/status", Constants.SCHEDULE_BOOKED);
            }
        }

        db().updateChildren(u)
                .addOnSuccessListener(x -> callback.onSuccess(payment))
                .addOnFailureListener(e -> callback.onError(isPermission(e)
                        ? context.getString(R.string.pay_err_rejected)
                        : FirebaseErrorMapper.fromDatabase(context, e)));
    }

    /** All my payments (newest first), updated live – for Payment history. */
    @NonNull
    public Subscription observeMyPayments(@NonNull DataCallback<List<Payment>> callback) {
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
                db().child(Constants.NODE_PAYMENTS).orderByChild("userId").equalTo(user.getUid()),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Payment> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Payment p = child.getValue(Payment.class);
                            if (p != null) {
                                if (p.getTransactionId() == null) p.setTransactionId(child.getKey());
                                list.add(p);
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

    /** One payment, updated live (e.g. it becomes REFUNDED after a cancellation). */
    @NonNull
    public Subscription observePayment(@NonNull String transactionId, @NonNull DataCallback<Payment> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(db().child(Constants.NODE_PAYMENTS).child(transactionId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Payment p = snapshot.getValue(Payment.class);
                if (p != null && p.getTransactionId() == null) p.setTransactionId(snapshot.getKey());
                callback.onSuccess(p);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
            }
        });
    }

    private static boolean isPermission(@NonNull Exception e) {
        return e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("permission");
    }

    private DatabaseReference db() {
        return FirebaseDatabase.getInstance().getReference();
    }
}

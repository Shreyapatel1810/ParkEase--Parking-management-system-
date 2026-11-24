package com.parkease.app.models;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.parkease.app.R;
import com.parkease.app.utils.Constants;

/**
 * STEP 12 – One SIMULATED payment attempt, stored at  payments/{transactionId}.
 *
 * No real card numbers, CVV, UPI PINs or bank details are ever collected or stored –
 * {@link #simulated} is always true and the database rules reject anything else.
 *
 * Status:
 *  PENDING  – Cash at parking: the booking is confirmed, pay at the gate
 *  SUCCESS  – UPI / Card demo payment succeeded → booking CONFIRMED
 *  FAILED   – demo payment failed (booking stays "Payment pending", try again),
 *             or a cash booking was cancelled / not used
 *  REFUNDED – a successful payment whose booking was cancelled
 */
@IgnoreExtraProperties
public class Payment {

    private String transactionId;   // e.g. "TXN20261124101530A7K2Q9MX4T" (also the database key)
    private String bookingId;
    private String bookingCode;
    private String userId;
    private String locationName;
    private String slotNumber;
    private int durationMinutes;
    private double amount;
    private String method;          // UPI / CARD / CASH
    private String status;          // PENDING / SUCCESS / FAILED / REFUNDED
    private boolean simulated = true;
    private String failureReason;
    private double refundAmount;
    private long refundedAt;
    private long createdAt;

    /** Required by Firebase. */
    public Payment() { }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getSlotNumber() { return slotNumber; }
    public void setSlotNumber(String slotNumber) { this.slotNumber = slotNumber; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }

    public long getRefundedAt() { return refundedAt; }
    public void setRefundedAt(long refundedAt) { this.refundedAt = refundedAt; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    // ------------------------------------------------------------------ helpers (not saved)

    @Exclude
    public boolean isSuccessful() {
        return Constants.PAYMENT_SUCCESS.equals(status);
    }

    @Exclude
    public boolean isFailed() {
        return Constants.PAYMENT_FAILED.equals(status);
    }

    @Exclude
    public boolean isPending() {
        return Constants.PAYMENT_PENDING.equals(status);
    }

    @Exclude
    public boolean isRefunded() {
        return Constants.PAYMENT_REFUNDED.equals(status);
    }

    /** "UPI (demo)", "Card (demo)", "Cash at parking". */
    @Exclude
    @StringRes
    public static int methodLabel(String method) {
        if (Constants.PAYMENT_UPI.equals(method)) return R.string.pay_method_upi;
        if (Constants.PAYMENT_CARD.equals(method)) return R.string.pay_method_card;
        return R.string.pay_method_cash;
    }

    @Exclude
    @DrawableRes
    public static int methodIcon(String method) {
        if (Constants.PAYMENT_UPI.equals(method)) return R.drawable.ic_upi;
        if (Constants.PAYMENT_CARD.equals(method)) return R.drawable.ic_credit_card;
        return R.drawable.ic_rupee;
    }

    @Exclude
    @StringRes
    public static int statusLabel(String status) {
        if (Constants.PAYMENT_SUCCESS.equals(status)) return R.string.pay_status_success;
        if (Constants.PAYMENT_FAILED.equals(status)) return R.string.pay_status_failed;
        if (Constants.PAYMENT_REFUNDED.equals(status)) return R.string.pay_status_refunded;
        return R.string.pay_status_pending;
    }
}

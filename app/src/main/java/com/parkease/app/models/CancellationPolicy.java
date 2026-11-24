package com.parkease.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * The cancellation rules, stored in Firebase at  appConfig/cancellation  so the admin can
 * change them without updating the app:
 * <pre>
 * appConfig/cancellation: { fullRefundBeforeMinutes: 60, partialRefundPercent: 50 }
 * </pre>
 * - Cancel at least {@code fullRefundBeforeMinutes} before the start → 100% refund
 * - Cancel later (but before the start)                              → partialRefundPercent %
 * - After the start time                                             → cannot cancel
 * If the config is missing, the defaults below are used.
 */
@IgnoreExtraProperties
public class CancellationPolicy {

    public static final int DEFAULT_FULL_REFUND_BEFORE_MINUTES = 60;
    public static final int DEFAULT_PARTIAL_REFUND_PERCENT = 50;

    private int fullRefundBeforeMinutes = DEFAULT_FULL_REFUND_BEFORE_MINUTES;
    private int partialRefundPercent = DEFAULT_PARTIAL_REFUND_PERCENT;

    /** Required by Firebase. */
    public CancellationPolicy() { }

    public CancellationPolicy(int fullRefundBeforeMinutes, int partialRefundPercent) {
        this.fullRefundBeforeMinutes = fullRefundBeforeMinutes;
        this.partialRefundPercent = partialRefundPercent;
    }

    public int getFullRefundBeforeMinutes() { return fullRefundBeforeMinutes; }
    public void setFullRefundBeforeMinutes(int v) { fullRefundBeforeMinutes = v; }

    public int getPartialRefundPercent() { return partialRefundPercent; }
    public void setPartialRefundPercent(int v) { partialRefundPercent = v; }

    /** Can this booking still be cancelled by the customer? (confirmed and not started) */
    public boolean canCancel(Booking booking, long now) {
        return booking.isConfirmed() && now < booking.getStartTime();
    }

    /** Refund for cancelling now, rounded to whole rupees; 0 if cancelling isn't allowed. */
    public double refundFor(Booking booking, long now) {
        if (!canCancel(booking, now)) return 0;
        long minutesBefore = (booking.getStartTime() - now) / 60_000L;
        if (minutesBefore >= fullRefundBeforeMinutes) return booking.getAmount();
        int pct = Math.max(0, Math.min(100, partialRefundPercent));
        return Math.floor(booking.getAmount() * pct / 100.0);
    }

    /** True when cancelling now gives the full amount back. */
    public boolean isFullRefund(Booking booking, long now) {
        return canCancel(booking, now)
                && (booking.getStartTime() - now) / 60_000L >= fullRefundBeforeMinutes;
    }
}

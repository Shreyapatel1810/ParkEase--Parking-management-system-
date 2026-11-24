package com.parkease.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;

/**
 * STEP 13 – Decides what the gate staff may do with a scanned ticket.
 *
 * It only looks at data loaded from Firebase (the booking and its payment) – never at
 * anything written inside the QR except the secret token, which must match.
 *
 * Checks, in order:
 *  1. Booking exists and the ticket token matches          → else INVALID_TICKET
 *  2. Booking status: cancelled / expired / completed / unpaid hold
 *  3. Payment: linked, belongs to this booking, successful or cash-due
 *  4. Time: entry allowed from 30 min before the start until the end time
 *  5. CONFIRMED → ready for ENTRY,  ACTIVE (parked) → ready for EXIT
 */
public final class TicketVerifier {

    public enum Verdict {
        READY_FOR_ENTRY,
        READY_FOR_EXIT,
        INVALID_TICKET,      // not found, or the token doesn't match (fake / old QR)
        NOT_PAID,            // still waiting for payment, or no payment record
        PAYMENT_PROBLEM,     // payment failed or was refunded
        CANCELLED,
        EXPIRED,
        ALREADY_COMPLETED,   // vehicle already left – exit was recorded
        TOO_EARLY,           // more than 30 minutes before the start
        ENDED                // booking time is over and the vehicle never entered
    }

    public static final class Result {
        @NonNull public final Verdict verdict;
        /** Cash at parking, not collected yet – collect when recording the exit. */
        public final boolean cashDue;
        /** Minutes parked after the booking ended (exit only). */
        public final long overstayMinutes;
        /** Minutes until entry opens (TOO_EARLY only). */
        public final long minutesUntilEntry;

        Result(@NonNull Verdict verdict, boolean cashDue, long overstayMinutes, long minutesUntilEntry) {
            this.verdict = verdict;
            this.cashDue = cashDue;
            this.overstayMinutes = overstayMinutes;
            this.minutesUntilEntry = minutesUntilEntry;
        }

        public boolean canEnter() { return verdict == Verdict.READY_FOR_ENTRY; }

        public boolean canExit() { return verdict == Verdict.READY_FOR_EXIT; }
    }

    private TicketVerifier() { }

    /**
     * @param scannedToken the token read from the QR, or null when staff looked the booking
     *                     up by its code by hand (the screen then warns to check the plate)
     */
    @NonNull
    public static Result verify(@Nullable Booking booking, @Nullable Payment payment,
                                @Nullable String scannedToken, boolean manualLookup, long now) {
        if (booking == null) return of(Verdict.INVALID_TICKET);
        if (!manualLookup && !TicketCodec.tokensMatch(scannedToken, booking.getTicketToken())) {
            return of(Verdict.INVALID_TICKET);
        }

        String status = booking.getStatus() == null ? "" : booking.getStatus();
        switch (status) {
            case Constants.BOOKING_CANCELLED: return of(Verdict.CANCELLED);
            case Constants.BOOKING_EXPIRED: return of(Verdict.EXPIRED);
            case Constants.BOOKING_COMPLETED: return of(Verdict.ALREADY_COMPLETED);
            case Constants.BOOKING_PENDING_PAYMENT: return of(Verdict.NOT_PAID);
            case Constants.BOOKING_CONFIRMED:
            case Constants.BOOKING_ACTIVE:
                break;
            default: return of(Verdict.INVALID_TICKET);
        }

        // Payment must exist, be this booking's, and be paid (or cash still to collect)
        if (payment == null || booking.getPaymentId() == null
                || !booking.getPaymentId().equals(payment.getTransactionId())
                || !booking.getId().equals(payment.getBookingId())) {
            return of(Verdict.NOT_PAID);
        }
        boolean cashDue = payment.isPending() && Constants.PAYMENT_CASH.equals(payment.getMethod());
        if (!payment.isSuccessful() && !cashDue) return of(Verdict.PAYMENT_PROBLEM);

        if (booking.isActive()) {
            long over = Math.max(0, now - booking.getEndTime()) / 60_000L;
            return new Result(Verdict.READY_FOR_EXIT, cashDue, over, 0);
        }

        // CONFIRMED → can the vehicle come in now?
        long opensAt = booking.getStartTime() - Constants.ENTRY_EARLY_MINUTES * 60_000L;
        if (now < opensAt) {
            long mins = (opensAt - now + 59_999L) / 60_000L;
            return new Result(Verdict.TOO_EARLY, cashDue, 0, mins);
        }
        if (now >= booking.getEndTime()) return of(Verdict.ENDED);
        return new Result(Verdict.READY_FOR_ENTRY, cashDue, 0, 0);
    }

    private static Result of(@NonNull Verdict v) {
        return new Result(v, false, 0, 0);
    }
}

package com.parkease.app.ui.customer.booking;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.models.Booking;
import com.parkease.app.models.CancellationPolicy;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.utils.FormatUtils;

/**
 * Shared "Cancel this booking?" flow (used by My Bookings and the booking details screen).
 * Shows the refund from the cancellation policy, then cancels in Firebase.
 */
public final class CancelBookingFlow {

    public interface Listener {
        /** Show / hide a loading indicator. */
        void onBusy(boolean busy);

        /** Show a short message (success with the refund, or the error). */
        void onMessage(@NonNull String message);
    }

    private CancelBookingFlow() { }

    /** True if the customer may cancel this booking now (confirmed and not started). */
    public static boolean canCancel(@NonNull Booking b) {
        return new CancellationPolicy().canCancel(b, System.currentTimeMillis());
    }

    public static void start(@NonNull Context context, @NonNull BookingRepository repository,
                             @NonNull CancellationPolicy policy, @NonNull Booking b,
                             @NonNull Listener listener) {
        long now = System.currentTimeMillis();
        if (!policy.canCancel(b, now)) {
            listener.onMessage(context.getString(R.string.booking_err_cannot_cancel));
            return;
        }
        String refund = FormatUtils.money(policy.refundFor(b, now));
        String msg = b.isCashPayment()
                ? context.getString(R.string.cancel_confirm_cash)
                : policy.isFullRefund(b, now)
                ? context.getString(R.string.cancel_confirm_full, refund)
                : context.getString(R.string.cancel_confirm_partial, policy.getFullRefundBeforeMinutes(),
                        refund, FormatUtils.money(b.getAmount()));

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.cancel_confirm_title)
                .setMessage(msg)
                .setNegativeButton(R.string.cancel_keep, null)
                .setPositiveButton(R.string.summary_cancel, (d, w) -> {
                    listener.onBusy(true);
                    repository.cancelBooking(b, policy, new DataCallback<Double>() {
                        @Override
                        public void onSuccess(Double amount) {
                            listener.onBusy(false);
                            listener.onMessage(context.getString(R.string.cancelled_message,
                                    FormatUtils.money(amount)));
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            listener.onBusy(false);
                            listener.onMessage(message);
                        }
                    });
                })
                .show();
    }
}

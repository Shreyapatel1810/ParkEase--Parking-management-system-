package com.parkease.app.ui.customer.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.adapters.BookingAdapter;
import com.parkease.app.databinding.ActivityBookingSummaryBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.models.CancellationPolicy;
import com.parkease.app.models.Payment;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.ui.customer.payment.PaymentActivity;
import com.parkease.app.ui.customer.payment.PaymentResultActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;

import java.util.Collections;
import java.util.Locale;

/**
 * STEP 11 – Booking summary, confirmation and cancellation.
 *
 * Opened in two ways:
 *  1. Right after the server held a slot (status PENDING_PAYMENT): shows the summary,
 *     price and a 10-minute countdown, with "Proceed to payment" (Step 12) and "Release slot".
 *  2. From My Bookings / Home: shows the booking's details and, while allowed by the
 *     cancellation policy, "Cancel booking".
 *
 * The booking is observed live, so the screen always shows the real status from Firebase.
 */
public class BookingSummaryActivity extends BaseActivity {

    public static Intent intent(@NonNull Context context, @NonNull String bookingId) {
        return new Intent(context, BookingSummaryActivity.class).putExtra(Constants.EXTRA_BOOKING_ID, bookingId);
    }

    private ActivityBookingSummaryBinding binding;
    private BookingRepository repository;
    private Subscription subscription = Subscription.EMPTY;
    private CancellationPolicy policy = new CancellationPolicy();
    private String bookingId;
    @Nullable private Booking booking;
    private boolean busy;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (booking != null) render(booking);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBookingSummaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = new BookingRepository(this);
        bookingId = getIntent().getStringExtra(Constants.EXTRA_BOOKING_ID);
        if (bookingId == null) {
            finish();
            return;
        }

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.actionBar);
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.summary_title);

        setLabel(binding.rowSlot, R.drawable.ic_parkease_logo, R.string.summary_slot);
        setLabel(binding.rowVehicle, R.drawable.ic_car, R.string.summary_vehicle);
        setLabel(binding.rowDate, R.drawable.ic_bookings, R.string.summary_date);
        setLabel(binding.rowTime, R.drawable.ic_clock, R.string.summary_time);
        setLabel(binding.rowDuration, R.drawable.ic_trending_up, R.string.summary_duration);
        setLabel(binding.rowPayment, R.drawable.ic_receipt, R.string.summary_payment);

        binding.stateView.showLoading();
        repository.loadCancellationPolicy(new DataCallback<CancellationPolicy>() {
            @Override
            public void onSuccess(CancellationPolicy p) {
                policy = p;
                if (booking != null) render(booking);
            }

            @Override
            public void onError(@NonNull String message) { /* defaults are used */ }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = repository.observeBooking(bookingId, new DataCallback<Booking>() {
            @Override
            public void onSuccess(Booking b) {
                booking = b;
                if (b == null) {
                    binding.stateView.showEmpty(R.drawable.ic_bookings, getString(R.string.summary_not_found),
                            null, getString(R.string.summary_done), v -> finish());
                    return;
                }
                binding.stateView.hide();
                // A hold or no-show that ran out → mark it expired in Firebase
                if (b.shouldExpire(System.currentTimeMillis())) {
                    repository.expireStaleBookings(Collections.singletonList(b));
                }
                render(b);
            }

            @Override
            public void onError(@NonNull String message) {
                binding.stateView.showError(message, v -> recreate());
            }
        });
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        subscription.cancel();
        handler.removeCallbacks(ticker);
        super.onStop();
    }

    // ================================================================== rendering

    private void render(@NonNull Booking b) {
        long now = System.currentTimeMillis();
        binding.tvLocation.setText(b.getLocationName());
        BookingAdapter.bindStatus(binding.tvStatus, b);

        value(binding.rowSlot, getString(R.string.summary_slot_value, safe(b.getSlotNumber()), safe(b.getSlotId())));
        String type = Constants.VEHICLE_BIKE.equals(b.getVehicleType()) ? getString(R.string.vehicle_bike)
                : Constants.VEHICLE_SUV.equals(b.getVehicleType()) ? getString(R.string.vehicle_suv)
                : getString(R.string.vehicle_car);
        value(binding.rowVehicle, safe(b.getVehicleNumber()) + " · " + type);
        binding.rowVehicle.imgIcon.setImageResource(Constants.VEHICLE_BIKE.equals(b.getVehicleType())
                ? R.drawable.ic_bike : R.drawable.ic_car);
        value(binding.rowDate, FormatUtils.day(b.getStartTime()));
        value(binding.rowTime, getString(R.string.summary_time_value,
                FormatUtils.time(b.getStartTime()), FormatUtils.time(b.getEndTime())));
        value(binding.rowDuration, durationText(b.getDurationMinutes()));

        // Price: rate × hours = total
        double hours = b.getDurationMinutes() / 60.0;
        double rate = hours > 0 ? b.getAmount() / hours : b.getAmount();
        binding.tvRateLine.setText(getString(R.string.summary_rate_line,
                FormatUtils.money(rate), durationText(b.getDurationMinutes())));
        binding.tvRateAmount.setText(FormatUtils.money(b.getAmount()));
        binding.tvTotal.setText(FormatUtils.money(b.getAmount()));
        binding.refundGroup.setVisibility(b.isCancelled() ? View.VISIBLE : View.GONE);
        binding.tvRefund.setText(FormatUtils.money(b.getRefundAmount()));

        // Step 12: linked payment (tap for the receipt)
        if (b.getPaymentId() != null) {
            binding.rowPayment.getRoot().setVisibility(View.VISIBLE);
            binding.rowPayment.imgIcon.setImageResource(Payment.methodIcon(b.getPaymentMethod()));
            value(binding.rowPayment, getString(R.string.summary_payment_value,
                    getString(Payment.methodLabel(b.getPaymentMethod())), b.getPaymentId()));
            binding.rowPayment.getRoot().setOnClickListener(v -> startActivity(
                    PaymentResultActivity.intent(this, b.getPaymentId())));
        } else {
            binding.rowPayment.getRoot().setVisibility(View.GONE);
        }

        binding.tvCode.setText(safe(b.getBookingCode()));
        binding.tvBookingId.setText(getString(R.string.summary_booking_id) + ": " + b.getId());
        binding.tvPolicy.setText(getString(R.string.summary_policy,
                policy.getFullRefundBeforeMinutes(), policy.getPartialRefundPercent()));

        // Countdown for a pending hold
        boolean holdActive = b.isHoldActive(now);
        if (b.isPending()) {
            binding.tvHoldTimer.setVisibility(View.VISIBLE);
            if (holdActive) {
                long left = Math.max(0, b.getHoldUntil() - now) / 1000;
                binding.tvHoldTimer.setText(getString(R.string.summary_hold_timer,
                        String.format(Locale.US, "%d:%02d", left / 60, left % 60)));
            } else {
                binding.tvHoldTimer.setText(R.string.summary_hold_expired);
            }
        } else {
            binding.tvHoldTimer.setVisibility(View.GONE);
        }

        renderActions(b, now, holdActive);
    }

    private void renderActions(@NonNull Booking b, long now, boolean holdActive) {
        binding.btnPrimary.setEnabled(!busy);
        binding.btnSecondary.setEnabled(!busy);
        binding.btnSecondary.setVisibility(View.GONE);

        if (b.isPending() && holdActive) {
            binding.btnPrimary.setText(getString(R.string.summary_pay, FormatUtils.money(b.getAmount())));
            binding.btnPrimary.setOnClickListener(v -> startActivity(PaymentActivity.intent(this, b.getId())));
            binding.btnSecondary.setVisibility(View.VISIBLE);
            binding.btnSecondary.setText(R.string.summary_release);
            binding.btnSecondary.setOnClickListener(v -> release());
        } else if (b.isPending()) {                 // hold ran out
            binding.btnPrimary.setText(R.string.summary_choose_again);
            binding.btnPrimary.setOnClickListener(v -> bookAgain(b));
        } else if (policy.canCancel(b, now)) {      // confirmed, not started
            showTicketButton(b);
            binding.btnSecondary.setVisibility(View.VISIBLE);
            binding.btnSecondary.setText(R.string.summary_cancel);
            binding.btnSecondary.setOnClickListener(v -> askCancel());
        } else if (b.isCancelled() || Constants.BOOKING_EXPIRED.equals(b.getStatus())
                || Constants.BOOKING_COMPLETED.equals(b.getStatus())) {
            binding.btnPrimary.setText(R.string.summary_book_again);
            binding.btnPrimary.setOnClickListener(v -> bookAgain(b));
        } else if (b.hasUsableTicket()) {           // started / parked
            showTicketButton(b);
        } else {
            binding.btnPrimary.setText(R.string.summary_done);
            binding.btnPrimary.setOnClickListener(v -> finish());
        }
    }

    /** Step 13: confirmed or parked bookings open their QR ticket. */
    private void showTicketButton(@NonNull Booking b) {
        if (b.hasUsableTicket()) {
            binding.btnPrimary.setText(R.string.ticket_view);
            binding.btnPrimary.setOnClickListener(v -> startActivity(
                    com.parkease.app.ui.customer.ticket.TicketActivity.intent(this, b.getId())));
        } else {
            binding.btnPrimary.setText(R.string.summary_done);
            binding.btnPrimary.setOnClickListener(v -> finish());
        }
    }

    // ================================================================== actions

    private void release() {
        if (booking == null || busy) return;
        setBusy(true, R.string.summary_cancelling);
        repository.releaseHold(booking, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                setBusy(false, 0);
                showMessage(R.string.summary_released);
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                setBusy(false, 0);
                showMessage(message);
            }
        });
    }

    private void askCancel() {
        if (booking == null) return;
        Booking b = booking;
        long now = System.currentTimeMillis();
        String refund = FormatUtils.money(policy.refundFor(b, now));
        String msg = b.isCashPayment()
                ? getString(R.string.cancel_confirm_cash)
                : policy.isFullRefund(b, now)
                ? getString(R.string.cancel_confirm_full, refund)
                : getString(R.string.cancel_confirm_partial, policy.getFullRefundBeforeMinutes(),
                        refund, FormatUtils.money(b.getAmount()));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cancel_confirm_title)
                .setMessage(msg)
                .setNegativeButton(R.string.cancel_keep, null)
                .setPositiveButton(R.string.summary_cancel, (d, w) -> cancel(b))
                .show();
    }

    private void cancel(@NonNull Booking b) {
        setBusy(true, R.string.summary_cancelling);
        repository.cancelBooking(b, policy, new DataCallback<Double>() {
            @Override
            public void onSuccess(Double refund) {
                setBusy(false, 0);
                showMessage(getString(R.string.cancelled_message, FormatUtils.money(refund)));
            }

            @Override
            public void onError(@NonNull String message) {
                setBusy(false, 0);
                showMessage(message);
            }
        });
    }

    private void bookAgain(@NonNull Booking b) {
        if (b.getLocationId() != null) {
            startActivity(new Intent(this, SlotSelectionActivity.class)
                    .putExtra(Constants.EXTRA_LOCATION_ID, b.getLocationId()));
        }
        finish();
    }

    // ================================================================== helpers

    private void setBusy(boolean value, @StringRes int message) {
        busy = value;
        if (value) {
            binding.loading.setMessage(getString(message));
            binding.loading.show();
        } else {
            binding.loading.hide();
        }
        if (booking != null) render(booking);
    }

    private String durationText(long minutes) {
        if (minutes == 30) return getString(R.string.duration_minutes);
        double h = minutes / 60.0;
        String n = h == Math.floor(h) ? String.valueOf((long) h) : String.valueOf(h);
        return getString(R.string.duration_hours, n);
    }

    private static void setLabel(@NonNull ItemProfileRowBinding row, @DrawableRes int icon, @StringRes int label) {
        row.imgIcon.setImageResource(icon);
        row.tvLabel.setText(label);
    }

    private static void value(@NonNull ItemProfileRowBinding row, @NonNull String text) {
        row.tvValue.setText(text);
    }

    @NonNull
    private static String safe(@Nullable String s) {
        return s == null ? "—" : s;
    }
}

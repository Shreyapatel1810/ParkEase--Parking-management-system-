package com.parkease.app.ui.customer.payment;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.parkease.app.R;
import com.parkease.app.databinding.ActivityPaymentBinding;
import com.parkease.app.databinding.ItemPaymentMethodBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.PaymentRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.ui.customer.booking.BookingSummaryActivity;
import com.parkease.app.ui.customer.booking.SlotSelectionActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;

import java.util.Locale;

/**
 * STEP 12 – Payment screen (SIMULATED – college demo, no real money).
 *
 * Shows: booking ID, parking location, slot number, duration, estimated amount,
 * the payment method (UPI demo / Card demo / Cash at parking) and "Confirm payment".
 *
 * Nothing sensitive is collected: there are NO card number, CVV, UPI PIN or bank fields.
 * For UPI / card the user picks the demo result (Approve / Decline) so both the success
 * and the failure screens can be shown in a presentation.
 */
public class PaymentActivity extends BaseActivity {

    public static Intent intent(@NonNull Context context, @NonNull String bookingId) {
        return new Intent(context, PaymentActivity.class).putExtra(Constants.EXTRA_BOOKING_ID, bookingId);
    }

    /** Short "talking to the bank" pause so the demo feels like a real checkout. */
    private static final long PROCESSING_DELAY_MS = 1500;

    private ActivityPaymentBinding binding;
    private PaymentRepository paymentRepository;
    private BookingRepository bookingRepository;
    private Subscription subscription = Subscription.EMPTY;
    private String bookingId;
    @Nullable private Booking booking;
    private String method = Constants.PAYMENT_UPI;
    /** Stops a second tap from starting a second payment while one is running. */
    private boolean busy;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (booking != null && !busy) render(booking);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        paymentRepository = new PaymentRepository(this);
        bookingRepository = new BookingRepository(this);
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
        binding.header.tvHeaderTitle.setText(R.string.pay_title);
        binding.header.tvHeaderSubtitle.setText(R.string.pay_subtitle);

        setLabel(binding.rowBookingId, R.drawable.ic_bookings, R.string.pay_booking_id);
        setLabel(binding.rowLocation, R.drawable.ic_location, R.string.pay_location);
        setLabel(binding.rowSlot, R.drawable.ic_parkease_logo, R.string.pay_slot);
        setLabel(binding.rowDuration, R.drawable.ic_clock, R.string.pay_duration);

        setupMethod(binding.methodUpi, Constants.PAYMENT_UPI, R.string.pay_method_upi_sub);
        setupMethod(binding.methodCard, Constants.PAYMENT_CARD, R.string.pay_method_card_sub);
        setupMethod(binding.methodCash, Constants.PAYMENT_CASH, R.string.pay_method_cash_sub);
        selectMethod(Constants.PAYMENT_UPI);

        binding.btnConfirm.setOnClickListener(v -> confirmPayment());
        binding.stateView.showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = bookingRepository.observeBooking(bookingId, new DataCallback<Booking>() {
            @Override
            public void onSuccess(Booking b) {
                booking = b;
                if (b == null) {
                    binding.stateView.showEmpty(R.drawable.ic_bookings, getString(R.string.summary_not_found),
                            null, getString(R.string.pay_done), v -> finish());
                    return;
                }
                if (!b.isPending() && !busy) {
                    // Already paid / cancelled (e.g. on another phone) → show the booking instead
                    startActivity(BookingSummaryActivity.intent(PaymentActivity.this, b.getId()));
                    finish();
                    return;
                }
                binding.stateView.hide();
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ================================================================== screen

    private void render(@NonNull Booking b) {
        value(binding.rowBookingId, b.getBookingCode() + "\n" + b.getId());
        value(binding.rowLocation, b.getLocationName() == null ? "—" : b.getLocationName());
        value(binding.rowSlot, b.getSlotNumber() == null ? "—" : b.getSlotNumber());
        value(binding.rowDuration, FormatUtils.duration(this, b.getDurationMinutes()) + " · "
                + FormatUtils.timeRange(b.getStartTime(), b.getEndTime()));
        binding.tvAmount.setText(FormatUtils.money(b.getAmount()));

        long now = System.currentTimeMillis();
        boolean holdActive = b.isHoldActive(now);
        if (holdActive) {
            long left = Math.max(0, b.getHoldUntil() - now) / 1000;
            binding.tvHoldTimer.setText(getString(R.string.pay_hold_timer,
                    String.format(Locale.US, "%d:%02d", left / 60, left % 60)));
        } else {
            binding.tvHoldTimer.setText(R.string.summary_hold_expired);
        }

        boolean cash = Constants.PAYMENT_CASH.equals(method);
        binding.outcomeGroup.setVisibility(cash ? View.GONE : View.VISIBLE);
        if (!holdActive) {
            binding.btnConfirm.setText(R.string.summary_choose_again);
            binding.btnConfirm.setOnClickListener(v -> {
                startActivity(new Intent(this, SlotSelectionActivity.class)
                        .putExtra(Constants.EXTRA_LOCATION_ID, b.getLocationId()));
                finish();
            });
        } else {
            binding.btnConfirm.setText(getString(cash ? R.string.pay_confirm_cash : R.string.pay_confirm,
                    FormatUtils.money(b.getAmount())));
            binding.btnConfirm.setOnClickListener(v -> confirmPayment());
        }
        binding.btnConfirm.setEnabled(!busy);
    }

    private void setupMethod(@NonNull ItemPaymentMethodBinding m, @NonNull String value, @StringRes int sub) {
        m.imgIcon.setImageResource(Payment.methodIcon(value));
        m.tvTitle.setText(Payment.methodLabel(value));
        m.tvSubtitle.setText(sub);
        m.card.setOnClickListener(v -> selectMethod(value));
    }

    private void selectMethod(@NonNull String value) {
        method = value;
        mark(binding.methodUpi, Constants.PAYMENT_UPI.equals(value));
        mark(binding.methodCard, Constants.PAYMENT_CARD.equals(value));
        mark(binding.methodCash, Constants.PAYMENT_CASH.equals(value));
        if (booking != null) render(booking);
    }

    private void mark(@NonNull ItemPaymentMethodBinding m, boolean selected) {
        m.card.setChecked(selected);
        m.radio.setChecked(selected);
        m.card.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this,
                selected ? R.color.brand_secondary : R.color.stroke)));
        float dp = getResources().getDisplayMetrics().density;
        m.card.setStrokeWidth(Math.round(dp * (selected ? 2 : 1)));
    }

    // ================================================================== pay

    private void confirmPayment() {
        Booking b = booking;
        if (b == null || busy) return;
        boolean approve = binding.toggleOutcome.getCheckedButtonId() == R.id.btnApprove;
        busy = true;
        binding.btnConfirm.setEnabled(false);
        binding.loading.show();

        handler.postDelayed(() -> paymentRepository.pay(b, method, approve, new DataCallback<Payment>() {
            @Override
            public void onSuccess(Payment payment) {
                binding.loading.hide();
                startActivity(PaymentResultActivity.intent(PaymentActivity.this, payment.getTransactionId()));
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                busy = false;
                binding.loading.hide();
                if (booking != null) render(booking);
                showMessage(message);
            }
        }), PROCESSING_DELAY_MS);
    }

    // ================================================================== helpers

    private static void setLabel(@NonNull ItemProfileRowBinding row, @DrawableRes int icon, @StringRes int label) {
        row.imgIcon.setImageResource(icon);
        row.tvLabel.setText(label);
    }

    private static void value(@NonNull ItemProfileRowBinding row, @NonNull String text) {
        row.tvValue.setText(text);
    }
}

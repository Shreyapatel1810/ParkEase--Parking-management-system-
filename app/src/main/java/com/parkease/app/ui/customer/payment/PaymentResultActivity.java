package com.parkease.app.ui.customer.payment;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.parkease.app.R;
import com.parkease.app.adapters.PaymentAdapter;
import com.parkease.app.databinding.ActivityPaymentResultBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.Payment;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.PaymentRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.ui.customer.booking.BookingSummaryActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;

/**
 * STEP 12 – Payment success / failure screen, also used as the receipt when a payment is
 * opened from Payment history. Reads the saved record from Firebase, so it shows exactly
 * what the server stored (live: it changes to "Refunded" after a cancellation).
 */
public class PaymentResultActivity extends BaseActivity {

    public static Intent intent(@NonNull Context context, @NonNull String transactionId) {
        return new Intent(context, PaymentResultActivity.class)
                .putExtra(Constants.EXTRA_TRANSACTION_ID, transactionId);
    }

    private ActivityPaymentResultBinding binding;
    private PaymentRepository repository;
    private Subscription subscription = Subscription.EMPTY;
    private String transactionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = new PaymentRepository(this);
        transactionId = getIntent().getStringExtra(Constants.EXTRA_TRANSACTION_ID);
        if (transactionId == null) {
            finish();
            return;
        }
        applySystemBarsPadding(binding.getRoot());

        setLabel(binding.rowTxn, R.drawable.ic_receipt, R.string.pay_txn_id);
        setLabel(binding.rowStatus, R.drawable.ic_check_circle, R.string.pay_status);
        setLabel(binding.rowMethod, R.drawable.ic_rupee, R.string.pay_method_title);
        setLabel(binding.rowBooking, R.drawable.ic_bookings, R.string.pay_booking_id);
        setLabel(binding.rowDate, R.drawable.ic_clock, R.string.pay_date);
        setLabel(binding.rowReason, R.drawable.ic_error, R.string.pay_reason);
        binding.stateView.showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = repository.observePayment(transactionId, new DataCallback<Payment>() {
            @Override
            public void onSuccess(Payment p) {
                if (p == null) {
                    binding.stateView.showError(getString(R.string.err_generic), v -> recreate());
                    return;
                }
                binding.stateView.hide();
                render(p);
            }

            @Override
            public void onError(@NonNull String message) {
                binding.stateView.showError(message, v -> recreate());
            }
        });
    }

    @Override
    protected void onStop() {
        subscription.cancel();
        super.onStop();
    }

    private void render(@NonNull Payment p) {
        String amount = FormatUtils.money(p.getAmount());
        binding.tvAmount.setText(amount);

        if (p.isSuccessful()) {
            header(R.drawable.ic_check_circle, R.color.slot_available, R.color.slot_available_bg,
                    getString(R.string.pay_success_title), getString(R.string.pay_success_msg));
        } else if (p.isPending()) {
            header(R.drawable.ic_check_circle, R.color.slot_available, R.color.slot_available_bg,
                    getString(R.string.pay_cash_title), getString(R.string.pay_cash_msg, amount));
        } else if (p.isRefunded()) {
            header(R.drawable.ic_refresh, R.color.brand_on_secondary_container, R.color.brand_secondary_container,
                    getString(R.string.pay_status_refunded),
                    getString(R.string.pay_history_refund, FormatUtils.money(p.getRefundAmount())));
        } else {
            header(R.drawable.ic_error, R.color.slot_occupied, R.color.slot_occupied_bg,
                    getString(R.string.pay_failed_title), getString(R.string.pay_failed_msg));
        }

        value(binding.rowTxn, p.getTransactionId());
        PaymentAdapter.bindStatus(binding.rowStatus.tvValue, p.getStatus());   // coloured status text
        binding.rowMethod.imgIcon.setImageResource(Payment.methodIcon(p.getMethod()));
        value(binding.rowMethod, getString(Payment.methodLabel(p.getMethod())));
        value(binding.rowBooking, (p.getBookingCode() == null ? "" : p.getBookingCode() + " · ")
                + (p.getLocationName() == null ? "" : p.getLocationName())
                + (p.getSlotNumber() == null ? "" : " · " + p.getSlotNumber()));
        value(binding.rowDate, FormatUtils.dateTime(p.getCreatedAt()));
        binding.rowReason.getRoot().setVisibility(p.getFailureReason() == null ? View.GONE : View.VISIBLE);
        if (p.getFailureReason() != null) value(binding.rowReason, p.getFailureReason());

        // Buttons
        if (p.isFailed() && p.getFailureReason() != null
                && p.getFailureReason().equals(getString(R.string.pay_fail_reason_demo))) {
            binding.btnPrimary.setText(R.string.pay_try_again);
            binding.btnPrimary.setOnClickListener(v -> {
                startActivity(PaymentActivity.intent(this, p.getBookingId()));
                finish();
            });
            binding.btnSecondary.setText(R.string.pay_view_booking);
            binding.btnSecondary.setOnClickListener(v -> openBooking(p));
        } else if ((p.isSuccessful() || p.isPending()) && p.getBookingId() != null) {
            // Step 13: booking confirmed → its QR ticket is ready
            binding.btnPrimary.setText(R.string.ticket_view);
            binding.btnPrimary.setOnClickListener(v -> {
                startActivity(com.parkease.app.ui.customer.ticket.TicketActivity.intent(this, p.getBookingId()));
                finish();
            });
            binding.btnSecondary.setText(R.string.pay_view_booking);
            binding.btnSecondary.setOnClickListener(v -> openBooking(p));
        } else {
            binding.btnPrimary.setText(R.string.pay_view_booking);
            binding.btnPrimary.setOnClickListener(v -> openBooking(p));
            binding.btnSecondary.setText(R.string.pay_done);
            binding.btnSecondary.setOnClickListener(v -> finish());
        }
    }

    private void openBooking(@NonNull Payment p) {
        if (p.getBookingId() != null) {
            startActivity(BookingSummaryActivity.intent(this, p.getBookingId()));
        }
        finish();
    }

    private void header(@DrawableRes int icon, @ColorRes int fg, @ColorRes int bg,
                        @NonNull String title, @NonNull String message) {
        binding.imgResult.setImageResource(icon);
        binding.imgResult.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, fg)));
        binding.iconCircle.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, bg)));
        binding.tvTitle.setText(title);
        binding.tvMessage.setText(message);
    }

    private static void setLabel(@NonNull ItemProfileRowBinding row, @DrawableRes int icon, @StringRes int label) {
        row.imgIcon.setImageResource(icon);
        row.tvLabel.setText(label);
    }

    private static void value(@NonNull ItemProfileRowBinding row, @NonNull String text) {
        row.tvValue.setText(text);
    }
}

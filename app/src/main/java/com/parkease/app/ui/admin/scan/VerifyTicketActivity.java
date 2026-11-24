package com.parkease.app.ui.admin.scan;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.adapters.BookingAdapter;
import com.parkease.app.databinding.ActivityVerifyTicketBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.GateRepository;
import com.parkease.app.repository.PaymentRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;
import com.parkease.app.utils.TicketVerifier;
import com.parkease.app.utils.VehicleNumberUtils;

/**
 * STEP 13 – Staff ticket check.
 *
 * 1. Loads the booking from FIREBASE using the booking ID from the QR (or the typed code).
 * 2. Loads its payment from Firebase.
 * 3. TicketVerifier checks: token matches, payment, cancellation, validity time.
 * 4. Shows the vehicle and slot details and the allowed action: Record entry / Record exit.
 *
 * Everything is observed live, so if another gate records the entry a moment earlier, this
 * screen updates at once. The database rules also reject a second entry / exit.
 */
public class VerifyTicketActivity extends BaseActivity {

    public static Intent forScan(@NonNull Context c, @NonNull String bookingId, @NonNull String token) {
        return new Intent(c, VerifyTicketActivity.class)
                .putExtra(Constants.EXTRA_BOOKING_ID, bookingId)
                .putExtra(Constants.EXTRA_TICKET_TOKEN, token);
    }

    public static Intent forCode(@NonNull Context c, @NonNull String bookingCode) {
        return new Intent(c, VerifyTicketActivity.class).putExtra(Constants.EXTRA_BOOKING_CODE, bookingCode);
    }

    private ActivityVerifyTicketBinding binding;
    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private GateRepository gateRepository;
    private Subscription bookingSub = Subscription.EMPTY;
    private Subscription paymentSub = Subscription.EMPTY;

    private String bookingId;
    @Nullable private String scannedToken;
    private boolean manualLookup;
    @Nullable private Booking booking;
    @Nullable private Payment payment;
    @Nullable private String paymentIdObserved;
    private boolean bookingLoaded;
    private boolean busy;
    /** True while the booking's payment is being loaded. */
    private boolean paymentLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVerifyTicketBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        bookingRepository = new BookingRepository(this);
        paymentRepository = new PaymentRepository(this);
        gateRepository = new GateRepository(this);

        bookingId = getIntent().getStringExtra(Constants.EXTRA_BOOKING_ID);
        scannedToken = getIntent().getStringExtra(Constants.EXTRA_TICKET_TOKEN);
        String code = getIntent().getStringExtra(Constants.EXTRA_BOOKING_CODE);
        manualLookup = bookingId == null && code != null;

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.actionBar);
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.gate_verify_title);
        binding.header.tvHeaderSubtitle.setText(R.string.gate_verify_subtitle);

        setLabel(binding.rowCustomer, R.drawable.ic_profile, R.string.gate_customer);
        setLabel(binding.rowSlot, R.drawable.ic_parkease_logo, R.string.gate_slot);
        setLabel(binding.rowTime, R.drawable.ic_clock, R.string.gate_booking_time);
        setLabel(binding.rowPayment, R.drawable.ic_rupee, R.string.gate_payment);
        setLabel(binding.rowGate, R.drawable.ic_qr_scan, R.string.gate_entry_exit);
        binding.tvManualWarning.setVisibility(manualLookup ? View.VISIBLE : View.GONE);
        binding.btnScanNext.setOnClickListener(v -> finish());   // back to the scanner
        binding.stateView.showLoading();

        if (manualLookup) {
            gateRepository.findByCode(code, new DataCallback<Booking>() {
                @Override
                public void onSuccess(Booking b) {
                    bookingId = b.getId();
                    subscribe();
                }

                @Override
                public void onError(@NonNull String message) {
                    binding.stateView.showEmpty(R.drawable.ic_error, getString(R.string.verdict_invalid_title),
                            message, getString(R.string.gate_scan_next), v -> finish());
                }
            });
        } else if (bookingId == null) {
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (bookingId != null) subscribe();
    }

    @Override
    protected void onStop() {
        bookingSub.cancel();
        paymentSub.cancel();
        paymentIdObserved = null;
        paymentLoading = false;
        super.onStop();
    }

    private void subscribe() {
        bookingSub.cancel();
        bookingSub = bookingRepository.observeBooking(bookingId, new DataCallback<Booking>() {
            @Override
            public void onSuccess(Booking b) {
                booking = b;
                bookingLoaded = true;
                observePayment(b == null ? null : b.getPaymentId());
                render();
            }

            @Override
            public void onError(@NonNull String message) {
                // Not found / not allowed to read → treat as an invalid ticket
                booking = null;
                bookingLoaded = true;
                render();
            }
        });
    }

    private void observePayment(@Nullable String paymentId) {
        if (TextUtils.equals(paymentId, paymentIdObserved)) return;
        paymentSub.cancel();
        payment = null;
        paymentIdObserved = paymentId;
        paymentLoading = paymentId != null;
        if (paymentId == null) return;
        paymentSub = paymentRepository.observePayment(paymentId, new DataCallback<Payment>() {
            @Override
            public void onSuccess(Payment p) {
                payment = p;
                paymentLoading = false;
                render();
            }

            @Override
            public void onError(@NonNull String message) {
                payment = null;
                paymentLoading = false;
                render();
            }
        });
    }

    // ================================================================== screen

    private void render() {
        if (!bookingLoaded) return;
        if (paymentLoading) return;     // decide only once the payment is loaded too
        binding.stateView.hide();
        long now = System.currentTimeMillis();
        TicketVerifier.Result r = TicketVerifier.verify(booking, payment, scannedToken, manualLookup, now);
        renderVerdict(r);

        boolean showDetails = booking != null && r.verdict != TicketVerifier.Verdict.INVALID_TICKET;
        binding.detailsCard.setVisibility(showDetails ? View.VISIBLE : View.GONE);
        if (showDetails) renderDetails(booking);

        // Action button
        binding.btnAction.setEnabled(!busy);
        if (r.canEnter()) {
            binding.btnAction.setVisibility(View.VISIBLE);
            binding.btnAction.setText(R.string.gate_record_entry);
            binding.btnAction.setOnClickListener(v -> confirmEntry());
        } else if (r.canExit()) {
            binding.btnAction.setVisibility(View.VISIBLE);
            binding.btnAction.setText(r.cashDue
                    ? getString(R.string.gate_record_exit_cash, FormatUtils.money(booking.getAmount()))
                    : getString(R.string.gate_record_exit));
            binding.btnAction.setOnClickListener(v -> confirmExit(r.cashDue));
        } else {
            binding.btnAction.setVisibility(View.GONE);
        }
    }

    private void renderVerdict(@NonNull TicketVerifier.Result r) {
        String slot = booking == null || booking.getSlotNumber() == null ? "—" : booking.getSlotNumber();
        String amount = booking == null ? "" : FormatUtils.money(booking.getAmount());
        switch (r.verdict) {
            case READY_FOR_ENTRY:
                verdict(true, R.drawable.ic_check_circle, R.string.verdict_entry_title, r.cashDue
                        ? getString(R.string.verdict_entry_cash_msg, amount, slot)
                        : getString(R.string.verdict_entry_msg, slot));
                break;
            case READY_FOR_EXIT:
                String msg = getString(R.string.verdict_exit_msg);
                if (r.overstayMinutes > 0) msg += "\n" + getString(R.string.verdict_exit_overstay, (int) r.overstayMinutes);
                verdict(true, R.drawable.ic_check_circle, R.string.verdict_exit_title, msg);
                break;
            case TOO_EARLY:
                warn(R.string.verdict_early_title, getString(R.string.verdict_early_msg, (int) r.minutesUntilEntry));
                break;
            case NOT_PAID:
                verdict(false, R.drawable.ic_error, R.string.verdict_not_paid_title, getString(R.string.verdict_not_paid_msg));
                break;
            case PAYMENT_PROBLEM:
                verdict(false, R.drawable.ic_error, R.string.verdict_payment_title, getString(R.string.verdict_payment_msg));
                break;
            case CANCELLED:
                verdict(false, R.drawable.ic_error, R.string.verdict_cancelled_title, getString(R.string.verdict_cancelled_msg));
                break;
            case EXPIRED:
                verdict(false, R.drawable.ic_error, R.string.verdict_expired_title, getString(R.string.verdict_expired_msg));
                break;
            case ALREADY_COMPLETED:
                verdict(false, R.drawable.ic_error, R.string.verdict_completed_title, getString(R.string.verdict_completed_msg));
                break;
            case ENDED:
                verdict(false, R.drawable.ic_error, R.string.verdict_ended_title, getString(R.string.verdict_ended_msg));
                break;
            default:
                verdict(false, R.drawable.ic_error, R.string.verdict_invalid_title, getString(R.string.verdict_invalid_msg));
                break;
        }
    }

    private void verdict(boolean ok, @DrawableRes int icon, @StringRes int title, @NonNull String message) {
        colours(ok ? R.color.slot_available : R.color.slot_occupied,
                ok ? R.color.slot_available_bg : R.color.slot_occupied_bg);
        binding.imgVerdict.setImageResource(icon);
        binding.tvVerdictTitle.setText(title);
        binding.tvVerdictMsg.setText(message);
    }

    private void warn(@StringRes int title, @NonNull String message) {
        colours(R.color.slot_reserved, R.color.slot_reserved_bg);
        binding.imgVerdict.setImageResource(R.drawable.ic_clock);
        binding.tvVerdictTitle.setText(title);
        binding.tvVerdictMsg.setText(message);
    }

    private void colours(@ColorRes int fg, @ColorRes int bg) {
        binding.verdictCard.setCardBackgroundColor(ContextCompat.getColor(this, bg));
        binding.imgVerdict.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, fg)));
    }

    private void renderDetails(@NonNull Booking b) {
        binding.tvPlate.setText(VehicleNumberUtils.format(b.getVehicleNumber()));
        BookingAdapter.bindStatus(binding.tvBookingStatus, b);
        @StringRes int type = Constants.VEHICLE_BIKE.equals(b.getVehicleType()) ? R.string.vehicle_bike
                : Constants.VEHICLE_SUV.equals(b.getVehicleType()) ? R.string.vehicle_suv : R.string.vehicle_car;
        binding.tvVehicleType.setText(getString(type) + " · " + b.getBookingCode());

        value(binding.rowCustomer, TextUtils.isEmpty(b.getUserName()) ? "—" : b.getUserName());
        value(binding.rowSlot, getString(R.string.gate_slot_value, safe(b.getSlotNumber()),
                safe(b.getLocationName()), safe(b.getSlotId())));
        value(binding.rowTime, FormatUtils.timeRange(b.getStartTime(), b.getEndTime()));

        if (payment != null) {
            binding.rowPayment.imgIcon.setImageResource(Payment.methodIcon(payment.getMethod()));
            value(binding.rowPayment, getString(R.string.gate_payment_value,
                    getString(Payment.methodLabel(payment.getMethod())),
                    getString(Payment.statusLabel(payment.getStatus())),
                    FormatUtils.money(payment.getAmount())));
        } else {
            value(binding.rowPayment, getString(R.string.gate_payment_none));
        }

        if (b.getExitTime() > 0) {
            value(binding.rowGate, getString(R.string.gate_entered_exited_value,
                    FormatUtils.dateTime(b.getEntryTime()), FormatUtils.dateTime(b.getExitTime())));
        } else if (b.getEntryTime() > 0) {
            value(binding.rowGate, getString(R.string.gate_entered_value, FormatUtils.dateTime(b.getEntryTime())));
        } else {
            value(binding.rowGate, getString(R.string.gate_not_entered));
        }
    }

    // ================================================================== gate actions

    private void confirmEntry() {
        Booking b = booking;
        if (b == null || busy) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.gate_record_entry)
                .setMessage(getString(R.string.gate_confirm_entry,
                        VehicleNumberUtils.format(b.getVehicleNumber()), safe(b.getSlotNumber())))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.gate_record_entry, (d, w) -> {
                    setBusy(true);
                    gateRepository.recordEntry(b, done(getString(R.string.gate_entry_done,
                            VehicleNumberUtils.format(b.getVehicleNumber()))));
                })
                .show();
    }

    private void confirmExit(boolean cashDue) {
        Booking b = booking;
        if (b == null || busy) return;
        String plate = VehicleNumberUtils.format(b.getVehicleNumber());
        String message = cashDue
                ? getString(R.string.gate_confirm_exit_cash, FormatUtils.money(b.getAmount()), plate)
                : getString(R.string.gate_confirm_exit, plate, safe(b.getSlotNumber()));
        Payment p = payment;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.gate_record_exit)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.gate_record_exit, (d, w) -> {
                    setBusy(true);
                    gateRepository.recordExit(b, p, done(getString(R.string.gate_exit_done, plate)));
                })
                .show();
    }

    /** After a successful entry/exit: confirm and go straight back to the scanner. */
    @NonNull
    private DataCallback<Void> done(@NonNull String successMessage) {
        return new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                setBusy(false);
                Toast.makeText(VerifyTicketActivity.this, successMessage, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                setBusy(false);
                new MaterialAlertDialogBuilder(VerifyTicketActivity.this)
                        .setMessage(message)
                        .setPositiveButton(R.string.action_ok, null)
                        .show();
            }
        };
    }

    private void setBusy(boolean value) {
        busy = value;
        if (value) binding.loading.show();
        else binding.loading.hide();
        binding.btnAction.setEnabled(!value);
    }

    // ================================================================== helpers

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

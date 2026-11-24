package com.parkease.app.ui.customer.ticket;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.parkease.app.R;
import com.parkease.app.adapters.BookingAdapter;
import com.parkease.app.database.TicketDao;
import com.parkease.app.databinding.ActivityTicketBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;
import com.parkease.app.utils.SessionManager;
import com.parkease.app.utils.TicketCodec;

import java.util.EnumMap;
import java.util.Map;

/**
 * STEP 13 – The customer's QR parking ticket.
 *
 * The QR contains only "PARKEASE1|bookingId|secretToken" (see TicketCodec) – the details
 * shown below it are for the customer; the gate scanner reads them from Firebase.
 *
 * The ticket is saved in SQLite (TicketDao), so it still opens without internet.
 * Screen brightness is turned up while the ticket is shown, to help the scanner.
 */
public class TicketActivity extends BaseActivity {

    public static Intent intent(@NonNull Context context, @NonNull String bookingId) {
        return new Intent(context, TicketActivity.class).putExtra(Constants.EXTRA_BOOKING_ID, bookingId);
    }

    private ActivityTicketBinding binding;
    private BookingRepository repository;
    private TicketDao ticketDao;
    private Subscription subscription = Subscription.EMPTY;
    private String bookingId;
    @Nullable private String qrContent;      // QR currently drawn (avoid re-drawing every update)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = new BookingRepository(this);
        ticketDao = new TicketDao(this);
        bookingId = getIntent().getStringExtra(Constants.EXTRA_BOOKING_ID);
        if (bookingId == null) {
            finish();
            return;
        }

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.content);
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());
        binding.header.tvHeaderTitle.setText(R.string.ticket_title);
        binding.header.tvHeaderSubtitle.setText(R.string.ticket_subtitle);

        setLabel(binding.rowCustomer, R.drawable.ic_profile, R.string.ticket_customer);
        setLabel(binding.rowVehicle, R.drawable.ic_car, R.string.ticket_vehicle);
        setLabel(binding.rowSlot, R.drawable.ic_parkease_logo, R.string.ticket_slot);
        setLabel(binding.rowDate, R.drawable.ic_bookings, R.string.ticket_date);
        setLabel(binding.rowTime, R.drawable.ic_clock, R.string.ticket_time);
        setLabel(binding.rowGate, R.drawable.ic_qr_scan, R.string.gate_entry_exit);

        // Show the saved copy straight away (works offline); Firebase updates it below
        Booking saved = ticketDao.get(bookingId);
        if (saved != null) render(saved);
        else binding.stateView.showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscription = repository.observeBooking(bookingId, new DataCallback<Booking>() {
            @Override
            public void onSuccess(Booking b) {
                binding.tvOffline.setVisibility(View.GONE);
                if (b == null) {
                    ticketDao.delete(bookingId);
                    binding.stateView.showEmpty(R.drawable.ic_bookings, getString(R.string.summary_not_found),
                            null, getString(R.string.summary_done), v -> finish());
                    return;
                }
                if (b.getTicketToken() == null) {
                    binding.stateView.showEmpty(R.drawable.ic_qr_scan, getString(R.string.ticket_title),
                            getString(R.string.ticket_unavailable), getString(R.string.summary_done), v -> finish());
                    return;
                }
                if (TextUtils.isEmpty(b.getUserName())) {
                    b.setUserName(SessionManager.getCachedName(TicketActivity.this));
                }
                ticketDao.save(b, b.getUserName());
                render(b);
            }

            @Override
            public void onError(@NonNull String message) {
                Booking saved = ticketDao.get(bookingId);
                if (saved != null) {
                    binding.tvOffline.setVisibility(View.VISIBLE);
                    render(saved);
                } else {
                    binding.stateView.showError(message, v -> recreate());
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setBrightness(1f);                  // full brightness helps the gate scanner
    }

    @Override
    protected void onPause() {
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        subscription.cancel();
        super.onStop();
    }

    // ================================================================== screen

    private void render(@NonNull Booking b) {
        binding.stateView.hide();
        binding.tvLocation.setText(b.getLocationName());
        BookingAdapter.bindStatus(binding.tvStatus, b);
        binding.tvCode.setText(b.getBookingCode());
        binding.tvBookingId.setText(getString(R.string.ticket_booking_id) + ": " + b.getId());

        value(binding.rowCustomer, TextUtils.isEmpty(b.getUserName())
                ? SessionManager.getCachedName(this) : b.getUserName());
        boolean bike = Constants.VEHICLE_BIKE.equals(b.getVehicleType());
        binding.rowVehicle.imgIcon.setImageResource(bike ? R.drawable.ic_bike : R.drawable.ic_car);
        value(binding.rowVehicle, b.getVehicleNumber());
        value(binding.rowSlot, b.getSlotNumber());
        value(binding.rowDate, FormatUtils.day(b.getStartTime()));
        value(binding.rowTime, getString(R.string.summary_time_value,
                FormatUtils.time(b.getStartTime()), FormatUtils.time(b.getEndTime())));

        if (b.getEntryTime() > 0) {
            binding.rowGate.getRoot().setVisibility(View.VISIBLE);
            String text = getString(R.string.ticket_entered, FormatUtils.dateTime(b.getEntryTime()));
            if (b.getExitTime() > 0) {
                text += "\n" + getString(R.string.ticket_exited, FormatUtils.dateTime(b.getExitTime()));
            }
            value(binding.rowGate, text);
        } else {
            binding.rowGate.getRoot().setVisibility(View.GONE);
        }

        drawQr(b);
    }

    /** Draws the QR; a ticket that can't be used any more is faded with a red label. */
    private void drawQr(@NonNull Booking b) {
        if (b.getTicketToken() == null) return;
        String content = TicketCodec.encode(b.getId(), b.getTicketToken());
        if (!content.equals(qrContent)) {
            try {
                Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                hints.put(EncodeHintType.MARGIN, 1);
                Bitmap bitmap = new BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 720, 720, hints);
                binding.imgQr.setImageBitmap(bitmap);
                binding.imgQr.setContentDescription(getString(R.string.cd_ticket_qr, b.getBookingCode()));
                qrContent = content;
            } catch (Exception e) {
                showMessage(R.string.err_generic);
                return;
            }
        }
        boolean usable = b.hasUsableTicket();
        binding.imgQr.setAlpha(usable ? 1f : 0.15f);
        binding.tvQrOverlay.setVisibility(usable ? View.GONE : View.VISIBLE);
        if (!usable) {
            binding.tvQrOverlay.setText(Constants.BOOKING_COMPLETED.equals(b.getStatus())
                    ? getString(R.string.ticket_used)
                    : getString(R.string.ticket_not_valid, binding.tvStatus.getText()));
        }
    }

    private void setBrightness(float value) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = value;
        getWindow().setAttributes(lp);
    }

    private static void setLabel(@NonNull ItemProfileRowBinding row, @DrawableRes int icon, @StringRes int label) {
        row.imgIcon.setImageResource(icon);
        row.tvLabel.setText(label);
    }

    private static void value(@NonNull ItemProfileRowBinding row, @Nullable String text) {
        row.tvValue.setText(text == null ? "—" : text);
    }
}

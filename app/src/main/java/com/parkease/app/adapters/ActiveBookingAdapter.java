package com.parkease.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.R;
import com.parkease.app.databinding.ItemActiveBookingBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;
import com.parkease.app.utils.VehicleNumberUtils;

import java.util.Collections;
import java.util.Map;

/**
 * STEP 14 – Bookings happening NOW (Active tab): a bigger card with a live progress bar of
 * the booked time, time left / overstay, and a prominent "Show QR ticket" button for the
 * exit gate. Call {@link #tick()} every few seconds to update the timers.
 */
public class ActiveBookingAdapter extends ListAdapter<Booking, ActiveBookingAdapter.Holder> {

    private final MyBookingAdapter.Listener listener;
    private Map<String, Payment> payments = Collections.emptyMap();

    public ActiveBookingAdapter(@NonNull MyBookingAdapter.Listener listener) {
        super(MyBookingAdapter.DIFF);
        this.listener = listener;
    }

    public void setPayments(@NonNull Map<String, Payment> payments) {
        this.payments = payments;
        notifyItemRangeChanged(0, getItemCount());
    }

    /** Refreshes the timers and progress bars. */
    public void tick() {
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemActiveBookingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Booking b = getItem(position);
        ItemActiveBookingBinding v = holder.b;
        Context c = v.getRoot().getContext();
        long now = System.currentTimeMillis();

        v.tvLocation.setText(b.getLocationName());
        v.tvCode.setText(b.getBookingCode());
        v.tvSlot.setText(b.getSlotNumber() == null ? "—" : b.getSlotNumber());
        BookingAdapter.bindStatus(v.tvStatus, b);

        // Parked, or time started but the vehicle hasn't entered yet
        v.tvState.setText(b.isActive() && b.getEntryTime() > 0
                ? c.getString(R.string.mb_active_parked, FormatUtils.time(b.getEntryTime()))
                : c.getString(R.string.mb_active_waiting));

        long total = Math.max(1, b.getEndTime() - b.getStartTime());
        long elapsed = Math.min(total, Math.max(0, now - b.getStartTime()));
        v.progress.setProgressCompat((int) (elapsed * 1000 / total), true);
        if (now < b.getEndTime()) {
            v.tvRemaining.setText(c.getString(R.string.mb_active_left,
                    duration(c, b.getEndTime() - now), FormatUtils.time(b.getEndTime())));
            v.tvRemaining.setTextColor(c.getColor(R.color.text_secondary));
        } else {
            v.tvRemaining.setText(c.getString(R.string.mb_active_overstay, duration(c, now - b.getEndTime())));
            v.tvRemaining.setTextColor(c.getColor(R.color.error));
        }

        String plate = b.getVehicleNumber() == null ? "—" : VehicleNumberUtils.format(b.getVehicleNumber());
        v.tvVehicleTime.setText(plate + " · " + FormatUtils.timeRange(b.getStartTime(), b.getEndTime()));
        v.tvVehicleTime.setCompoundDrawablesRelativeWithIntrinsicBounds(
                Constants.VEHICLE_BIKE.equals(b.getVehicleType()) ? R.drawable.ic_bike : R.drawable.ic_car, 0, 0, 0);

        Payment payment = b.getPaymentId() == null ? null : payments.get(b.getPaymentId());
        MyBookingAdapter.bindPayment(v.tvPaymentMethod, v.tvPaymentStatus, b, payment);

        v.btnTicket.setVisibility(b.hasUsableTicket() ? View.VISIBLE : View.GONE);
        v.btnReceipt.setVisibility(b.getPaymentId() != null ? View.VISIBLE : View.GONE);
        v.btnTicket.setOnClickListener(x -> listener.onTicket(b));
        v.btnDetails.setOnClickListener(x -> listener.onOpen(b));
        v.btnReceipt.setOnClickListener(x -> listener.onReceipt(b));
        v.card.setOnClickListener(x -> listener.onOpen(b));
    }

    /** "45 min" or "1 h 05 min". */
    @NonNull
    static String duration(@NonNull Context c, long millis) {
        long minutes = Math.max(1, (millis + 59_999L) / 60_000L);
        if (minutes < 60) return c.getString(R.string.mb_minutes, (int) minutes);
        return c.getString(R.string.mb_hours_minutes, (int) (minutes / 60), (int) (minutes % 60));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemActiveBookingBinding b;

        Holder(@NonNull ItemActiveBookingBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }
}

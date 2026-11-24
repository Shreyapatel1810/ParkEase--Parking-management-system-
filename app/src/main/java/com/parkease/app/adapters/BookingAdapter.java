package com.parkease.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.R;
import com.parkease.app.databinding.ItemBookingBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;

import java.util.Objects;

/** Bookings list ("My Bookings"). {@link #bind} is also used for the Home upcoming card. */
public class BookingAdapter extends ListAdapter<Booking, BookingAdapter.Holder> {

    public interface Listener {
        void onBookingClick(@NonNull Booking booking);
    }

    private final Listener listener;

    public BookingAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBookingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Booking booking = getItem(position);
        bind(holder.b, booking);
        holder.b.card.setOnClickListener(v -> listener.onBookingClick(booking));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemBookingBinding b;

        Holder(@NonNull ItemBookingBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }

    /** Fills an item_booking view with a booking (shared with the Home screen). */
    public static void bind(@NonNull ItemBookingBinding b, @NonNull Booking booking) {
        Context c = b.getRoot().getContext();
        b.tvLocation.setText(booking.getLocationName());
        String slot = c.getString(R.string.booking_slot,
                booking.getSlotNumber() == null ? "—" : booking.getSlotNumber());
        b.tvSlot.setText(booking.getVehicleNumber() == null ? slot : slot + " · " + booking.getVehicleNumber());
        b.tvTime.setText(FormatUtils.timeRange(booking.getStartTime(), booking.getEndTime()));
        b.tvAmount.setText(FormatUtils.money(booking.getAmount()));
        b.tvCode.setText(booking.getBookingCode());
        b.imgVehicle.setImageResource(Constants.VEHICLE_BIKE.equals(booking.getVehicleType())
                ? R.drawable.ic_bike : R.drawable.ic_car);

        bindStatus(b.tvStatus, booking);
    }

    /** Coloured status badge: Confirmed / Payment pending / Parked / Completed / Cancelled / Expired. */
    public static void bindStatus(@NonNull android.widget.TextView badge, @NonNull Booking booking) {
        Context c = badge.getContext();
        @StringRes int label;
        @ColorRes int fg;
        @ColorRes int bg;
        String status = booking.getStatus() == null ? "" : booking.getStatus();
        // A hold that ran out is shown as Expired straight away
        if (Constants.BOOKING_PENDING_PAYMENT.equals(status) && !booking.isHoldActive(System.currentTimeMillis())) {
            status = Constants.BOOKING_EXPIRED;
        }
        switch (status) {
            case Constants.BOOKING_ACTIVE:
                label = R.string.booking_status_active; fg = R.color.slot_available; bg = R.color.slot_available_bg;
                break;
            case Constants.BOOKING_COMPLETED:
                label = R.string.booking_status_completed; fg = R.color.slot_maintenance; bg = R.color.slot_maintenance_bg;
                break;
            case Constants.BOOKING_CANCELLED:
                label = R.string.booking_status_cancelled; fg = R.color.slot_occupied; bg = R.color.slot_occupied_bg;
                break;
            case Constants.BOOKING_EXPIRED:
                label = R.string.booking_status_expired; fg = R.color.slot_reserved; bg = R.color.slot_reserved_bg;
                break;
            case Constants.BOOKING_PENDING_PAYMENT:
                label = R.string.booking_status_pending; fg = R.color.slot_reserved; bg = R.color.slot_reserved_bg;
                break;
            default:
                label = R.string.booking_status_confirmed; fg = R.color.brand_on_secondary_container; bg = R.color.brand_secondary_container;
                break;
        }
        badge.setText(label);
        badge.setTextColor(ContextCompat.getColor(c, fg));
        badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, bg)));
    }

    private static final DiffUtil.ItemCallback<Booking> DIFF = new DiffUtil.ItemCallback<Booking>() {
        @Override
        public boolean areItemsTheSame(@NonNull Booking a, @NonNull Booking b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Booking a, @NonNull Booking b) {
            return Objects.equals(a.getStatus(), b.getStatus())
                    && a.getStartTime() == b.getStartTime()
                    && a.getEndTime() == b.getEndTime()
                    && a.getAmount() == b.getAmount()
                    && Objects.equals(a.getSlotNumber(), b.getSlotNumber());
        }
    };
}

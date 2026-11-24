package com.parkease.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.R;
import com.parkease.app.databinding.ItemMyBookingBinding;
import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;
import com.parkease.app.utils.VehicleNumberUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * STEP 14 – Booking cards for the Upcoming and History tabs of My Bookings.
 * Shows booking ID, parking name, slot, vehicle, date, start–end time, payment status and
 * booking status, with the actions that apply: QR ticket, Cancel, Receipt (tap = details).
 * The Active tab uses {@link ActiveBookingAdapter}.
 */
public class MyBookingAdapter extends ListAdapter<Booking, MyBookingAdapter.Holder> {

    /** What the user can do with a booking (shared with ActiveBookingAdapter). */
    public interface Listener {
        void onOpen(@NonNull Booking booking);        // booking details

        void onTicket(@NonNull Booking booking);      // QR ticket

        void onCancel(@NonNull Booking booking);

        void onReceipt(@NonNull Booking booking);     // transaction details

        void onPay(@NonNull Booking booking);         // hold waiting for payment
    }

    private final Listener listener;
    private Map<String, Payment> payments = Collections.emptyMap();

    public MyBookingAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Payments by transaction ID, to show each booking's payment status. */
    public void setPayments(@NonNull Map<String, Payment> payments) {
        this.payments = payments;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMyBookingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Booking b = getItem(position);
        ItemMyBookingBinding v = holder.b;
        Context c = v.getRoot().getContext();
        long now = System.currentTimeMillis();

        v.tvLocation.setText(b.getLocationName());
        v.tvCode.setText(b.getBookingCode());
        v.imgVehicle.setImageResource(Constants.VEHICLE_BIKE.equals(b.getVehicleType()) ? R.drawable.ic_bike : R.drawable.ic_car);
        BookingAdapter.bindStatus(v.tvStatus, b);
        v.tvSlot.setText(b.getSlotNumber() == null ? "—" : b.getSlotNumber());
        v.tvVehicle.setText(b.getVehicleNumber() == null ? "—" : VehicleNumberUtils.format(b.getVehicleNumber()));
        v.tvDate.setText(FormatUtils.day(b.getStartTime()));
        v.tvTime.setText(c.getString(R.string.summary_time_value,
                FormatUtils.time(b.getStartTime()), FormatUtils.time(b.getEndTime())));

        Payment payment = b.getPaymentId() == null ? null : payments.get(b.getPaymentId());
        bindPayment(v.tvPaymentMethod, v.tvPaymentStatus, b, payment);
        v.imgPayment.setImageResource(payment != null ? Payment.methodIcon(payment.getMethod())
                : b.getPaymentMethod() != null ? Payment.methodIcon(b.getPaymentMethod()) : R.drawable.ic_rupee);

        // Actions that apply to THIS booking
        boolean pendingHold = b.isHoldActive(now);
        boolean cancellable = b.isConfirmed() && now < b.getStartTime();
        v.btnTicket.setVisibility(b.hasUsableTicket() ? View.VISIBLE : View.GONE);
        v.btnCancel.setVisibility(cancellable ? View.VISIBLE : View.GONE);
        v.btnReceipt.setVisibility(b.getPaymentId() != null ? View.VISIBLE : View.GONE);
        if (pendingHold) {
            v.btnTicket.setVisibility(View.VISIBLE);
            v.btnTicket.setText(R.string.mb_action_pay);
            v.btnTicket.setIconResource(R.drawable.ic_rupee);
            v.btnTicket.setOnClickListener(x -> listener.onPay(b));
        } else {
            v.btnTicket.setText(R.string.mb_action_ticket);
            v.btnTicket.setIconResource(R.drawable.ic_qr_scan);
            v.btnTicket.setOnClickListener(x -> listener.onTicket(b));
        }
        boolean anyAction = v.btnTicket.getVisibility() == View.VISIBLE
                || v.btnCancel.getVisibility() == View.VISIBLE || v.btnReceipt.getVisibility() == View.VISIBLE;
        v.actions.setVisibility(anyAction ? View.VISIBLE : View.GONE);
        v.body.setPaddingRelative(v.body.getPaddingStart(), v.body.getPaddingTop(), v.body.getPaddingEnd(),
                c.getResources().getDimensionPixelSize(anyAction ? R.dimen.space_xs : R.dimen.card_padding));

        v.btnCancel.setOnClickListener(x -> listener.onCancel(b));
        v.btnReceipt.setOnClickListener(x -> listener.onReceipt(b));
        v.card.setOnClickListener(x -> listener.onOpen(b));
    }

    /**
     * "UPI (demo) · ₹100" + a payment status badge (Pending / Successful / Failed / Refunded).
     * A hold that isn't paid yet shows "Awaiting payment".
     */
    public static void bindPayment(@NonNull TextView method, @NonNull TextView status,
                                   @NonNull Booking b, @Nullable Payment payment) {
        Context c = method.getContext();
        String amount = FormatUtils.money(b.getAmount());
        if (payment != null) {
            method.setText(c.getString(Payment.methodLabel(payment.getMethod())) + " · " + amount);
            status.setVisibility(View.VISIBLE);
            PaymentAdapter.bindStatus(status, payment.getStatus());
        } else if (b.getPaymentMethod() != null) {         // payment not loaded yet
            method.setText(c.getString(Payment.methodLabel(b.getPaymentMethod())) + " · " + amount);
            status.setVisibility(View.GONE);
        } else if (b.isPending()) {
            method.setText(c.getString(R.string.mb_payment) + " · " + amount);
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.mb_payment_awaiting);
            status.setTextColor(ContextCompat.getColor(c, R.color.slot_reserved));
            status.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.slot_reserved_bg)));
        } else {
            method.setText(c.getString(R.string.mb_payment) + " · " + c.getString(R.string.mb_payment_none));
            status.setVisibility(View.GONE);
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemMyBookingBinding b;

        Holder(@NonNull ItemMyBookingBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }

    static final DiffUtil.ItemCallback<Booking> DIFF = new DiffUtil.ItemCallback<Booking>() {
        @Override
        public boolean areItemsTheSame(@NonNull Booking a, @NonNull Booking b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Booking a, @NonNull Booking b) {
            return Objects.equals(a.getStatus(), b.getStatus())
                    && a.getStartTime() == b.getStartTime()
                    && a.getEndTime() == b.getEndTime()
                    && a.getEntryTime() == b.getEntryTime()
                    && a.getHoldUntil() == b.getHoldUntil()
                    && Objects.equals(a.getPaymentId(), b.getPaymentId());
        }
    };
}

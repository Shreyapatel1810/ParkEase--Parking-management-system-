package com.parkease.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.R;
import com.parkease.app.databinding.ItemPaymentBinding;
import com.parkease.app.models.Payment;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;

import java.util.Objects;

/** Payment history list (Step 12). {@link #bindStatus} is shared with the result screen. */
public class PaymentAdapter extends ListAdapter<Payment, PaymentAdapter.Holder> {

    public interface Listener {
        void onPaymentClick(@NonNull Payment payment);
    }

    private final Listener listener;

    public PaymentAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPaymentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Payment p = getItem(position);
        ItemPaymentBinding b = holder.b;
        Context c = b.getRoot().getContext();

        b.imgMethod.setImageResource(Payment.methodIcon(p.getMethod()));
        b.tvPlace.setText(c.getString(R.string.pay_history_item_place,
                p.getLocationName() == null ? "—" : p.getLocationName(),
                p.getSlotNumber() == null ? "—" : p.getSlotNumber()));
        b.tvMethodDate.setText(c.getString(Payment.methodLabel(p.getMethod())) + " · "
                + FormatUtils.dateTime(p.getCreatedAt()));
        b.tvTxn.setText(p.getTransactionId());
        b.tvAmount.setText(FormatUtils.money(p.getAmount()));
        bindStatus(b.tvStatus, p.getStatus());

        if (p.isRefunded()) {
            b.tvExtra.setVisibility(View.VISIBLE);
            b.tvExtra.setText(c.getString(R.string.pay_history_refund, FormatUtils.money(p.getRefundAmount())));
        } else if (p.isFailed() && p.getFailureReason() != null) {
            b.tvExtra.setVisibility(View.VISIBLE);
            b.tvExtra.setText(p.getFailureReason());
        } else {
            b.tvExtra.setVisibility(View.GONE);
        }
        b.card.setOnClickListener(v -> listener.onPaymentClick(p));
    }

    /** Coloured badge: Pending / Successful / Failed / Refunded. */
    public static void bindStatus(@NonNull TextView badge, String status) {
        Context c = badge.getContext();
        @ColorRes int fg;
        @ColorRes int bg;
        if (Constants.PAYMENT_SUCCESS.equals(status)) {
            fg = R.color.slot_available; bg = R.color.slot_available_bg;
        } else if (Constants.PAYMENT_FAILED.equals(status)) {
            fg = R.color.slot_occupied; bg = R.color.slot_occupied_bg;
        } else if (Constants.PAYMENT_REFUNDED.equals(status)) {
            fg = R.color.brand_on_secondary_container; bg = R.color.brand_secondary_container;
        } else {
            fg = R.color.slot_reserved; bg = R.color.slot_reserved_bg;
        }
        badge.setText(Payment.statusLabel(status));
        badge.setTextColor(ContextCompat.getColor(c, fg));
        badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, bg)));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemPaymentBinding b;

        Holder(@NonNull ItemPaymentBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }

    private static final DiffUtil.ItemCallback<Payment> DIFF = new DiffUtil.ItemCallback<Payment>() {
        @Override
        public boolean areItemsTheSame(@NonNull Payment a, @NonNull Payment b) {
            return Objects.equals(a.getTransactionId(), b.getTransactionId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Payment a, @NonNull Payment b) {
            return Objects.equals(a.getStatus(), b.getStatus())
                    && a.getAmount() == b.getAmount()
                    && a.getRefundAmount() == b.getRefundAmount()
                    && a.getCreatedAt() == b.getCreatedAt();
        }
    };
}

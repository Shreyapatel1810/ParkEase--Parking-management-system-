package com.parkease.app.adapters;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.R;
import com.parkease.app.databinding.ItemNotificationBinding;
import com.parkease.app.models.AppNotification;

import java.util.Objects;

/** Notification inbox list. Unread items show a teal dot. */
public class NotificationAdapter extends ListAdapter<AppNotification, NotificationAdapter.Holder> {

    public interface Listener {
        void onNotificationClick(@NonNull AppNotification notification);
    }

    private final Listener listener;

    public NotificationAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppNotification n = getItem(position);
        h.b.tvTitle.setText(n.getTitle());
        h.b.tvMessage.setText(n.getMessage());
        h.b.tvTime.setText(n.getCreatedAt() > 0 ? DateUtils.getRelativeTimeSpanString(n.getCreatedAt()) : "");
        h.b.unreadDot.setVisibility(n.isRead() ? View.INVISIBLE : View.VISIBLE);
        h.b.imgIcon.setImageResource("BOOKING".equals(n.getType()) ? R.drawable.ic_bookings
                : "PAYMENT".equals(n.getType()) ? R.drawable.ic_rupee
                : R.drawable.ic_notifications);
        h.b.row.setOnClickListener(v -> listener.onNotificationClick(n));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemNotificationBinding b;

        Holder(@NonNull ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }

    private static final DiffUtil.ItemCallback<AppNotification> DIFF = new DiffUtil.ItemCallback<AppNotification>() {
        @Override
        public boolean areItemsTheSame(@NonNull AppNotification a, @NonNull AppNotification b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull AppNotification a, @NonNull AppNotification b) {
            return a.isRead() == b.isRead() && Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getMessage(), b.getMessage());
        }
    };
}

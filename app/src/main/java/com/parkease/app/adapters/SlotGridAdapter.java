package com.parkease.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.databinding.ItemSlotGridBinding;
import com.parkease.app.databinding.ItemSlotHeaderBinding;
import com.parkease.app.models.ParkingSlot;
import com.parkease.app.ui.components.ParkingSlotView;

import java.util.Objects;

/**
 * The visual parking layout: a grid of slots (RecyclerView + GridLayoutManager) with a
 * full-width "Zone A" heading before each zone.
 *
 * Each item already knows its colour status for the chosen time (worked out by
 * SlotRepository.statusFor). Only AVAILABLE slots can be selected; tapping any other slot
 * calls {@link Listener#onUnavailableSlotClick} so the screen can explain why.
 */
public class SlotGridAdapter extends ListAdapter<SlotGridAdapter.Item, RecyclerView.ViewHolder> {

    public interface Listener {
        void onSlotSelected(@NonNull ParkingSlot slot);

        void onUnavailableSlotClick(@NonNull ParkingSlot slot, @NonNull ParkingSlotView.Status status);
    }

    /** One grid cell: either a zone heading or a slot. */
    public static final class Item {
        @Nullable final String header;
        @Nullable final ParkingSlot slot;
        @Nullable final ParkingSlotView.Status status;

        private Item(@Nullable String header, @Nullable ParkingSlot slot, @Nullable ParkingSlotView.Status status) {
            this.header = header;
            this.slot = slot;
            this.status = status;
        }

        public static Item header(@NonNull String text) {
            return new Item(text, null, null);
        }

        public static Item slot(@NonNull ParkingSlot slot, @NonNull ParkingSlotView.Status status) {
            return new Item(null, slot, status);
        }

        boolean isHeader() {
            return header != null;
        }

        String key() {
            return isHeader() ? "h:" + header : "s:" + Objects.requireNonNull(slot).getId();
        }
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SLOT = 1;

    private final Listener listener;
    @Nullable private String selectedSlotId;

    public SlotGridAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setSelectedSlotId(@Nullable String slotId) {
        String old = selectedSlotId;
        selectedSlotId = slotId;
        for (int i = 0; i < getItemCount(); i++) {
            Item item = getItem(i);
            if (item.slot != null && (Objects.equals(item.slot.getId(), old)
                    || Objects.equals(item.slot.getId(), slotId))) {
                notifyItemChanged(i);
            }
        }
    }

    /** Headings take the full row; slots take one column. */
    @NonNull
    public GridLayoutManager.SpanSizeLookup spanSizeLookup(int columns) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_HEADER ? columns : 1;
            }
        };
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isHeader() ? TYPE_HEADER : TYPE_SLOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemSlotHeaderBinding.inflate(inflater, parent, false));
        }
        return new SlotHolder(ItemSlotGridBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = getItem(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).b.tvHeader.setText(item.header);
            return;
        }
        ParkingSlot slot = Objects.requireNonNull(item.slot);
        ParkingSlotView.Status status = Objects.requireNonNull(item.status);
        ParkingSlotView view = ((SlotHolder) holder).b.slotView;
        view.setLabel(slot.getSlotNumber() == null ? slot.getId() : slot.getSlotNumber());
        view.setStatus(status);
        view.setSlotSelected(status == ParkingSlotView.Status.AVAILABLE
                && Objects.equals(slot.getId(), selectedSlotId));
        view.setOnClickListener(v -> {
            if (status == ParkingSlotView.Status.AVAILABLE) listener.onSlotSelected(slot);
            else listener.onUnavailableSlotClick(slot, status);
        });
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemSlotHeaderBinding b;

        HeaderHolder(@NonNull ItemSlotHeaderBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }

    static class SlotHolder extends RecyclerView.ViewHolder {
        final ItemSlotGridBinding b;

        SlotHolder(@NonNull ItemSlotGridBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<Item>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
            return a.key().equals(b.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
            if (a.isHeader()) return Objects.equals(a.header, b.header);
            return a.status == b.status
                    && Objects.equals(Objects.requireNonNull(a.slot).getSlotNumber(),
                    Objects.requireNonNull(b.slot).getSlotNumber());
        }
    };
}

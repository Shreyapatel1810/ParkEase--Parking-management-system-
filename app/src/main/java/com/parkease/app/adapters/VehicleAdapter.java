package com.parkease.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.parkease.app.R;
import com.parkease.app.databinding.ItemVehicleBinding;
import com.parkease.app.models.Vehicle;

import java.util.Objects;

/** "My vehicles" list. Tap a card to edit; ⋮ opens Edit / Delete. */
public class VehicleAdapter extends ListAdapter<Vehicle, VehicleAdapter.Holder> {

    public interface Listener {
        void onEdit(@NonNull Vehicle vehicle);

        void onDelete(@NonNull Vehicle vehicle);
    }

    private final Listener listener;

    public VehicleAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemVehicleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Vehicle v = getItem(position);
        String type = h.itemView.getContext().getString(v.getTypeLabel());
        h.b.imgType.setImageResource(v.getIcon());
        h.b.tvNumber.setText(v.getDisplayNumber());
        h.b.tvDetails.setText(v.hasNickname() ? type + " · " + v.getNickname() : type);
        h.b.btnMore.setContentDescription(h.itemView.getContext()
                .getString(R.string.cd_vehicle_options, v.getDisplayNumber()));

        h.b.card.setOnClickListener(view -> listener.onEdit(v));
        h.b.btnMore.setOnClickListener(anchor -> {
            PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
            menu.getMenu().add(0, 1, 0, R.string.vehicle_edit);
            menu.getMenu().add(0, 2, 1, R.string.vehicle_delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) listener.onEdit(v);
                else listener.onDelete(v);
                return true;
            });
            menu.show();
        });
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemVehicleBinding b;

        Holder(@NonNull ItemVehicleBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }

    private static final DiffUtil.ItemCallback<Vehicle> DIFF = new DiffUtil.ItemCallback<Vehicle>() {
        @Override
        public boolean areItemsTheSame(@NonNull Vehicle a, @NonNull Vehicle b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Vehicle a, @NonNull Vehicle b) {
            return Objects.equals(a.getRegistrationNumber(), b.getRegistrationNumber())
                    && Objects.equals(a.getVehicleType(), b.getVehicleType())
                    && Objects.equals(a.getNickname(), b.getNickname());
        }
    };
}

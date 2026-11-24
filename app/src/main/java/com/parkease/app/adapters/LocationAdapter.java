package com.parkease.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.location.Location;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.parkease.app.R;
import com.parkease.app.databinding.ItemLocationCardBinding;
import com.parkease.app.databinding.ItemLocationCompactBinding;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.utils.FormatUtils;
import com.parkease.app.utils.LocationUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * ONE reusable adapter for every list of parking locations.
 * <ul>
 *   <li>{@link Mode#CARD_HORIZONTAL} – wide cards in a sideways row (Home "Nearby")</li>
 *   <li>{@link Mode#CARD_VERTICAL}   – full-width cards in a list (Explore, Favourites)</li>
 *   <li>{@link Mode#COMPACT}         – small chips in a sideways row (Home favourites / recent)</li>
 * </ul>
 * ListAdapter + DiffUtil only redraws the cards that actually changed when Firebase sends
 * new data (e.g. one location's free-slot count), so lists stay smooth.
 */
public class LocationAdapter extends ListAdapter<ParkingLocation, RecyclerView.ViewHolder> {

    public enum Mode { CARD_HORIZONTAL, CARD_VERTICAL, COMPACT }

    public interface Listener {
        void onLocationClick(@NonNull ParkingLocation location);

        /** Called when the heart is tapped; {@code makeFavourite} is the new wanted state. */
        void onFavouriteClick(@NonNull ParkingLocation location, boolean makeFavourite);
    }

    private final Mode mode;
    private final Listener listener;
    private Set<String> favouriteIds = new HashSet<>();
    @Nullable private Location userLocation;

    public LocationAdapter(@NonNull Mode mode, @NonNull Listener listener) {
        super(DIFF);
        this.mode = mode;
        this.listener = listener;
    }

    /** Hearts are filled for these IDs. */
    public void setFavouriteIds(@NonNull Set<String> ids) {
        favouriteIds = new HashSet<>(ids);
        notifyItemRangeChanged(0, getItemCount());
    }

    /** Distances are shown when the user's position is known. */
    public void setUserLocation(@Nullable Location location) {
        userLocation = location;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemViewType(int position) {
        return mode == Mode.COMPACT ? 1 : 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == 1) {
            return new CompactHolder(ItemLocationCompactBinding.inflate(inflater, parent, false));
        }
        ItemLocationCardBinding b = ItemLocationCardBinding.inflate(inflater, parent, false);
        if (mode == Mode.CARD_HORIZONTAL) {
            // Fixed width so the next card "peeks" in and users know they can scroll
            ViewGroup.LayoutParams lp = b.getRoot().getLayoutParams();
            lp.width = parent.getResources().getDimensionPixelSize(R.dimen.location_card_width);
            b.getRoot().setLayoutParams(lp);
        }
        return new CardHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ParkingLocation item = getItem(position);
        if (holder instanceof CardHolder) ((CardHolder) holder).bind(item);
        else ((CompactHolder) holder).bind(item);
    }

    // ================================================================== card

    class CardHolder extends RecyclerView.ViewHolder {
        private final ItemLocationCardBinding b;

        CardHolder(@NonNull ItemLocationCardBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(@NonNull ParkingLocation p) {
            Context c = itemView.getContext();
            b.tvName.setText(p.getName());
            b.tvAddress.setText(joinAddress(p));
            b.tvRating.setText(c.getString(R.string.location_rating,
                    FormatUtils.rating(p.getAvgRating()), p.getReviewCount()));
            b.tvRating.setVisibility(p.getReviewCount() > 0 ? View.VISIBLE : View.GONE);
            b.tvPrice.setText(c.getString(R.string.location_from_rate, FormatUtils.money(p.getLowestRate())));

            float meters = LocationUtils.distanceTo(userLocation, p);
            b.tvDistance.setVisibility(meters >= 0 ? View.VISIBLE : View.GONE);
            if (meters >= 0) b.tvDistance.setText(FormatUtils.distance(meters));

            bindAvailability(b.tvAvailability, p);

            // Photo (if the admin added one) or the ParkEase logo on a navy background
            if (!TextUtils.isEmpty(p.getImageUrl())) {
                b.imgLocation.setVisibility(View.VISIBLE);
                Glide.with(b.imgLocation).load(p.getImageUrl()).centerCrop().into(b.imgLocation);
            } else {
                Glide.with(b.imgLocation).clear(b.imgLocation);
                b.imgLocation.setVisibility(View.GONE);
            }

            boolean fav = favouriteIds.contains(p.getId());
            b.btnFavourite.setImageResource(fav ? R.drawable.ic_favourite : R.drawable.ic_heart_outline);
            b.btnFavourite.setContentDescription(c.getString(
                    fav ? R.string.cd_remove_favourite : R.string.cd_add_favourite, p.getName()));
            b.btnFavourite.setOnClickListener(v -> listener.onFavouriteClick(p, !fav));
            b.card.setOnClickListener(v -> listener.onLocationClick(p));
        }
    }

    // ================================================================== compact

    class CompactHolder extends RecyclerView.ViewHolder {
        private final ItemLocationCompactBinding b;

        CompactHolder(@NonNull ItemLocationCompactBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(@NonNull ParkingLocation p) {
            Context c = itemView.getContext();
            b.tvName.setText(p.getName());
            if (p.getTotalSlots() > 0) {
                String free = p.getAvailableSlots() > 0
                        ? c.getString(R.string.location_free, p.getAvailableSlots())
                        : c.getString(R.string.location_full);
                b.tvSubtitle.setText(free + " · " + c.getString(R.string.location_from_rate,
                        FormatUtils.money(p.getLowestRate())));
            } else {
                b.tvSubtitle.setText(joinAddress(p));   // recent item without live data yet
            }
            boolean fav = favouriteIds.contains(p.getId());
            b.imgIcon.setImageResource(fav ? R.drawable.ic_favourite : R.drawable.ic_location);
            b.card.setOnClickListener(v -> listener.onLocationClick(p));
        }
    }

    // ================================================================== shared helpers

    /** Green "12 free", or red "Full". */
    public static void bindAvailability(@NonNull android.widget.TextView badge, @NonNull ParkingLocation p) {
        Context c = badge.getContext();
        boolean full = p.getAvailableSlots() <= 0;
        badge.setText(full ? c.getString(R.string.location_full)
                : c.getString(R.string.location_free, p.getAvailableSlots()));
        badge.setTextColor(ContextCompat.getColor(c, full ? R.color.slot_occupied : R.color.slot_available));
        badge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(c, full ? R.color.slot_occupied_bg : R.color.slot_available_bg)));
    }

    @NonNull
    private static String joinAddress(@NonNull ParkingLocation p) {
        if (TextUtils.isEmpty(p.getCity())) return p.getAddress() == null ? "" : p.getAddress();
        if (TextUtils.isEmpty(p.getAddress())) return p.getCity();
        return p.getAddress() + ", " + p.getCity();
    }

    private static final DiffUtil.ItemCallback<ParkingLocation> DIFF = new DiffUtil.ItemCallback<ParkingLocation>() {
        @Override
        public boolean areItemsTheSame(@NonNull ParkingLocation a, @NonNull ParkingLocation b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ParkingLocation a, @NonNull ParkingLocation b) {
            return Objects.equals(a.getName(), b.getName())
                    && Objects.equals(a.getAddress(), b.getAddress())
                    && Objects.equals(a.getImageUrl(), b.getImageUrl())
                    && a.getAvailableSlots() == b.getAvailableSlots()
                    && a.getTotalSlots() == b.getTotalSlots()
                    && a.getAvgRating() == b.getAvgRating()
                    && a.getReviewCount() == b.getReviewCount()
                    && a.getCarRatePerHour() == b.getCarRatePerHour()
                    && a.getBikeRatePerHour() == b.getBikeRatePerHour();
        }
    };
}

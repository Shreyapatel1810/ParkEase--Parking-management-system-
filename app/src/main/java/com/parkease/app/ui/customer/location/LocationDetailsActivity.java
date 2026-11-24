package com.parkease.app.ui.customer.location;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.databinding.ActivityLocationDetailsBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.FavouriteRepository;
import com.parkease.app.repository.LocationRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;

import java.util.Locale;
import java.util.Set;

/**
 * STEP 7 – Details of one parking place (opened from every location card).
 * Free slots update live. Opening it records a "recently visited" entry in SQLite.
 * Booking itself (slot map, payment, QR) is built in the booking module.
 */
public class LocationDetailsActivity extends BaseActivity {

    private ActivityLocationDetailsBinding binding;
    private LocationRepository locationRepository;
    private FavouriteRepository favouriteRepository;
    private Subscription locationSub = Subscription.EMPTY;
    private Subscription favouriteSub = Subscription.EMPTY;

    private String locationId;
    @Nullable private ParkingLocation location;
    private boolean isFavourite = false;
    private boolean visitRecorded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        locationId = getIntent().getStringExtra(Constants.EXTRA_LOCATION_ID);
        if (locationId == null) {
            finish();
            return;
        }
        locationRepository = new LocationRepository(this);
        favouriteRepository = new FavouriteRepository(this);

        applyStatusBarPadding(binding.header.getRoot());
        applyStatusBarScrim(binding.statusBarScrim.getRoot());
        applyNavigationBarPadding(binding.scroll);
        binding.header.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.header.toolbar.setNavigationContentDescription(R.string.action_back);
        binding.header.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnDirections.setOnClickListener(v -> openDirections());
        binding.btnFavourite.setOnClickListener(v -> toggleFavourite());
        binding.btnBook.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.details_booking_next_title)
                .setMessage(R.string.details_booking_next_msg)
                .setPositiveButton(R.string.action_ok, null)
                .show());

        binding.stateView.showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (locationId == null) return;
        locationSub = locationRepository.observeLocation(locationId, new DataCallback<ParkingLocation>() {
            @Override
            public void onSuccess(ParkingLocation data) {
                if (data == null || !data.isActive()) {
                    binding.stateView.showEmpty(R.drawable.ic_location, getString(R.string.details_not_found),
                            null, getString(R.string.action_back), v -> finish());
                    return;
                }
                location = data;
                binding.stateView.hide();
                show(data);
                if (!visitRecorded) {
                    locationRepository.recordVisit(data);   // SQLite "recently visited"
                    visitRecorded = true;
                }
            }

            @Override
            public void onError(@NonNull String message) {
                binding.stateView.showError(message, v -> recreate());
            }
        });
        favouriteSub = favouriteRepository.observeFavouriteIds(new DataCallback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> ids) {
                isFavourite = ids.contains(locationId);
                binding.btnFavourite.setIconResource(isFavourite ? R.drawable.ic_favourite : R.drawable.ic_heart_outline);
            }

            @Override
            public void onError(@NonNull String message) { }
        });
    }

    @Override
    protected void onStop() {
        locationSub.cancel();
        favouriteSub.cancel();
        super.onStop();
    }

    private void show(@NonNull ParkingLocation p) {
        binding.header.toolbar.setTitle(R.string.app_name);
        binding.header.tvHeaderTitle.setText(p.getName());
        binding.header.tvHeaderSubtitle.setText(p.getCity());

        binding.tvFree.setText(String.valueOf(Math.max(0, p.getAvailableSlots())));
        binding.tvFree.setTextColor(getColor(p.getAvailableSlots() > 0 ? R.color.slot_available : R.color.slot_occupied));
        binding.tvCarRate.setText(p.acceptsCars() ? FormatUtils.money(p.getCarRatePerHour()) : "—");
        binding.tvBikeRate.setText(p.acceptsBikes() ? FormatUtils.money(p.getBikeRatePerHour()) : "—");

        setRow(binding.rowAddress, R.drawable.ic_location, R.string.details_address,
                p.getAddress() + (p.getCity() == null ? "" : ", " + p.getCity()));

        boolean allDay = "00:00".equals(p.getOpenTime()) && "23:59".equals(p.getCloseTime());
        String hours = allDay ? getString(R.string.details_open_24h)
                : getString(R.string.details_hours_value, p.getOpenTime(), p.getCloseTime());
        if (!FormatUtils.isOpenNow(p.getOpenTime(), p.getCloseTime())) {
            hours += " · " + getString(R.string.location_closed);
        }
        setRow(binding.rowHours, R.drawable.ic_clock, R.string.details_hours, hours);
        setRow(binding.rowRating, R.drawable.ic_star, R.string.details_rating, p.getReviewCount() > 0
                ? getString(R.string.location_rating, FormatUtils.rating(p.getAvgRating()), p.getReviewCount())
                : "—");

        binding.chipAmenities.removeAllViews();
        boolean hasAmenities = p.getAmenities() != null && !p.getAmenities().isEmpty();
        binding.tvAmenitiesLabel.setVisibility(hasAmenities ? View.VISIBLE : View.GONE);
        if (hasAmenities) {
            for (String a : p.getAmenities()) {
                Chip chip = new Chip(this);
                chip.setText(a);
                chip.setCheckable(false);
                chip.setClickable(false);
                binding.chipAmenities.addView(chip);
            }
        }
        binding.btnDirections.setEnabled(p.hasCoordinates());
    }

    private void setRow(@NonNull ItemProfileRowBinding row, int icon, int label, @Nullable String value) {
        row.imgIcon.setImageResource(icon);
        row.tvLabel.setText(label);
        row.tvValue.setText(value == null || value.isEmpty() ? "—" : value);
    }

    /** Opens Google Maps (app or website) at the parking place. */
    private void openDirections() {
        if (location == null || !location.hasCoordinates()) return;
        Uri uri = Uri.parse(String.format(Locale.US,
                "https://www.google.com/maps/search/?api=1&query=%f,%f",
                location.getLatitude(), location.getLongitude()));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            showMessage(R.string.details_no_maps);
        }
    }

    private void toggleFavourite() {
        boolean makeFavourite = !isFavourite;
        favouriteRepository.setFavourite(locationId, makeFavourite, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) { }

            @Override
            public void onError(@NonNull String message) {
                showMessage(message);
            }
        });
        showMessage(makeFavourite ? R.string.favourite_added : R.string.favourite_removed);
    }
}

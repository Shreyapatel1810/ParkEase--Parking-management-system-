package com.parkease.app.ui.customer.location;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.SystemBarStyle;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parkease.app.R;
import com.parkease.app.adapters.ImagePagerAdapter;
import com.parkease.app.adapters.LocationAdapter;
import com.parkease.app.adapters.ReviewAdapter;
import com.parkease.app.databinding.ActivityLocationDetailsBinding;
import com.parkease.app.databinding.ItemProfileRowBinding;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.models.Review;
import com.parkease.app.repository.DataCallback;
import com.parkease.app.repository.FavouriteRepository;
import com.parkease.app.repository.LocationRepository;
import com.parkease.app.repository.ReviewRepository;
import com.parkease.app.repository.Subscription;
import com.parkease.app.ui.common.BaseActivity;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FormatUtils;
import com.parkease.app.utils.InsetsHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * STEP 8 – Details of one parking place, all live from Firebase:
 * photo gallery, name, address, open/closed, free slots, vehicle types, hourly charges,
 * facilities, rating summary, reviews, directions, favourite and a fixed "Book now" bar.
 * Opening it also saves a "recently visited" entry in SQLite.
 */
public class LocationDetailsActivity extends BaseActivity {

    private ActivityLocationDetailsBinding binding;
    private LocationRepository locationRepository;
    private FavouriteRepository favouriteRepository;
    private ReviewRepository reviewRepository;
    private final List<Subscription> subscriptions = new ArrayList<>();

    private final ImagePagerAdapter galleryAdapter = new ImagePagerAdapter();
    private final ReviewAdapter reviewAdapter = new ReviewAdapter();

    private String locationId;
    @Nullable private ParkingLocation location;
    private boolean isFavourite = false;
    private boolean visitRecorded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationId = getIntent().getStringExtra(Constants.EXTRA_LOCATION_ID);
        if (locationId == null) {
            finish();
            return;
        }
        binding = ActivityLocationDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        locationRepository = new LocationRepository(this);
        favouriteRepository = new FavouriteRepository(this);
        reviewRepository = new ReviewRepository(this);

        // Photo goes under the status bar; buttons and the bottom bar stay clear of system bars
        InsetsHelper.statusBarPadding(binding.topBar);
        InsetsHelper.navigationBarPadding(binding.bottomBarContent);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnFavourite.setOnClickListener(v -> toggleFavourite());
        binding.btnDirections.setOnClickListener(v -> openDirections());
        binding.btnBook.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.details_booking_next_title)
                .setMessage(R.string.details_booking_next_msg)
                .setPositiveButton(R.string.action_ok, null)
                .show());

        binding.galleryPager.setAdapter(galleryAdapter);
        binding.galleryPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.tvImageCounter.setText(getString(R.string.details_image_counter,
                        position + 1, galleryAdapter.getItemCount()));
            }
        });
        binding.rvReviews.setAdapter(reviewAdapter);

        binding.stateView.showLoading();
    }

    /** The photo/navy area is dark, so the status bar icons stay white. */
    @NonNull
    @Override
    protected SystemBarStyle statusBarStyle() {
        return SystemBarStyle.dark(Color.TRANSPARENT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (binding == null) return;

        subscriptions.add(locationRepository.observeLocation(locationId, new DataCallback<ParkingLocation>() {
            @Override
            public void onSuccess(ParkingLocation data) {
                if (data == null || !data.isActive()) {
                    binding.stateView.showEmpty(R.drawable.ic_location, getString(R.string.details_not_found),
                            null, getString(R.string.action_back), v -> finish());
                    binding.bottomBar.setVisibility(View.GONE);
                    return;
                }
                location = data;
                binding.stateView.hide();
                binding.bottomBar.setVisibility(View.VISIBLE);
                show(data);
                if (!visitRecorded) {
                    locationRepository.recordVisit(data);      // SQLite "recently visited"
                    visitRecorded = true;
                }
            }

            @Override
            public void onError(@NonNull String message) {
                binding.stateView.showError(message, v -> recreate());
            }
        }));

        subscriptions.add(favouriteRepository.observeFavouriteIds(new DataCallback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> ids) {
                isFavourite = ids.contains(locationId);
                binding.btnFavourite.setImageResource(isFavourite ? R.drawable.ic_favourite : R.drawable.ic_heart_outline);
                String name = location == null ? "" : location.getName();
                binding.btnFavourite.setContentDescription(getString(
                        isFavourite ? R.string.cd_remove_favourite : R.string.cd_add_favourite, name));
            }

            @Override
            public void onError(@NonNull String message) { }
        }));

        subscriptions.add(reviewRepository.observeReviews(locationId, new DataCallback<List<Review>>() {
            @Override
            public void onSuccess(List<Review> reviews) {
                showReviews(reviews);
            }

            @Override
            public void onError(@NonNull String message) {
                binding.rvReviews.setVisibility(View.GONE);
                binding.tvNoReviews.setVisibility(View.VISIBLE);
                binding.tvNoReviews.setText(message);
            }
        }));
    }

    @Override
    protected void onStop() {
        for (Subscription s : subscriptions) s.cancel();
        subscriptions.clear();
        super.onStop();
    }

    // ================================================================== display

    private void show(@NonNull ParkingLocation p) {
        // ---- gallery
        List<String> images = p.getAllImages();
        galleryAdapter.setImages(images);
        boolean hasImages = !images.isEmpty();
        binding.galleryPager.setVisibility(hasImages ? View.VISIBLE : View.GONE);
        binding.galleryPlaceholder.setVisibility(hasImages ? View.GONE : View.VISIBLE);
        binding.tvImageCounter.setVisibility(images.size() > 1 ? View.VISIBLE : View.GONE);
        binding.tvImageCounter.setText(getString(R.string.details_image_counter,
                binding.galleryPager.getCurrentItem() + 1, images.size()));

        // ---- name card
        binding.tvName.setText(p.getName());
        binding.tvAddress.setText(fullAddress(p));
        LocationAdapter.bindAvailability(binding.tvAvailability, p);
        boolean open = FormatUtils.isOpenNow(p.getOpenTime(), p.getCloseTime());
        badge(binding.tvOpenStatus, getString(open ? R.string.details_open_now : R.string.location_closed),
                open ? R.color.brand_on_secondary_container : R.color.slot_occupied,
                open ? R.color.brand_secondary_container : R.color.slot_occupied_bg);
        binding.tvRatingBadge.setVisibility(p.getReviewCount() > 0 ? View.VISIBLE : View.GONE);
        binding.tvRatingBadge.setText(getString(R.string.location_rating,
                FormatUtils.rating(p.getAvgRating()), p.getReviewCount()));

        // ---- live numbers
        binding.tvFree.setText(String.valueOf(Math.max(0, p.getAvailableSlots())));
        binding.tvFree.setTextColor(getColor(p.getAvailableSlots() > 0 ? R.color.slot_available : R.color.slot_occupied));
        binding.tvTotal.setText(getString(R.string.details_free_of, p.getTotalSlots()));
        binding.tvCarRate.setText(p.acceptsCars() ? FormatUtils.money(p.getCarRatePerHour()) : "—");
        binding.tvBikeRate.setText(p.acceptsBikes() ? FormatUtils.money(p.getBikeRatePerHour()) : "—");

        // ---- information
        setRow(binding.rowAddress, R.drawable.ic_location, R.string.details_address, fullAddress(p));
        boolean allDay = "00:00".equals(p.getOpenTime()) && "23:59".equals(p.getCloseTime());
        String hours = allDay ? getString(R.string.details_open_24h)
                : getString(R.string.details_hours_value, p.getOpenTime(), p.getCloseTime());
        if (!open) hours += " · " + getString(R.string.location_closed);
        setRow(binding.rowHours, R.drawable.ic_clock, R.string.details_hours, hours);

        List<String> vehicles = new ArrayList<>();
        List<String> charges = new ArrayList<>();
        if (p.acceptsCars()) {
            vehicles.add(getString(R.string.explore_filter_car));
            charges.add(getString(R.string.details_car_charge, FormatUtils.money(p.getCarRatePerHour())));
        }
        if (p.acceptsBikes()) {
            vehicles.add(getString(R.string.explore_filter_bike));
            charges.add(getString(R.string.details_bike_charge, FormatUtils.money(p.getBikeRatePerHour())));
        }
        setRow(binding.rowVehicles, p.acceptsCars() ? R.drawable.ic_car : R.drawable.ic_bike,
                R.string.details_vehicle_types, String.join(", ", vehicles));
        setRow(binding.rowCharges, R.drawable.ic_rupee, R.string.details_charges, String.join("\n", charges));

        // ---- facilities
        binding.chipAmenities.removeAllViews();
        boolean hasAmenities = p.getAmenities() != null && !p.getAmenities().isEmpty();
        binding.tvAmenitiesLabel.setVisibility(hasAmenities ? View.VISIBLE : View.GONE);
        if (hasAmenities) {
            for (String a : p.getAmenities()) {
                Chip chip = new Chip(this);
                chip.setText(a);
                chip.setCheckable(false);
                chip.setClickable(false);
                if (Constants.AMENITY_EV.equalsIgnoreCase(a)) chip.setChipIconResource(R.drawable.ic_bolt);
                else if (Constants.AMENITY_CCTV.equalsIgnoreCase(a)) chip.setChipIconResource(R.drawable.ic_shield);
                else chip.setChipIconResource(R.drawable.ic_check_circle);
                chip.setChipIconVisible(true);
                binding.chipAmenities.addView(chip);
            }
        }

        binding.btnDirections.setEnabled(p.hasCoordinates());

        // ---- bottom bar
        binding.tvBottomPrice.setText(getString(R.string.details_from_price, FormatUtils.money(p.getLowestRate())));
        binding.tvBottomFree.setText(p.getAvailableSlots() > 0
                ? getString(R.string.location_free, p.getAvailableSlots())
                : getString(R.string.location_full));
        binding.btnBook.setEnabled(p.getAvailableSlots() > 0);
    }

    private void showReviews(@NonNull List<Review> reviews) {
        reviewAdapter.submitList(reviews);
        boolean empty = reviews.isEmpty();
        binding.rvReviews.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.tvNoReviews.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.tvNoReviews.setText(R.string.details_no_reviews);
        binding.ratingSummary.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.reviewDivider.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.tvReviewCount.setText(getString(R.string.details_reviews_count, reviews.size()));
        if (empty) return;

        // Average of the reviews actually shown
        double sum = 0;
        for (Review r : reviews) sum += r.getRating();
        double avg = sum / reviews.size();
        binding.tvAvgRating.setText(FormatUtils.rating(avg));
        binding.ratingAvg.setRating((float) avg);
        binding.ratingAvg.setContentDescription(getString(R.string.cd_rating, FormatUtils.rating(avg)));
        binding.tvRatingBased.setText(getString(R.string.details_reviews_count, reviews.size()));
    }

    private void badge(@NonNull android.widget.TextView view, @NonNull String text,
                       @ColorRes int fg, @ColorRes int bg) {
        view.setText(text);
        view.setTextColor(getColor(fg));
        view.setBackgroundTintList(ColorStateList.valueOf(getColor(bg)));
    }

    private void setRow(@NonNull ItemProfileRowBinding row, int icon, int label, @Nullable String value) {
        row.imgIcon.setImageResource(icon);
        row.tvLabel.setText(label);
        row.tvValue.setText(value == null || value.isEmpty() ? "—" : value);
    }

    @NonNull
    private static String fullAddress(@NonNull ParkingLocation p) {
        String a = p.getAddress() == null ? "" : p.getAddress();
        if (p.getCity() == null || p.getCity().isEmpty()) return a;
        return a.isEmpty() ? p.getCity() : a + ", " + p.getCity();
    }

    // ================================================================== actions

    /** Opens Google Maps (app or website) at the parking place – no API key needed. */
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

package com.parkease.app.ui.customer.search;

import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.parkease.app.R;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.LocationUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Everything the user chose on the Explore screen, plus the logic that applies it.
 * Keeping filtering in one plain class makes it easy to read and to test.
 *
 * Search:  name, area/address, city or code (case-insensitive)
 * Filters: vehicle type, max price per hour, free slots now, EV charging, covered
 * Sort:    recommended, price low→high, price high→low, most free slots, top rated
 */
public class SearchFilters {

    public enum Vehicle { ANY, CAR, BIKE }

    public enum Sort {
        RECOMMENDED(R.string.sort_recommended),
        PRICE_LOW(R.string.sort_price_low),
        PRICE_HIGH(R.string.sort_price_high),
        MOST_AVAILABLE(R.string.sort_most_available),
        TOP_RATED(R.string.sort_top_rated);

        @StringRes public final int label;

        Sort(@StringRes int label) {
            this.label = label;
        }
    }

    /** 0 means "any price". */
    public static final int ANY_PRICE = 0;

    public String query = "";
    public Vehicle vehicle = Vehicle.ANY;
    public int maxPrice = ANY_PRICE;
    public boolean onlyAvailable = false;
    public boolean ev = false;
    public boolean covered = false;
    public Sort sort = Sort.RECOMMENDED;

    /**
     * The hourly price that matters for this search:
     * car rate when "Car" is chosen, bike rate for "Bike", otherwise the cheapest rate.
     */
    public double priceFor(@NonNull ParkingLocation p) {
        switch (vehicle) {
            case CAR: return p.getCarRatePerHour();
            case BIKE: return p.getBikeRatePerHour();
            default: return p.getLowestRate();
        }
    }

    /** True if the location passes the search text and every active filter. */
    public boolean matches(@NonNull ParkingLocation p) {
        if (!p.matches(query)) return false;
        if (vehicle == Vehicle.CAR && !p.acceptsCars()) return false;
        if (vehicle == Vehicle.BIKE && !p.acceptsBikes()) return false;
        if (maxPrice != ANY_PRICE && priceFor(p) > maxPrice) return false;
        if (onlyAvailable && p.getAvailableSlots() <= 0) return false;
        if (ev && !p.hasAmenity(Constants.AMENITY_EV)) return false;
        return !covered || p.hasAmenity(Constants.AMENITY_COVERED);
    }

    /** Filters, then sorts. Returns a new list (the input is not changed). */
    @NonNull
    public List<ParkingLocation> apply(@NonNull List<ParkingLocation> all, @Nullable Location me) {
        List<ParkingLocation> result = new ArrayList<>();
        for (ParkingLocation p : all) if (matches(p)) result.add(p);

        switch (sort) {
            case PRICE_LOW:
                result.sort(Comparator.comparingDouble(this::priceFor));
                break;
            case PRICE_HIGH:
                result.sort(Comparator.comparingDouble(this::priceFor).reversed());
                break;
            case MOST_AVAILABLE:
                result.sort((a, b) -> Integer.compare(b.getAvailableSlots(), a.getAvailableSlots()));
                break;
            case TOP_RATED:
                result.sort((a, b) -> {
                    int byRating = Double.compare(b.getAvgRating(), a.getAvgRating());
                    return byRating != 0 ? byRating : Integer.compare(b.getReviewCount(), a.getReviewCount());
                });
                break;
            default:
                LocationUtils.sortForUser(result, me);   // nearest first, or most free + best rated
                break;
        }
        return result;
    }

    /** Number of active filters (not counting search text or sort) – shown on the Filters button. */
    public int activeFilterCount() {
        int n = 0;
        if (vehicle != Vehicle.ANY) n++;
        if (maxPrice != ANY_PRICE) n++;
        if (onlyAvailable) n++;
        if (ev) n++;
        if (covered) n++;
        return n;
    }

    public boolean isDefault() {
        return query.isEmpty() && activeFilterCount() == 0 && sort == Sort.RECOMMENDED;
    }

    public void reset() {
        query = "";
        vehicle = Vehicle.ANY;
        maxPrice = ANY_PRICE;
        onlyAvailable = ev = covered = false;
        sort = Sort.RECOMMENDED;
    }

    // ------------------------------------------------------------------ Bundle (for the bottom sheet)

    private static final String K_VEHICLE = "vehicle", K_PRICE = "maxPrice", K_AVAILABLE = "available",
            K_EV = "ev", K_COVERED = "covered", K_SORT = "sort";

    @NonNull
    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(K_VEHICLE, vehicle.name());
        b.putInt(K_PRICE, maxPrice);
        b.putBoolean(K_AVAILABLE, onlyAvailable);
        b.putBoolean(K_EV, ev);
        b.putBoolean(K_COVERED, covered);
        b.putString(K_SORT, sort.name());
        return b;
    }

    /** Copies the sheet's choices into this object (search text is kept). */
    public void readFrom(@NonNull Bundle b) {
        vehicle = Vehicle.valueOf(b.getString(K_VEHICLE, Vehicle.ANY.name()));
        maxPrice = b.getInt(K_PRICE, ANY_PRICE);
        onlyAvailable = b.getBoolean(K_AVAILABLE);
        ev = b.getBoolean(K_EV);
        covered = b.getBoolean(K_COVERED);
        sort = Sort.valueOf(b.getString(K_SORT, Sort.RECOMMENDED.name()));
    }
}

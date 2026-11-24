package com.parkease.app.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A parking place (mall, station, office…) stored in Firebase at  locations/{locationId}.
 *
 * <pre>
 * locations/{id}: { code, name, address, city, latitude, longitude,
 *                   carRatePerHour, bikeRatePerHour, openTime, closeTime,
 *                   totalSlots, availableSlots, amenities[], imageUrl,
 *                   avgRating, reviewCount, active, createdAt }
 * </pre>
 * Admins create and edit these; customers can only read them.
 */
@IgnoreExtraProperties
public class ParkingLocation {

    @Exclude
    private String id;               // the Firebase key – not saved inside the record

    private String code;             // short code used in slot IDs, e.g. "PMC01"
    private String name;
    private String address;
    private String city;
    private double latitude;
    private double longitude;
    private double carRatePerHour;   // ₹ per hour for cars (0 = cars not accepted)
    private double bikeRatePerHour;  // ₹ per hour for bikes (0 = bikes not accepted)
    private String openTime;         // "06:00"
    private String closeTime;        // "23:00"
    private int totalSlots;
    private int availableSlots;
    private List<String> amenities = new ArrayList<>();
    private String imageUrl;          // main photo (shown on cards)
    private List<String> images = new ArrayList<>();   // extra photos for the details gallery
    private double avgRating;
    private int reviewCount;
    private boolean active = true;   // inactive locations are hidden from customers
    private long createdAt;

    /** Required by Firebase. */
    public ParkingLocation() { }

    // ------------------------------------------------------------------ getters / setters

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getCarRatePerHour() { return carRatePerHour; }
    public void setCarRatePerHour(double carRatePerHour) { this.carRatePerHour = carRatePerHour; }

    public double getBikeRatePerHour() { return bikeRatePerHour; }
    public void setBikeRatePerHour(double bikeRatePerHour) { this.bikeRatePerHour = bikeRatePerHour; }

    public String getOpenTime() { return openTime; }
    public void setOpenTime(String openTime) { this.openTime = openTime; }

    public String getCloseTime() { return closeTime; }
    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }

    public int getTotalSlots() { return totalSlots; }
    public void setTotalSlots(int totalSlots) { this.totalSlots = totalSlots; }

    public int getAvailableSlots() { return availableSlots; }
    public void setAvailableSlots(int availableSlots) { this.availableSlots = availableSlots; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) {
        this.amenities = amenities == null ? new ArrayList<>() : amenities;
    }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }

    public double getAvgRating() { return avgRating; }
    public void setAvgRating(double avgRating) { this.avgRating = avgRating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    // ------------------------------------------------------------------ helpers (not saved)

    @Exclude
    public boolean hasCoordinates() {
        return latitude != 0 || longitude != 0;
    }

    @Exclude
    public boolean acceptsCars() { return carRatePerHour > 0; }

    @Exclude
    public boolean acceptsBikes() { return bikeRatePerHour > 0; }

    /** Cheapest hourly rate the location offers (for "from ₹15/hr"). */
    @Exclude
    public double getLowestRate() {
        if (acceptsCars() && acceptsBikes()) return Math.min(carRatePerHour, bikeRatePerHour);
        return acceptsCars() ? carRatePerHour : bikeRatePerHour;
    }

    /** Main photo first, then the gallery photos, without duplicates or empty values. */
    @Exclude
    @NonNull
    public List<String> getAllImages() {
        List<String> all = new ArrayList<>();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) all.add(imageUrl.trim());
        if (images != null) {
            for (String url : images) {
                if (url != null && !url.trim().isEmpty() && !all.contains(url.trim())) all.add(url.trim());
            }
        }
        return all;
    }

    @Exclude
    public boolean hasAmenity(@NonNull String amenity) {
        if (amenities == null) return false;
        for (String a : amenities) {
            if (a != null && a.equalsIgnoreCase(amenity)) return true;
        }
        return false;
    }

    /** True if the text appears in the name, address, city or code (used by search). */
    @Exclude
    public boolean matches(@Nullable String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        return contains(name, q) || contains(address, q) || contains(city, q) || contains(code, q);
    }

    private static boolean contains(@Nullable String field, @NonNull String q) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(q);
    }
}

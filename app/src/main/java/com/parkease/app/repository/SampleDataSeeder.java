package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.parkease.app.R;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.NetworkUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * DEMO / TESTING TOOL (admin only).
 * Writes real sample records into Firebase so the customer screens have data before the
 * admin "Manage locations" module exists:
 *   - 6 parking locations in Ahmedabad (fictional names)
 *   - 1 promotion for the home screen
 *   - 1 upcoming booking + 1 notification for the admin's OWN account
 *
 * Fixed IDs (LOC_SAMPLE_01 …) mean running it twice updates the same records instead of
 * creating duplicates. The database rules reject this for non-admins.
 */
public class SampleDataSeeder {

    private static final long HOUR = 60 * 60 * 1000L;

    private final Context context;

    public SampleDataSeeder(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public void seed(@NonNull DataCallback<Void> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return;
        }
        if (!NetworkUtils.isOnline(context)) {
            callback.onError(context.getString(R.string.err_no_internet));
            return;
        }
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            callback.onError(context.getString(R.string.err_session_expired));
            return;
        }

        // One multi-path update = all records are saved together, or none are.
        Map<String, Object> updates = new HashMap<>();
        addLocation(updates, "LOC_SAMPLE_01", "RFP01", "Riverfront Plaza Parking",
                "Ashram Road, near Riverfront", 23.0300, 72.5720, 40, 15, 60, 23,
                "06:00", "23:00", 4.5, 128, Constants.AMENITY_CCTV, Constants.AMENITY_COVERED, "Security Guard");
        addLocation(updates, "LOC_SAMPLE_02", "SGH01", "SG Highway Central Parking",
                "Bodakdev, SG Highway", 23.0395, 72.5070, 50, 20, 120, 47,
                "00:00", "23:59", 4.3, 342, Constants.AMENITY_CCTV, Constants.AMENITY_EV, Constants.AMENITY_COVERED);
        addLocation(updates, "LOC_SAMPLE_03", "PNB01", "Prahlad Nagar Business Park",
                "Corporate Road, Prahlad Nagar", 23.0120, 72.5110, 45, 15, 80, 12,
                "07:00", "22:00", 4.1, 96, Constants.AMENITY_CCTV, Constants.AMENITY_EV);
        addLocation(updates, "LOC_SAMPLE_04", "KST01", "Kalupur Station Parking",
                "Kalupur Railway Station, Gate 2", 23.0270, 72.6010, 30, 10, 150, 0,
                "00:00", "23:59", 3.9, 511, Constants.AMENITY_CCTV, "Security Guard");
        addLocation(updates, "LOC_SAMPLE_05", "NVM01", "Navrangpura Market Parking",
                "CG Road, Navrangpura", 23.0360, 72.5610, 35, 10, 40, 9,
                "09:00", "22:00", 4.0, 77, Constants.AMENITY_COVERED);
        addLocation(updates, "LOC_SAMPLE_06", "SCV01", "Science City Visitor Parking",
                "Science City Road, Sola", 23.0790, 72.4940, 30, 10, 200, 164,
                "09:00", "20:00", 4.6, 209, Constants.AMENITY_CCTV, Constants.AMENITY_EV, "Wheelchair Access");

        // Promotion (information only – no fake discount codes)
        Map<String, Object> promo = new HashMap<>();
        promo.put("title", "Book ahead, park stress-free");
        promo.put("message", "Reserve your slot before you leave home and walk straight in with your QR ticket.");
        promo.put("active", true);
        promo.put("priority", 1);
        updates.put(Constants.NODE_PROMOTIONS + "/PROMO_SAMPLE_01", promo);

        // An upcoming booking for MY account, starting in 3 hours, for 2 hours
        long start = System.currentTimeMillis() + 3 * HOUR;
        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingCode", "PE-DEMO01");
        booking.put("userId", me.getUid());
        booking.put("userName", me.getDisplayName() == null ? "" : me.getDisplayName());
        booking.put("locationId", "LOC_SAMPLE_02");
        booking.put("locationName", "SG Highway Central Parking");
        booking.put("slotId", "SGH01-G-A-007");
        booking.put("slotNumber", "A-07");
        booking.put("vehicleNumber", "GJ01AB1234");
        booking.put("vehicleType", Constants.VEHICLE_CAR);
        booking.put("startTime", start);
        booking.put("endTime", start + 2 * HOUR);
        booking.put("status", Constants.BOOKING_CONFIRMED);
        booking.put("amount", 100);
        booking.put("createdAt", ServerValue.TIMESTAMP);
        updates.put(Constants.NODE_BOOKINGS + "/BK_SAMPLE_" + me.getUid(), booking);

        // A notification for my account
        Map<String, Object> note = new HashMap<>();
        note.put("title", "Booking confirmed");
        note.put("message", "Slot A-07 at SG Highway Central Parking is reserved for you.");
        note.put("type", "BOOKING");
        note.put("isRead", false);
        note.put("createdAt", ServerValue.TIMESTAMP);
        updates.put(Constants.NODE_NOTIFICATIONS + "/" + me.getUid() + "/NTF_SAMPLE_01", note);

        FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    private static void addLocation(Map<String, Object> updates, String id, String code, String name,
                                    String address, double lat, double lng, double carRate, double bikeRate,
                                    int total, int available, String open, String close,
                                    double rating, int reviews, String... amenities) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("address", address);
        m.put("city", "Ahmedabad");
        m.put("latitude", lat);
        m.put("longitude", lng);
        m.put("carRatePerHour", carRate);
        m.put("bikeRatePerHour", bikeRate);
        m.put("openTime", open);
        m.put("closeTime", close);
        m.put("totalSlots", total);
        m.put("availableSlots", available);
        m.put("amenities", Arrays.asList(amenities));
        m.put("avgRating", rating);
        m.put("reviewCount", reviews);
        m.put("active", true);
        m.put("createdAt", ServerValue.TIMESTAMP);
        updates.put(Constants.NODE_LOCATIONS + "/" + id, m);
    }
}

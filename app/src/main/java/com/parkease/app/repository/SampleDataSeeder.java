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
 * Writes real sample records into Firebase so the customer screens can be tested before
 * the admin "Manage locations" module exists:
 *   - 10 parking locations in Ahmedabad (fictional names) with different prices,
 *     vehicle types (car-only, bike-only, both), facilities and availability (incl. one full)
 *   - 2–4 sample reviews per location; each location's rating and review count are
 *     CALCULATED from those reviews, so the numbers always match what is shown
 *   - 1 promotion for the home screen
 *   - 1 upcoming booking + 1 notification for the admin's OWN account
 *
 * Fixed IDs (LOC_SAMPLE_01 …) mean running it again updates the same records instead of
 * creating duplicates. The database rules reject this for non-admins.
 */
public class SampleDataSeeder {

    private static final long HOUR = 60 * 60 * 1000L;
    private static final long DAY = 24 * HOUR;

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

        // One multi-path update = everything is saved together, or nothing is.
        Map<String, Object> u = new HashMap<>();

        location(u, "LOC_SAMPLE_01", "RFP01", "Riverfront Plaza Parking", "Ashram Road, near Riverfront",
                23.0300, 72.5720, 40, 15, 60, 23, "06:00", "23:00",
                new String[]{Constants.AMENITY_CCTV, Constants.AMENITY_COVERED, "Security Guard"},
                review("Priya Mehta", 5, "Clean, well lit and right next to the riverfront walk."),
                review("Arjun Patel", 4, "Easy entry. Weekends get busy after 6 pm."),
                review("Neha Shah", 4, "Covered slots kept my car cool in the afternoon."));

        location(u, "LOC_SAMPLE_02", "SGH01", "SG Highway Central Parking", "Bodakdev, SG Highway",
                23.0395, 72.5070, 50, 20, 120, 47, "00:00", "23:59",
                new String[]{Constants.AMENITY_CCTV, Constants.AMENITY_EV, Constants.AMENITY_COVERED},
                review("Rohan Desai", 5, "Open all night and the EV chargers actually work."),
                review("Kavya Joshi", 4, "A bit pricey but always has space."),
                review("Imran Shaikh", 5, "QR entry took seconds."),
                review("Sneha Rao", 3, "Ramp to level 2 is narrow."));

        location(u, "LOC_SAMPLE_03", "PNB01", "Prahlad Nagar Business Park", "Corporate Road, Prahlad Nagar",
                23.0120, 72.5110, 45, 15, 80, 12, "07:00", "22:00",
                new String[]{Constants.AMENITY_CCTV, Constants.AMENITY_EV},
                review("Vikram Iyer", 4, "Good for office hours, fills up by 10 am."),
                review("Ananya Gupta", 4, "Friendly staff and clear signs."));

        location(u, "LOC_SAMPLE_04", "KST01", "Kalupur Station Parking", "Kalupur Railway Station, Gate 2",
                23.0270, 72.6010, 30, 10, 150, 0, "00:00", "23:59",
                new String[]{Constants.AMENITY_CCTV, "Security Guard"},
                review("Mohit Verma", 4, "Cheap and very close to the platforms."),
                review("Fatima Khan", 3, "Always full at train times – book ahead."),
                review("Sanjay Pandya", 4, "Safe for overnight parking."));

        location(u, "LOC_SAMPLE_05", "NVM01", "Navrangpura Market Parking", "CG Road, Navrangpura",
                23.0360, 72.5610, 35, 10, 40, 9, "09:00", "22:00",
                new String[]{Constants.AMENITY_COVERED},
                review("Pooja Trivedi", 4, "Perfect for CG Road shopping."),
                review("Harsh Modi", 3, "Small slots for SUVs."));

        location(u, "LOC_SAMPLE_06", "SCV01", "Science City Visitor Parking", "Science City Road, Sola",
                23.0790, 72.4940, 30, 10, 200, 164, "09:00", "20:00",
                new String[]{Constants.AMENITY_CCTV, Constants.AMENITY_EV, "Wheelchair Access"},
                review("Riya Bhatt", 5, "Huge, never full, and close to the entrance."),
                review("Aditya Nair", 5, "Wheelchair access made the visit easy for my father."),
                review("Meera Kapoor", 4, "Good shade near the trees."));

        location(u, "LOC_SAMPLE_07", "LGB01", "Law Garden Two-Wheeler Stand", "Law Garden, Ellisbridge",
                23.0270, 72.5560, 0, 10, 80, 31, "08:00", "23:00",
                new String[]{Constants.AMENITY_CCTV},
                review("Karan Solanki", 4, "Best spot for the night market on a bike."),
                review("Nidhi Parmar", 4, "Cheap and attendants are helpful."));

        location(u, "LOC_SAMPLE_08", "APT01", "Airport Premium Car Parking", "Hansol, near SVPI Airport",
                23.0730, 72.6260, 80, 0, 300, 88, "00:00", "23:59",
                new String[]{Constants.AMENITY_CCTV, Constants.AMENITY_COVERED, Constants.AMENITY_EV, "Security Guard"},
                review("Deepak Jain", 5, "Left my car for 4 days, came back spotless."),
                review("Shreya Reddy", 4, "Shuttle to terminal every 15 minutes."),
                review("Amit Chauhan", 4, "Expensive but worth it for long trips."));

        location(u, "LOC_SAMPLE_09", "MMP01", "Maninagar Metro Parking", "Near Maninagar Metro Station",
                22.9960, 72.6030, 25, 8, 60, 18, "06:00", "23:00",
                new String[]{Constants.AMENITY_CCTV},
                review("Tanvi Dave", 4, "Park and take the metro – saves a lot of time."),
                review("Rahul Thakkar", 3, "Exit gets slow in the evening."));

        location(u, "LOC_SAMPLE_10", "VLP01", "Vastrapur Lake Parking", "Vastrapur Lake Road",
                23.0400, 72.5290, 20, 10, 50, 27, "06:00", "22:00",
                new String[]{Constants.AMENITY_COVERED, "Wheelchair Access"},
                review("Isha Pandey", 5, "Cheapest car parking near the lake."),
                review("Nikhil Rana", 4, "Good for evening walks."),
                review("Zoya Mirza", 4, "Clean and well marked."));

        // Promotion (information only – no fake discount codes)
        Map<String, Object> promo = new HashMap<>();
        promo.put("title", "Book ahead, park stress-free");
        promo.put("message", "Reserve your slot before you leave home and walk straight in with your QR ticket.");
        promo.put("active", true);
        promo.put("priority", 1);
        u.put(Constants.NODE_PROMOTIONS + "/PROMO_SAMPLE_01", promo);

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
        u.put(Constants.NODE_BOOKINGS + "/BK_SAMPLE_" + me.getUid(), booking);

        // A notification for my account
        Map<String, Object> note = new HashMap<>();
        note.put("title", "Booking confirmed");
        note.put("message", "Slot A-07 at SG Highway Central Parking is reserved for you.");
        note.put("type", "BOOKING");
        note.put("isRead", false);
        note.put("createdAt", ServerValue.TIMESTAMP);
        u.put(Constants.NODE_NOTIFICATIONS + "/" + me.getUid() + "/NTF_SAMPLE_01", note);

        FirebaseDatabase.getInstance().getReference().updateChildren(u)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    // ------------------------------------------------------------------ helpers

    /** A sample review (name, 1–5 stars, comment). */
    private static Object[] review(String name, int stars, String comment) {
        return new Object[]{name, stars, comment};
    }

    private static void location(Map<String, Object> u, String id, String code, String name, String address,
                                 double lat, double lng, double carRate, double bikeRate,
                                 int total, int available, String open, String close,
                                 String[] amenities, Object[]... reviews) {
        // Reviews first, so the location's rating can be calculated from them
        long now = System.currentTimeMillis();
        int sum = 0;
        for (int i = 0; i < reviews.length; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("userName", reviews[i][0]);
            r.put("rating", reviews[i][1]);
            r.put("comment", reviews[i][2]);
            r.put("createdAt", now - (i * 3L + 1) * DAY);     // 1, 4, 7 … days ago
            u.put(Constants.NODE_REVIEWS + "/" + id + "/sample_reviewer_" + (i + 1), r);
            sum += (int) reviews[i][1];
        }
        double avg = reviews.length == 0 ? 0 : Math.round(sum * 10.0 / reviews.length) / 10.0;

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
        m.put("avgRating", avg);
        m.put("reviewCount", reviews.length);
        m.put("active", true);
        m.put("createdAt", ServerValue.TIMESTAMP);
        u.put(Constants.NODE_LOCATIONS + "/" + id, m);
    }
}

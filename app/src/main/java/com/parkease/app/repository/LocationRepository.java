package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.database.RecentLocationDao;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parking locations (Firebase  locations/ ) + "recently visited" history (SQLite).
 *
 * observe…() methods keep listening: whenever an admin changes a location or a slot is
 * booked (availableSlots changes), the screen receives the new list automatically.
 * Thanks to Firebase offline persistence, the last downloaded list is also available
 * without internet.
 */
public class LocationRepository {

    private final Context context;
    private final RecentLocationDao recentDao;

    public LocationRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.recentDao = new RecentLocationDao(this.context);
    }

    /** All ACTIVE locations, updated live. */
    @NonNull
    public Subscription observeActiveLocations(@NonNull DataCallback<List<ParkingLocation>> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(
                FirebaseDatabase.getInstance().getReference(Constants.NODE_LOCATIONS),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<ParkingLocation> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            ParkingLocation location = child.getValue(ParkingLocation.class);
                            if (location != null && location.isActive()) {
                                location.setId(child.getKey());
                                list.add(location);
                            }
                        }
                        callback.onSuccess(list);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
                    }
                });
    }

    /** One location, updated live (e.g. its free-slot count). Null if it was deleted. */
    @NonNull
    public Subscription observeLocation(@NonNull String locationId,
                                        @NonNull DataCallback<ParkingLocation> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(
                FirebaseDatabase.getInstance().getReference(Constants.NODE_LOCATIONS).child(locationId),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        ParkingLocation location = snapshot.getValue(ParkingLocation.class);
                        if (location != null) location.setId(snapshot.getKey());
                        callback.onSuccess(location);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
                    }
                });
    }

    // ------------------------------------------------------------------ recently visited (SQLite)

    /** Remembers that the user opened this location (newest first, max 10). */
    public void recordVisit(@NonNull ParkingLocation location) {
        recentDao.upsert(location);
    }

    /** Recently opened locations from SQLite – available instantly, even offline. */
    @NonNull
    public List<ParkingLocation> getRecentlyVisited() {
        return recentDao.getRecent(10);
    }
}

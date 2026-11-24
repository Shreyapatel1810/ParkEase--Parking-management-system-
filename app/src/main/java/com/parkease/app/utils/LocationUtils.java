package com.parkease.app.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.parkease.app.models.ParkingLocation;

import java.util.Comparator;
import java.util.List;

/**
 * "Nearby" support using the phone's last known position (approximate location only).
 * If the user doesn't allow location, the app still works: lists are sorted by
 * availability and rating instead of distance.
 */
public final class LocationUtils {

    private LocationUtils() { }

    public static boolean hasPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** The most recent position any app got on this phone, or null. No battery cost. */
    @Nullable
    public static Location getLastKnown(@NonNull Context context) {
        if (!hasPermission(context)) return null;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {
            return null;
        }
        return best;
    }

    /** Distance in metres, or -1 if unknown. */
    public static float distanceTo(@Nullable Location me, @NonNull ParkingLocation place) {
        if (me == null || !place.hasCoordinates()) return -1;
        float[] result = new float[1];
        Location.distanceBetween(me.getLatitude(), me.getLongitude(),
                place.getLatitude(), place.getLongitude(), result);
        return result[0];
    }

    /** Nearest first if we know where the user is; otherwise most free slots, then best rated. */
    public static void sortForUser(@NonNull List<ParkingLocation> list, @Nullable Location me) {
        if (me != null) {
            list.sort(Comparator.comparingDouble(p -> {
                float d = distanceTo(me, p);
                return d < 0 ? Float.MAX_VALUE : d;
            }));
        } else {
            list.sort((a, b) -> {
                int byFree = Integer.compare(b.getAvailableSlots(), a.getAvailableSlots());
                return byFree != 0 ? byFree : Double.compare(b.getAvgRating(), a.getAvgRating());
            });
        }
    }
}

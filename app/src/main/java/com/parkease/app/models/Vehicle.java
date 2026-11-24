package com.parkease.app.models;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.parkease.app.R;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.VehicleNumberUtils;

/**
 * A customer's vehicle, stored at  vehicles/{userId}/{vehicleId}.
 *
 * <pre>
 * vehicles/{uid}/{vehicleId}: { userId, registrationNumber: "GJ01AB1234",
 *                               vehicleType: "CAR" | "BIKE" | "SUV", nickname?, createdAt, updatedAt }
 * </pre>
 * The registration number is stored without spaces in capitals, so "gj 01 ab-1234" and
 * "GJ01AB1234" are the same vehicle (used for duplicate checks).
 */
@IgnoreExtraProperties
public class Vehicle {

    @Exclude private String id;          // vehicleId = the Firebase key

    private String userId;
    private String registrationNumber;
    private String vehicleType;
    private String nickname;
    private long createdAt;
    private long updatedAt;

    /** Required by Firebase. */
    public Vehicle() { }

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    // ------------------------------------------------------------------ helpers (not saved)

    /** Which kind of parking slot this vehicle needs: bikes → BIKE slots, cars & SUVs → CAR slots. */
    @Exclude
    @NonNull
    public String getSlotType() {
        return Constants.VEHICLE_BIKE.equals(vehicleType) ? Constants.VEHICLE_BIKE : Constants.VEHICLE_CAR;
    }

    /** "GJ 01 AB 1234" */
    @Exclude
    @NonNull
    public String getDisplayNumber() {
        return VehicleNumberUtils.format(registrationNumber);
    }

    @Exclude
    public boolean hasNickname() {
        return nickname != null && !nickname.trim().isEmpty();
    }

    @Exclude
    @DrawableRes
    public int getIcon() {
        return Constants.VEHICLE_BIKE.equals(vehicleType) ? R.drawable.ic_bike : R.drawable.ic_car;
    }

    @Exclude
    @StringRes
    public int getTypeLabel() {
        if (Constants.VEHICLE_BIKE.equals(vehicleType)) return R.string.vehicle_bike;
        if (Constants.VEHICLE_SUV.equals(vehicleType)) return R.string.vehicle_suv;
        return R.string.vehicle_car;
    }
}

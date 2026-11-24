package com.parkease.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.parkease.app.utils.Constants;

/**
 * A parking reservation, stored in Firebase at  bookings/{bookingId}.
 *
 * <pre>
 * bookings/{id}: { bookingCode, userId, userName, locationId, locationName, slotId,
 *                  slotNumber, vehicleNumber, vehicleType, startTime, endTime,
 *                  status, amount, createdAt }
 * </pre>
 * status: PENDING_PAYMENT → CONFIRMED → ACTIVE (car parked) → COMPLETED
 *         (or CANCELLED / EXPIRED). Times are milliseconds since 1970.
 */
@IgnoreExtraProperties
public class Booking {

    @Exclude
    private String id;

    private String bookingCode;    // short human-readable code, e.g. "PE-7K3D9Q"
    private String userId;
    private String userName;
    private String locationId;
    private String locationName;
    private String slotId;         // e.g. "PMC01-G-A-012"
    private String slotNumber;     // e.g. "A-12"
    private String vehicleNumber;
    private String vehicleType;    // CAR / BIKE
    private long startTime;
    private long endTime;
    private String status;
    private double amount;
    private long holdUntil;        // PENDING_PAYMENT only: slot is held until this time
    private String vehicleId;      // which of the customer's vehicles (Step 10)
    private long confirmedAt;
    private long cancelledAt;
    private double refundAmount;
    private long createdAt;

    /** Required by Firebase. */
    public Booking() { }

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getLocationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }

    public String getSlotNumber() { return slotNumber; }
    public void setSlotNumber(String slotNumber) { this.slotNumber = slotNumber; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public long getHoldUntil() { return holdUntil; }
    public void setHoldUntil(long holdUntil) { this.holdUntil = holdUntil; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public long getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }

    public long getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(long cancelledAt) { this.cancelledAt = cancelledAt; }

    public double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    // ------------------------------------------------------------------ helpers (not saved)

    /** Confirmed, parked, or a still-valid hold waiting for confirmation – and not finished yet. */
    @Exclude
    public boolean isUpcoming(long now) {
        boolean liveStatus = Constants.BOOKING_CONFIRMED.equals(status)
                || Constants.BOOKING_ACTIVE.equals(status)
                || isHoldActive(now);
        return liveStatus && endTime > now;
    }

    /** PENDING_PAYMENT and the 10-minute hold hasn't run out. */
    @Exclude
    public boolean isHoldActive(long now) {
        return Constants.BOOKING_PENDING_PAYMENT.equals(status) && holdUntil > now;
    }

    @Exclude
    public boolean isPending() {
        return Constants.BOOKING_PENDING_PAYMENT.equals(status);
    }

    @Exclude
    public boolean isConfirmed() {
        return Constants.BOOKING_CONFIRMED.equals(status);
    }

    /**
     * Should this booking now be marked EXPIRED?
     *  - an unconfirmed hold whose time ran out, or
     *  - a confirmed booking whose car never arrived within the grace period (no-show).
     */
    @Exclude
    public boolean shouldExpire(long now) {
        if (isPending()) return holdUntil <= now;
        return isConfirmed() && startTime + Constants.GRACE_PERIOD_MINUTES * 60_000L < now;
    }

    /** Duration in minutes. */
    @Exclude
    public int getDurationMinutes() {
        return (int) ((endTime - startTime) / 60_000L);
    }

    @Exclude
    public boolean isCancelled() {
        return Constants.BOOKING_CANCELLED.equals(status);
    }

    /** Car is in the slot right now. */
    @Exclude
    public boolean isActive() {
        return Constants.BOOKING_ACTIVE.equals(status);
    }
}

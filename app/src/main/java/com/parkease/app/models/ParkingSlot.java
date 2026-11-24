package com.parkease.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.parkease.app.utils.Constants;

/**
 * One parking space, stored at  slots/{locationId}/{slotId}.
 *
 * <pre>
 * slots/LOC_SAMPLE_02/SGH01-G-A-007: {
 *     slotNumber: "A-07", floor: "G", zone: "A",
 *     vehicleType: "CAR", status: "AVAILABLE", pricePerHour: 50
 * }
 * </pre>
 * The slot ID (the key) is unique: LOCATIONCODE-FLOOR-ZONE-NUMBER.
 * "status" is the CURRENT state set by the admin / gate (AVAILABLE, RESERVED, OCCUPIED,
 * MAINTENANCE). Future reservations are kept separately in  slotSchedule/  (see SlotRepository).
 */
@IgnoreExtraProperties
public class ParkingSlot {

    @Exclude private String id;          // e.g. "SGH01-G-A-007"
    @Exclude private String locationId;  // the parent key

    private String slotNumber;           // short label shown in the grid, e.g. "A-07"
    private String floor;                // "G", "1", "2"…
    private String zone;                 // "A", "B"…
    private String vehicleType;          // CAR / BIKE
    private String status;               // AVAILABLE / RESERVED / OCCUPIED / MAINTENANCE
    private double pricePerHour;

    /** Required by Firebase. */
    public ParkingSlot() { }

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    @Exclude public String getLocationId() { return locationId; }
    @Exclude public void setLocationId(String locationId) { this.locationId = locationId; }

    public String getSlotNumber() { return slotNumber; }
    public void setSlotNumber(String slotNumber) { this.slotNumber = slotNumber; }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getPricePerHour() { return pricePerHour; }
    public void setPricePerHour(double pricePerHour) { this.pricePerHour = pricePerHour; }

    @Exclude
    public boolean isMaintenance() {
        return Constants.SLOT_MAINTENANCE.equals(status);
    }

    /** "Ground floor" / "Floor 1" style label. */
    @Exclude
    public String getFloorLabel() {
        return floor == null || "G".equalsIgnoreCase(floor) ? "Ground floor" : "Floor " + floor;
    }
}

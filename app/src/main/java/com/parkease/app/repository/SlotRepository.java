package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.models.Booking;
import com.parkease.app.models.ParkingLocation;
import com.parkease.app.models.ParkingSlot;
import com.parkease.app.models.ScheduleBlock;
import com.parkease.app.models.TimeWindow;
import com.parkease.app.models.Vehicle;
import com.parkease.app.ui.components.ParkingSlotView;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.NetworkUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parking slots and slot reservations.
 *
 * <h3>How double booking is prevented (on the server)</h3>
 * Each slot's day is split into 30-minute blocks:
 * <pre>slotSchedule/{locationId}/{yyyyMMdd}/{slotId}/{HHmm} = { bookingId, userId, status, holdUntil }</pre>
 * Reserving 10:00–12:00 writes the blocks 1000, 1030, 1100, 1130 AND the pending booking in
 * ONE atomic multi-path update. The Firebase security rules (database.rules.json) run on
 * Google's servers and REJECT the whole update if any block already belongs to someone else
 * (unless it is an expired hold), if the slot is under maintenance, or if the data is wrong.
 * So two people tapping "Reserve" at the same moment can never both get the slot –
 * the first write wins, the second gets "slot just taken". The app cannot bypass this.
 */
public class SlotRepository {

    private final Context context;

    public SlotRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    // ================================================================== reading (live)

    /** All slots of a location, updated live (statuses change when cars enter/leave). */
    @NonNull
    public Subscription observeSlots(@NonNull String locationId, @NonNull DataCallback<List<ParkingSlot>> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(db().child(Constants.NODE_SLOTS).child(locationId), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ParkingSlot> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ParkingSlot slot = child.getValue(ParkingSlot.class);
                    if (slot != null) {
                        slot.setId(child.getKey());
                        slot.setLocationId(locationId);
                        list.add(slot);
                    }
                }
                // Firebase keys are sorted A→Z, which already groups by floor and zone
                callback.onSuccess(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
            }
        });
    }

    /** Taken blocks for one day of a location: slotId → (HHmm → block). Updated live. */
    @NonNull
    public Subscription observeSchedule(@NonNull String locationId, @NonNull String dateKey,
                                        @NonNull DataCallback<Map<String, Map<String, ScheduleBlock>>> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(scheduleRef(locationId, dateKey), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callback.onSuccess(parseSchedule(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
            }
        });
    }

    // ================================================================== status for a time window

    /**
     * What a slot looks like for the chosen time:
     * <ul>
     *   <li>MAINTENANCE – blocked by the admin</li>
     *   <li>RESERVED    – someone holds or booked at least one 30-minute block in the window</li>
     *   <li>OCCUPIED / RESERVED – the slot's CURRENT status, if the window starts within 2 hours
     *       (a car parked now may still be there)</li>
     *   <li>AVAILABLE   – free for the whole window</li>
     * </ul>
     */
    @NonNull
    public static ParkingSlotView.Status statusFor(@NonNull ParkingSlot slot,
                                                   @Nullable Map<String, ScheduleBlock> blocks,
                                                   @NonNull TimeWindow window) {
        if (slot.isMaintenance()) return ParkingSlotView.Status.MAINTENANCE;
        long now = System.currentTimeMillis();
        if (blocks != null) {
            for (String key : window.blockKeys()) {
                ScheduleBlock b = blocks.get(key);
                if (b != null && b.isActive(now)) return ParkingSlotView.Status.RESERVED;
            }
        }
        if (window.startsWithin(120)) {
            if (Constants.SLOT_OCCUPIED.equals(slot.getStatus())) return ParkingSlotView.Status.OCCUPIED;
            if (Constants.SLOT_RESERVED.equals(slot.getStatus())) return ParkingSlotView.Status.RESERVED;
        }
        return ParkingSlotView.Status.AVAILABLE;
    }

    // ================================================================== reserving

    /**
     * Reserves a slot for the window (a 10-minute HOLD while the customer pays).
     * 1. Quick check of the latest schedule (friendly message if already taken).
     * 2. Atomic write of all blocks + a PENDING_PAYMENT booking – the security rules on the
     *    server make the final decision, so conflicting bookings are impossible.
     */
    public void holdSlot(@NonNull ParkingLocation location, @NonNull ParkingSlot slot,
                         @NonNull TimeWindow window, @NonNull Vehicle vehicle,
                         @NonNull DataCallback<Booking> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return;
        }
        if (!NetworkUtils.isOnline(context)) {
            callback.onError(context.getString(R.string.err_no_internet));   // never reserve offline
            return;
        }
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            callback.onError(context.getString(R.string.err_session_expired));
            return;
        }
        int timeError = window.validate(location.getOpenTime(), location.getCloseTime());
        if (timeError != 0) {
            callback.onError(context.getString(timeError));
            return;
        }
        if (slot.isMaintenance()) {
            callback.onError(context.getString(R.string.slot_err_maintenance));
            return;
        }
        // A bike can't take a car slot (and cars/SUVs can't take bike slots)
        if (!vehicle.getSlotType().equals(slot.getVehicleType())) {
            callback.onError(context.getString(R.string.err_generic));
            return;
        }

        String locationId = location.getId();
        // Step 1: read the very latest schedule from the server
        scheduleRef(locationId, window.dateKey()).child(slot.getId()).get()
                .addOnSuccessListener(snapshot -> {
                    long now = System.currentTimeMillis();
                    for (String key : window.blockKeys()) {
                        ScheduleBlock b = snapshot.child(key).getValue(ScheduleBlock.class);
                        if (b != null && b.isActive(now)) {
                            callback.onError(context.getString(R.string.slot_err_taken));
                            return;
                        }
                    }
                    // Step 2: atomic write, verified by the server rules
                    writeHold(me, location, slot, window, vehicle, callback);
                })
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    private void writeHold(@NonNull FirebaseUser me, @NonNull ParkingLocation location,
                           @NonNull ParkingSlot slot, @NonNull TimeWindow window,
                           @NonNull Vehicle vehicle, @NonNull DataCallback<Booking> callback) {
        String bookingId = db().child(Constants.NODE_BOOKINGS).push().getKey();
        if (bookingId == null) {
            callback.onError(context.getString(R.string.err_generic));
            return;
        }
        long holdUntil = System.currentTimeMillis() + Constants.HOLD_MINUTES * 60_000L;
        double amount = Math.round(slot.getPricePerHour() * window.getHours() * 100) / 100.0;
        String code = newBookingCode();

        Map<String, Object> updates = new HashMap<>();

        // The 30-minute blocks
        String base = Constants.NODE_SLOT_SCHEDULE + "/" + location.getId() + "/" + window.dateKey()
                + "/" + slot.getId() + "/";
        for (String key : window.blockKeys()) {
            Map<String, Object> block = new HashMap<>();
            block.put("bookingId", bookingId);
            block.put("userId", me.getUid());
            block.put("status", Constants.SCHEDULE_HOLD);
            block.put("holdUntil", holdUntil);
            updates.put(base + key, block);
        }

        // The pending booking (payment confirms it in the next step).
        // The rules check that vehicleId really is one of MY vehicles with this number.
        Map<String, Object> booking = new HashMap<>();
        booking.put("vehicleId", vehicle.getId());
        booking.put("vehicleNumber", vehicle.getRegistrationNumber());
        booking.put("bookingCode", code);
        booking.put("userId", me.getUid());
        booking.put("userName", me.getDisplayName() == null ? "" : me.getDisplayName());
        booking.put("locationId", location.getId());
        booking.put("locationName", location.getName());
        booking.put("slotId", slot.getId());
        booking.put("slotNumber", slot.getSlotNumber());
        booking.put("vehicleType", vehicle.getVehicleType());
        booking.put("startTime", window.startMillis());
        booking.put("endTime", window.endMillis());
        booking.put("status", Constants.BOOKING_PENDING_PAYMENT);
        booking.put("amount", amount);
        booking.put("holdUntil", holdUntil);
        booking.put("createdAt", ServerValue.TIMESTAMP);
        updates.put(Constants.NODE_BOOKINGS + "/" + bookingId, booking);

        db().updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    Booking b = new Booking();
                    b.setId(bookingId);
                    b.setBookingCode(code);
                    b.setUserId(me.getUid());
                    b.setLocationId(location.getId());
                    b.setLocationName(location.getName());
                    b.setSlotId(slot.getId());
                    b.setSlotNumber(slot.getSlotNumber());
                    b.setVehicleType(vehicle.getVehicleType());
                    b.setVehicleNumber(vehicle.getRegistrationNumber());
                    b.setStartTime(window.startMillis());
                    b.setEndTime(window.endMillis());
                    b.setStatus(Constants.BOOKING_PENDING_PAYMENT);
                    b.setAmount(amount);
                    b.setHoldUntil(holdUntil);
                    callback.onSuccess(b);
                })
                .addOnFailureListener(e -> {
                    // The server refused: almost always because someone else got the slot first
                    String msg = e.getMessage() != null && e.getMessage().toLowerCase().contains("permission")
                            ? context.getString(R.string.slot_err_taken)
                            : FirebaseErrorMapper.fromDatabase(context, e);
                    callback.onError(msg);
                });
    }

    /** Cancels an unpaid hold: frees the blocks and deletes the pending booking. */
    public void releaseHold(@NonNull Booking booking, @NonNull TimeWindow window, @NonNull DataCallback<Void> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        String base = Constants.NODE_SLOT_SCHEDULE + "/" + booking.getLocationId() + "/" + window.dateKey()
                + "/" + booking.getSlotId() + "/";
        for (String key : window.blockKeys()) updates.put(base + key, null);
        updates.put(Constants.NODE_BOOKINGS + "/" + booking.getId(), null);
        db().updateChildren(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    // ================================================================== helpers

    @NonNull
    private static Map<String, Map<String, ScheduleBlock>> parseSchedule(@NonNull DataSnapshot day) {
        Map<String, Map<String, ScheduleBlock>> result = new HashMap<>();
        for (DataSnapshot slotNode : day.getChildren()) {
            Map<String, ScheduleBlock> blocks = new HashMap<>();
            for (DataSnapshot blockNode : slotNode.getChildren()) {
                ScheduleBlock b = blockNode.getValue(ScheduleBlock.class);
                if (b != null) blocks.put(blockNode.getKey(), b);
            }
            result.put(slotNode.getKey(), blocks);
        }
        return result;
    }

    private DatabaseReference db() {
        return FirebaseDatabase.getInstance().getReference();
    }

    private DatabaseReference scheduleRef(@NonNull String locationId, @NonNull String dateKey) {
        return db().child(Constants.NODE_SLOT_SCHEDULE).child(locationId).child(dateKey);
    }

    /** Short code people can read out at the gate, e.g. "PE-7K3D9Q" (no 0/O or 1/I). */
    @NonNull
    private static String newBookingCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("PE-");
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}

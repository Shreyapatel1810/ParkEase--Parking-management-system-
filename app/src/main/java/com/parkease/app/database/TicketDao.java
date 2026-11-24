package com.parkease.app.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.database.DBContract.Tickets;
import com.parkease.app.models.Booking;
import com.parkease.app.utils.TicketCodec;

/**
 * STEP 13 – SQL for the tickets table: a copy of each QR ticket on the phone, so the
 * ticket still opens at the parking gate when there is no internet (basements!).
 * It is only a display copy – the gate scanner always checks Firebase.
 */
public class TicketDao {

    private final DBHelper helper;

    public TicketDao(@NonNull Context context) {
        this.helper = DBHelper.getInstance(context);
    }

    /** Saves / refreshes the ticket of a confirmed or active booking. */
    public void save(@NonNull Booking b, @Nullable String customerName) {
        if (b.getId() == null || b.getTicketToken() == null) return;
        ContentValues v = new ContentValues();
        v.put(Tickets.BOOKING_ID, b.getId());
        v.put(Tickets.BOOKING_CODE, b.getBookingCode());
        v.put(Tickets.LOCATION_NAME, b.getLocationName());
        v.put(Tickets.SLOT_ID, b.getSlotId());
        v.put(Tickets.SLOT_NUMBER, b.getSlotNumber());
        v.put(Tickets.VEHICLE_NUMBER, b.getVehicleNumber());
        v.put(Tickets.VEHICLE_TYPE, b.getVehicleType());
        v.put(Tickets.START_TIME, b.getStartTime());
        v.put(Tickets.END_TIME, b.getEndTime());
        v.put(Tickets.AMOUNT, b.getAmount());
        v.put(Tickets.STATUS, b.getStatus());
        v.put(Tickets.QR_CONTENT, TicketCodec.encode(b.getId(), b.getTicketToken()));
        v.put(Tickets.CUSTOMER_NAME, customerName);
        v.put(Tickets.ENTRY_TIME, b.getEntryTime());
        v.put(Tickets.EXIT_TIME, b.getExitTime());
        v.put(Tickets.SYNCED_AT, System.currentTimeMillis());
        helper.getWritableDatabase().insertWithOnConflict(Tickets.TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** The saved ticket as a Booking (ticketToken filled from the QR text), or null. */
    @Nullable
    public Booking get(@NonNull String bookingId) {
        try (Cursor c = helper.getReadableDatabase().query(Tickets.TABLE, null,
                Tickets.BOOKING_ID + " = ?", new String[]{bookingId}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            Booking b = new Booking();
            b.setId(bookingId);
            b.setBookingCode(str(c, Tickets.BOOKING_CODE));
            b.setLocationName(str(c, Tickets.LOCATION_NAME));
            b.setSlotId(str(c, Tickets.SLOT_ID));
            b.setSlotNumber(str(c, Tickets.SLOT_NUMBER));
            b.setVehicleNumber(str(c, Tickets.VEHICLE_NUMBER));
            b.setVehicleType(str(c, Tickets.VEHICLE_TYPE));
            b.setStartTime(c.getLong(c.getColumnIndexOrThrow(Tickets.START_TIME)));
            b.setEndTime(c.getLong(c.getColumnIndexOrThrow(Tickets.END_TIME)));
            b.setAmount(c.getDouble(c.getColumnIndexOrThrow(Tickets.AMOUNT)));
            b.setStatus(str(c, Tickets.STATUS));
            b.setUserName(str(c, Tickets.CUSTOMER_NAME));
            b.setEntryTime(c.getLong(c.getColumnIndexOrThrow(Tickets.ENTRY_TIME)));
            b.setExitTime(c.getLong(c.getColumnIndexOrThrow(Tickets.EXIT_TIME)));
            TicketCodec.Scanned qr = TicketCodec.parse(str(c, Tickets.QR_CONTENT));
            if (qr != null) b.setTicketToken(qr.token);
            return b;
        }
    }

    public void delete(@NonNull String bookingId) {
        helper.getWritableDatabase().delete(Tickets.TABLE, Tickets.BOOKING_ID + " = ?", new String[]{bookingId});
    }

    @Nullable
    private static String str(@NonNull Cursor c, @NonNull String column) {
        return c.getString(c.getColumnIndexOrThrow(column));
    }
}

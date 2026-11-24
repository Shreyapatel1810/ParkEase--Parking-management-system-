package com.parkease.app.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.parkease.app.database.DBContract.RecentLocations;
import com.parkease.app.models.ParkingLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL for the recent_locations table ("Recently visited" on the home screen).
 * INSERT/UPDATE: upsert()   READ: getRecent()   DELETE: trim() keeps only the newest rows.
 */
public class RecentLocationDao {

    private static final int MAX_ROWS = 20;

    private final DBHelper helper;

    public RecentLocationDao(@NonNull Context context) {
        this.helper = DBHelper.getInstance(context);
    }

    /** Saves (or moves to the top) a location the user just opened. */
    public void upsert(@NonNull ParkingLocation location) {
        if (location.getId() == null) return;
        ContentValues v = new ContentValues();
        v.put(RecentLocations.LOCATION_ID, location.getId());
        v.put(RecentLocations.NAME, location.getName() == null ? "" : location.getName());
        v.put(RecentLocations.ADDRESS, location.getAddress());
        v.put(RecentLocations.CITY, location.getCity());
        v.put(RecentLocations.IMAGE_URL, location.getImageUrl());
        v.put(RecentLocations.VIEWED_AT, System.currentTimeMillis());
        SQLiteDatabase db = helper.getWritableDatabase();
        db.insertWithOnConflict(RecentLocations.TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        trim(db);
    }

    /** Newest first. Only basic fields are stored; live details come from Firebase. */
    @NonNull
    public List<ParkingLocation> getRecent(int limit) {
        List<ParkingLocation> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(RecentLocations.TABLE, null, null, null,
                null, null, RecentLocations.VIEWED_AT + " DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                ParkingLocation p = new ParkingLocation();
                p.setId(c.getString(c.getColumnIndexOrThrow(RecentLocations.LOCATION_ID)));
                p.setName(c.getString(c.getColumnIndexOrThrow(RecentLocations.NAME)));
                p.setAddress(c.getString(c.getColumnIndexOrThrow(RecentLocations.ADDRESS)));
                p.setCity(c.getString(c.getColumnIndexOrThrow(RecentLocations.CITY)));
                p.setImageUrl(c.getString(c.getColumnIndexOrThrow(RecentLocations.IMAGE_URL)));
                list.add(p);
            }
        }
        return list;
    }

    /** Deletes one location from history (e.g. if it no longer exists in Firebase). */
    public void delete(@NonNull String locationId) {
        helper.getWritableDatabase().delete(RecentLocations.TABLE,
                RecentLocations.LOCATION_ID + " = ?", new String[]{locationId});
    }

    /** Keeps only the newest MAX_ROWS entries. */
    private void trim(@NonNull SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + RecentLocations.TABLE + " WHERE " + RecentLocations.LOCATION_ID
                + " NOT IN (SELECT " + RecentLocations.LOCATION_ID + " FROM " + RecentLocations.TABLE
                + " ORDER BY " + RecentLocations.VIEWED_AT + " DESC LIMIT " + MAX_ROWS + ")");
    }
}

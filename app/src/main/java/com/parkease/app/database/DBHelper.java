package com.parkease.app.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.parkease.app.database.DBContract.Favourites;
import com.parkease.app.database.DBContract.Notifications;
import com.parkease.app.database.DBContract.RecentLocations;
import com.parkease.app.database.DBContract.Reminders;
import com.parkease.app.database.DBContract.SearchHistory;
import com.parkease.app.database.DBContract.Tickets;
import com.parkease.app.database.DBContract.UserProfile;

/**
 * Creates and upgrades the local SQLite database (parkease_local.db).
 * Use DBHelper.getInstance(context) everywhere so only one connection is open.
 */
public class DBHelper extends SQLiteOpenHelper {

    private static DBHelper instance;

    public static synchronized DBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DBHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DBHelper(Context context) {
        super(context, DBContract.DATABASE_NAME, null, DBContract.DATABASE_VERSION);
    }

    private static final String CREATE_TICKETS =
            "CREATE TABLE " + Tickets.TABLE + " ("
                    + Tickets.BOOKING_ID + " TEXT PRIMARY KEY, "
                    + Tickets.BOOKING_CODE + " TEXT, "
                    + Tickets.LOCATION_NAME + " TEXT, "
                    + Tickets.LOCATION_ADDRESS + " TEXT, "
                    + Tickets.SLOT_ID + " TEXT, "
                    + Tickets.VEHICLE_NUMBER + " TEXT, "
                    + Tickets.START_TIME + " INTEGER, "
                    + Tickets.END_TIME + " INTEGER, "
                    + Tickets.AMOUNT + " REAL, "
                    + Tickets.STATUS + " TEXT, "
                    + Tickets.QR_CONTENT + " TEXT, "
                    + Tickets.CUSTOMER_NAME + " TEXT, "
                    + Tickets.SLOT_NUMBER + " TEXT, "
                    + Tickets.VEHICLE_TYPE + " TEXT, "
                    + Tickets.ENTRY_TIME + " INTEGER, "
                    + Tickets.EXIT_TIME + " INTEGER, "
                    + Tickets.SYNCED_AT + " INTEGER)";

    private static final String CREATE_SEARCH_HISTORY =
            "CREATE TABLE " + SearchHistory.TABLE + " ("
                    + SearchHistory.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + SearchHistory.QUERY + " TEXT UNIQUE, "
                    + SearchHistory.SEARCHED_AT + " INTEGER)";

    private static final String CREATE_NOTIFICATIONS =
            "CREATE TABLE " + Notifications.TABLE + " ("
                    + Notifications.ID + " TEXT PRIMARY KEY, "
                    + Notifications.TITLE + " TEXT, "
                    + Notifications.MESSAGE + " TEXT, "
                    + Notifications.TYPE + " TEXT, "
                    + Notifications.IS_READ + " INTEGER DEFAULT 0, "
                    + Notifications.CREATED_AT + " INTEGER)";

    private static final String CREATE_FAVOURITES =
            "CREATE TABLE " + Favourites.TABLE + " ("
                    + Favourites.LOCATION_ID + " TEXT PRIMARY KEY, "
                    + Favourites.NAME + " TEXT, "
                    + Favourites.ADDRESS + " TEXT, "
                    + Favourites.IMAGE_URL + " TEXT, "
                    + Favourites.CAR_RATE + " REAL, "
                    + Favourites.AVG_RATING + " REAL, "
                    + Favourites.ADDED_AT + " INTEGER)";

    private static final String CREATE_USER_PROFILE =
            "CREATE TABLE " + UserProfile.TABLE + " ("
                    + UserProfile.UID + " TEXT PRIMARY KEY, "
                    + UserProfile.FULL_NAME + " TEXT NOT NULL, "
                    + UserProfile.EMAIL + " TEXT NOT NULL, "
                    + UserProfile.PHONE + " TEXT, "
                    + UserProfile.ROLE + " TEXT NOT NULL, "
                    + UserProfile.BLOCKED + " INTEGER DEFAULT 0, "
                    + UserProfile.PHOTO + " TEXT, "
                    + UserProfile.CREATED_AT + " INTEGER, "
                    + UserProfile.UPDATED_AT + " INTEGER, "
                    + UserProfile.SYNCED_AT + " INTEGER)";

    private static final String CREATE_RECENT_LOCATIONS =
            "CREATE TABLE " + RecentLocations.TABLE + " ("
                    + RecentLocations.LOCATION_ID + " TEXT PRIMARY KEY, "
                    + RecentLocations.NAME + " TEXT NOT NULL, "
                    + RecentLocations.ADDRESS + " TEXT, "
                    + RecentLocations.CITY + " TEXT, "
                    + RecentLocations.IMAGE_URL + " TEXT, "
                    + RecentLocations.VIEWED_AT + " INTEGER)";

    private static final String CREATE_REMINDERS =
            "CREATE TABLE " + Reminders.TABLE + " ("
                    + Reminders.BOOKING_ID + " TEXT PRIMARY KEY, "
                    + Reminders.TRIGGER_AT + " INTEGER, "
                    + Reminders.REQUEST_CODE + " INTEGER)";

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TICKETS);
        db.execSQL(CREATE_SEARCH_HISTORY);
        db.execSQL(CREATE_NOTIFICATIONS);
        db.execSQL(CREATE_FAVOURITES);
        db.execSQL(CREATE_REMINDERS);
        db.execSQL(CREATE_USER_PROFILE);
        db.execSQL(CREATE_RECENT_LOCATIONS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Local data is only a cache of Firebase, so it is safe to rebuild it.
        db.execSQL("DROP TABLE IF EXISTS " + Tickets.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + SearchHistory.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + Notifications.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + Favourites.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + Reminders.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + UserProfile.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + RecentLocations.TABLE);
        onCreate(db);   // the cache refills from Firebase automatically
    }

    /** Number of ParkEase tables in the database (used by the setup check screen). */
    public int countTables() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' "
                        + "AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Deletes all local data – called on logout. */
    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(Tickets.TABLE, null, null);
        db.delete(SearchHistory.TABLE, null, null);
        db.delete(Notifications.TABLE, null, null);
        db.delete(Favourites.TABLE, null, null);
        db.delete(Reminders.TABLE, null, null);
        db.delete(UserProfile.TABLE, null, null);
        db.delete(RecentLocations.TABLE, null, null);
    }
}

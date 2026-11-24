package com.parkease.app.database;

/**
 * All SQLite table and column names in one place.
 * Using constants avoids typing mistakes in SQL strings.
 */
public final class DBContract {

    private DBContract() { }

    public static final String DATABASE_NAME = "parkease_local.db";
    public static final int DATABASE_VERSION = 1;

    /** Saved booking tickets – the QR ticket opens without internet. */
    public static final class Tickets {
        public static final String TABLE = "tickets";
        public static final String BOOKING_ID = "booking_id";
        public static final String BOOKING_CODE = "booking_code";
        public static final String LOCATION_NAME = "location_name";
        public static final String LOCATION_ADDRESS = "location_address";
        public static final String SLOT_ID = "slot_id";
        public static final String VEHICLE_NUMBER = "vehicle_number";
        public static final String START_TIME = "start_time";
        public static final String END_TIME = "end_time";
        public static final String AMOUNT = "amount";
        public static final String STATUS = "status";
        public static final String QR_CONTENT = "qr_content";
        public static final String SYNCED_AT = "synced_at";
    }

    /** Recent searches on the Search screen. */
    public static final class SearchHistory {
        public static final String TABLE = "search_history";
        public static final String ID = "id";
        public static final String QUERY = "query";
        public static final String SEARCHED_AT = "searched_at";
    }

    /** Local copy of the notification inbox. */
    public static final class Notifications {
        public static final String TABLE = "notifications";
        public static final String ID = "id";
        public static final String TITLE = "title";
        public static final String MESSAGE = "message";
        public static final String TYPE = "type";
        public static final String IS_READ = "is_read";
        public static final String CREATED_AT = "created_at";
    }

    /** Favourite locations cache. */
    public static final class Favourites {
        public static final String TABLE = "favourites";
        public static final String LOCATION_ID = "location_id";
        public static final String NAME = "name";
        public static final String ADDRESS = "address";
        public static final String IMAGE_URL = "image_url";
        public static final String CAR_RATE = "car_rate";
        public static final String AVG_RATING = "avg_rating";
        public static final String ADDED_AT = "added_at";
    }

    /** Scheduled booking reminders (so alarms can be cancelled). */
    public static final class Reminders {
        public static final String TABLE = "reminders";
        public static final String BOOKING_ID = "booking_id";
        public static final String TRIGGER_AT = "trigger_at";
        public static final String REQUEST_CODE = "request_code";
    }
}

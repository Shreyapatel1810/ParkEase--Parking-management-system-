package com.parkease.app.utils;

/**
 * App-wide constants: Firebase node names, status values, roles and intent keys.
 * Always use these instead of typing strings by hand.
 */
public final class Constants {

    private Constants() { }

    // ---------- Firebase Realtime Database nodes ----------
    public static final String NODE_USERS = "users";
    public static final String NODE_VEHICLES = "vehicles";
    public static final String NODE_LOCATIONS = "locations";
    public static final String NODE_SLOTS = "slots";
    public static final String NODE_SLOT_SCHEDULE = "slotSchedule";
    public static final String NODE_BOOKINGS = "bookings";
    public static final String NODE_PAYMENTS = "payments";
    public static final String NODE_FAVOURITES = "favourites";
    public static final String NODE_REVIEWS = "reviews";
    public static final String NODE_NOTIFICATIONS = "notifications";
    public static final String NODE_ENTRY_EXIT_LOGS = "entryExitLogs";
    public static final String NODE_DAILY_REVENUE = "dailyRevenue";
    public static final String NODE_APP_CONFIG = "appConfig";
    public static final String NODE_PROMOTIONS = "promotions";

    // ---------- Amenity names used in filters ----------
    public static final String AMENITY_EV = "EV Charging";
    public static final String AMENITY_COVERED = "Covered";
    public static final String AMENITY_CCTV = "CCTV";

    // ---------- User roles ----------
    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_ADMIN = "admin";

    // ---------- Slot statuses ----------
    public static final String SLOT_AVAILABLE = "AVAILABLE";
    public static final String SLOT_RESERVED = "RESERVED";
    public static final String SLOT_OCCUPIED = "OCCUPIED";
    public static final String SLOT_MAINTENANCE = "MAINTENANCE";

    // ---------- Booking statuses ----------
    public static final String BOOKING_PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String BOOKING_CONFIRMED = "CONFIRMED";
    public static final String BOOKING_ACTIVE = "ACTIVE";
    public static final String BOOKING_COMPLETED = "COMPLETED";
    public static final String BOOKING_CANCELLED = "CANCELLED";
    public static final String BOOKING_EXPIRED = "EXPIRED";

    // ---------- Vehicle types ----------
    public static final String VEHICLE_CAR = "CAR";
    public static final String VEHICLE_BIKE = "BIKE";

    // ---------- Business rules (defaults; can be overridden by appConfig) ----------
    public static final int GRACE_PERIOD_MINUTES = 15;
    public static final int RESERVE_BEFORE_MINUTES = 30;
    public static final int MIN_BOOKING_MINUTES = 30;
    public static final int MAX_BOOKING_HOURS = 12;
    public static final double OVERSTAY_MULTIPLIER = 1.5;

    // ---------- QR ticket ----------
    public static final String QR_PREFIX = "PARKEASE";

    // ---------- Intent extra keys ----------
    public static final String EXTRA_LOCATION_ID = "extra_location_id";
    public static final String EXTRA_SLOT_ID = "extra_slot_id";
    public static final String EXTRA_BOOKING_ID = "extra_booking_id";
    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_USER_ID = "extra_user_id";
    public static final String EXTRA_MESSAGE = "extra_message";
    public static final String EXTRA_EMAIL = "extra_email";
    public static final String EXTRA_SHOW_BACK = "extra_show_back";
}

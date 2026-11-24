package com.parkease.app.utils;

import androidx.annotation.NonNull;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns numbers and times into the text shown on screen. */
public final class FormatUtils {

    private static final Locale INDIA = new Locale("en", "IN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private FormatUtils() { }

    /** 1234.5 → "₹1,234.50", 40 → "₹40". */
    @NonNull
    public static String money(double amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(INDIA);
        f.setMaximumFractionDigits(amount == Math.floor(amount) ? 0 : 2);
        f.setMinimumFractionDigits(amount == Math.floor(amount) ? 0 : 2);
        return f.format(amount);
    }

    /** "Today", "Tomorrow" or "Tue, 25 Nov". */
    @NonNull
    public static String day(long millis) {
        LocalDate date = toZoned(millis).toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Today";
        if (date.equals(today.plusDays(1))) return "Tomorrow";
        return date.format(DATE);
    }

    /** "10:30 AM" */
    @NonNull
    public static String time(long millis) {
        return toZoned(millis).format(TIME);
    }

    /** "Today · 10:30 AM – 12:30 PM" */
    @NonNull
    public static String timeRange(long start, long end) {
        return day(start) + " · " + time(start) + " – " + time(end);
    }

    /** "1.4 km" or "850 m". */
    @NonNull
    public static String distance(float meters) {
        if (meters < 1000) return Math.round(meters / 10f) * 10 + " m";
        return String.format(Locale.ENGLISH, "%.1f km", meters / 1000f);
    }

    /** 4.333 → "4.3" */
    @NonNull
    public static String rating(double rating) {
        return String.format(Locale.ENGLISH, "%.1f", rating);
    }

    /** "Good morning" / "Good afternoon" / "Good evening" for the current time. */
    @NonNull
    public static String greetingPart() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    /** Is "HH:mm"–"HH:mm" open right now? Handles 24-hour places ("00:00"–"23:59"). */
    public static boolean isOpenNow(String open, String close) {
        try {
            LocalTime now = LocalTime.now();
            LocalTime o = LocalTime.parse(open);
            LocalTime c = LocalTime.parse(close);
            if (o.equals(c)) return true;
            return o.isBefore(c) ? !now.isBefore(o) && !now.isAfter(c)
                                 : !now.isBefore(o) || !now.isAfter(c);    // past midnight
        } catch (Exception e) {
            return true;   // unknown hours → don't hide the place
        }
    }

    private static ZonedDateTime toZoned(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
    }
}

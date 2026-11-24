package com.parkease.app.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.parkease.app.R;
import com.parkease.app.utils.Constants;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The parking time the customer wants: a date, a start time and a duration.
 *
 * Booking rules (checked by {@link #validate}):
 *  - starts on a 30-minute mark (10:00, 10:30 …) and not in the past
 *  - lasts 30 minutes to 12 hours, in 30-minute steps
 *  - ends on the same day (no bookings across midnight)
 *  - fits inside the location's opening hours
 *
 * The window is also turned into 30-minute "blocks" ({@link #blockKeys()}), which is how
 * reservations are stored and how conflicts are detected.
 */
public class TimeWindow {

    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BLOCK_KEY = DateTimeFormatter.ofPattern("HHmm");
    public static final int MIN_MINUTES = 30;
    public static final int MAX_MINUTES = 12 * 60;

    private final LocalDate date;
    private final LocalTime start;
    private final int minutes;

    public TimeWindow(@NonNull LocalDate date, @NonNull LocalTime start, int minutes) {
        this.date = date;
        this.start = start;
        this.minutes = minutes;
    }

    /** Default: the next 30-minute mark from now, for 2 hours. */
    @NonNull
    public static TimeWindow nextAvailable() {
        LocalDateTime now = LocalDateTime.now();
        int add = Constants.BLOCK_MINUTES - (now.getMinute() % Constants.BLOCK_MINUTES);
        LocalDateTime start = now.plusMinutes(add).withSecond(0).withNano(0);
        return new TimeWindow(start.toLocalDate(), start.toLocalTime(), 120);
    }

    /** Rebuilds the window of an existing booking (to find its 30-minute blocks). */
    @NonNull
    public static TimeWindow ofMillis(long startMillis, long endMillis) {
        LocalDateTime start = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startMillis), ZoneId.systemDefault());
        int minutes = (int) Math.max(0, (endMillis - startMillis) / 60_000L);
        return new TimeWindow(start.toLocalDate(), start.toLocalTime(), minutes);
    }

    @NonNull public LocalDate getDate() { return date; }
    @NonNull public LocalTime getStart() { return start; }
    public int getMinutes() { return minutes; }

    @NonNull
    public LocalTime getEnd() {
        return start.plusMinutes(minutes);
    }

    @NonNull
    public TimeWindow withDate(@NonNull LocalDate d) { return new TimeWindow(d, start, minutes); }

    @NonNull
    public TimeWindow withStart(@NonNull LocalTime t) { return new TimeWindow(date, t, minutes); }

    @NonNull
    public TimeWindow withMinutes(int m) { return new TimeWindow(date, start, m); }

    public double getHours() {
        return minutes / 60.0;
    }

    public long startMillis() {
        return date.atTime(start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public long endMillis() {
        return startMillis() + Duration.ofMinutes(minutes).toMillis();
    }

    /** "20261124" – the schedule node for this date. */
    @NonNull
    public String dateKey() {
        return date.format(DATE_KEY);
    }

    /** The 30-minute blocks covered, e.g. 10:00–11:30 → ["1000", "1030", "1100"]. */
    @NonNull
    public List<String> blockKeys() {
        List<String> keys = new ArrayList<>();
        for (int m = 0; m < minutes; m += Constants.BLOCK_MINUTES) {
            keys.add(start.plusMinutes(m).format(BLOCK_KEY));
        }
        return keys;
    }

    /** True if the window has started or starts within the given number of minutes. */
    public boolean startsWithin(int minutesFromNow) {
        return startMillis() <= System.currentTimeMillis() + minutesFromNow * 60_000L;
    }

    /**
     * @return 0 if the window is allowed, otherwise the error message to show.
     */
    @StringRes
    public int validate(@Nullable String openTime, @Nullable String closeTime) {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES || minutes % Constants.BLOCK_MINUTES != 0) {
            return R.string.time_err_duration;
        }
        if (start.getMinute() % Constants.BLOCK_MINUTES != 0) return R.string.time_err_step;
        if (startMillis() < System.currentTimeMillis() - 60_000L) return R.string.time_err_past;
        // Same day only: the end may be midnight (24:00) but not later
        if ((start.toSecondOfDay() + minutes * 60L) > 24 * 3600L) {
            return R.string.time_err_midnight;
        }
        try {
            LocalTime open = LocalTime.parse(openTime);
            LocalTime close = LocalTime.parse(closeTime);
            boolean allDay = open.equals(LocalTime.MIDNIGHT) && close.getHour() == 23 && close.getMinute() == 59;
            if (!allDay) {
                LocalTime end = getEnd();
                boolean endsAtMidnight = end.equals(LocalTime.MIDNIGHT);
                if (start.isBefore(open) || (!endsAtMidnight && end.isAfter(close)) || (endsAtMidnight && !close.equals(LocalTime.of(23, 59)))) {
                    return R.string.time_err_hours;
                }
            }
        } catch (Exception ignored) {
            // unknown opening hours → don't block the booking
        }
        return 0;
    }
}

package com.parkease.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.parkease.app.models.Booking;
import com.parkease.app.models.CancellationPolicy;
import com.parkease.app.models.TimeWindow;
import com.parkease.app.repository.BookingRepository;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.VehicleNumberUtils;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Step 11 – unit tests for the booking rules that run inside the app.
 * Run: gradlew testDebugUnitTest   (results: app/build/reports/tests/testDebugUnitTest/index.html)
 *
 * The final "no double booking" check happens in the Firebase security rules, which can't be
 * tested here – these tests check the app-side logic that builds the reservation.
 */
public class BookingRulesTest {

    private static final long MIN = 60_000L;
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    // ================================================================== time window rules

    @Test
    public void validWindowPasses() {
        TimeWindow w = new TimeWindow(TOMORROW, LocalTime.of(10, 0), 120);
        assertEquals(0, w.validate("06:00", "22:00"));
    }

    @Test
    public void pastStartIsRejected() {
        TimeWindow w = new TimeWindow(LocalDate.now().minusDays(1), LocalTime.of(10, 0), 60);
        assertEquals(R.string.time_err_past, w.validate("00:00", "23:59"));
    }

    @Test
    public void durationMustBe30MinTo12HoursIn30MinSteps() {
        assertEquals(R.string.time_err_duration, new TimeWindow(TOMORROW, LocalTime.of(10, 0), 0).validate("00:00", "23:59"));
        assertEquals(R.string.time_err_duration, new TimeWindow(TOMORROW, LocalTime.of(10, 0), 45).validate("00:00", "23:59"));
        assertEquals(R.string.time_err_duration, new TimeWindow(TOMORROW, LocalTime.of(8, 0), 750).validate("00:00", "23:59"));
        assertEquals(0, new TimeWindow(TOMORROW, LocalTime.of(8, 0), 720).validate("00:00", "23:59"));
    }

    @Test
    public void startMustBeOnHalfHour() {
        assertEquals(R.string.time_err_step, new TimeWindow(TOMORROW, LocalTime.of(10, 15), 60).validate("00:00", "23:59"));
    }

    @Test
    public void bookingCannotCrossMidnight() {
        assertEquals(R.string.time_err_midnight, new TimeWindow(TOMORROW, LocalTime.of(23, 0), 120).validate("00:00", "23:59"));
        assertEquals(0, new TimeWindow(TOMORROW, LocalTime.of(23, 0), 60).validate("00:00", "23:59"));
    }

    @Test
    public void mustFitOpeningHours() {
        assertEquals(R.string.time_err_hours, new TimeWindow(TOMORROW, LocalTime.of(7, 0), 60).validate("08:00", "20:00"));
        assertEquals(R.string.time_err_hours, new TimeWindow(TOMORROW, LocalTime.of(19, 0), 90).validate("08:00", "20:00"));
        assertEquals(0, new TimeWindow(TOMORROW, LocalTime.of(19, 0), 60).validate("08:00", "20:00"));
    }

    @Test
    public void endIsAlwaysAfterStart() {
        TimeWindow w = new TimeWindow(TOMORROW, LocalTime.of(10, 0), 30);
        assertTrue(w.endMillis() > w.startMillis());
    }

    // ================================================================== overlap detection (blocks)

    @Test
    public void windowIsSplitInto30MinuteBlocks() {
        TimeWindow w = new TimeWindow(TOMORROW, LocalTime.of(10, 0), 90);
        assertEquals(Arrays.asList("1000", "1030", "1100"), w.blockKeys());
    }

    @Test
    public void overlappingWindowsShareABlock() {
        // 10:00–12:00 and 11:30–13:00 overlap → both need block 1130 → second one is refused
        List<String> a = new TimeWindow(TOMORROW, LocalTime.of(10, 0), 120).blockKeys();
        List<String> b = new TimeWindow(TOMORROW, LocalTime.of(11, 30), 90).blockKeys();
        assertFalse(Collections.disjoint(a, b));
    }

    @Test
    public void backToBackWindowsDoNotConflict() {
        // 10:00–12:00 then 12:00–13:00 → no shared block → both allowed
        List<String> a = new TimeWindow(TOMORROW, LocalTime.of(10, 0), 120).blockKeys();
        List<String> b = new TimeWindow(TOMORROW, LocalTime.of(12, 0), 60).blockKeys();
        assertTrue(Collections.disjoint(a, b));
    }

    @Test
    public void ofMillisRebuildsTheSameBlocks() {
        TimeWindow w = new TimeWindow(TOMORROW, LocalTime.of(14, 30), 150);
        TimeWindow back = TimeWindow.ofMillis(w.startMillis(), w.endMillis());
        assertEquals(w.dateKey(), back.dateKey());
        assertEquals(w.blockKeys(), back.blockKeys());
    }

    // ================================================================== statuses & expiry

    private static Booking booking(String status, long start, long end, long holdUntil, double amount) {
        Booking b = new Booking();
        b.setId(status + start);
        b.setStatus(status);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setHoldUntil(holdUntil);
        b.setAmount(amount);
        return b;
    }

    @Test
    public void pendingHoldExpiresWhenTimeRunsOut() {
        long now = System.currentTimeMillis();
        Booking live = booking(Constants.BOOKING_PENDING_PAYMENT, now + 60 * MIN, now + 120 * MIN, now + 5 * MIN, 100);
        Booking dead = booking(Constants.BOOKING_PENDING_PAYMENT, now + 60 * MIN, now + 120 * MIN, now - MIN, 100);
        assertTrue(live.isHoldActive(now));
        assertFalse(live.shouldExpire(now));
        assertFalse(dead.isHoldActive(now));
        assertTrue(dead.shouldExpire(now));
    }

    @Test
    public void confirmedNoShowExpiresAfterGracePeriod() {
        long now = System.currentTimeMillis();
        Booking justStarted = booking(Constants.BOOKING_CONFIRMED, now - 10 * MIN, now + 60 * MIN, 0, 100);
        Booking noShow = booking(Constants.BOOKING_CONFIRMED, now - 20 * MIN, now + 60 * MIN, 0, 100);
        assertFalse(justStarted.shouldExpire(now));   // within 15-minute grace
        assertTrue(noShow.shouldExpire(now));
    }

    @Test
    public void cancelledAndCompletedNeverExpire() {
        long now = System.currentTimeMillis();
        assertFalse(booking(Constants.BOOKING_CANCELLED, now - 600 * MIN, now - 500 * MIN, 0, 100).shouldExpire(now));
        assertFalse(booking(Constants.BOOKING_COMPLETED, now - 600 * MIN, now - 500 * MIN, 0, 100).shouldExpire(now));
    }

    @Test
    public void tabsSplitBookingsCorrectly() {
        long now = System.currentTimeMillis();
        Booking upcoming = booking(Constants.BOOKING_CONFIRMED, now + 60 * MIN, now + 120 * MIN, 0, 100);
        Booking holding = booking(Constants.BOOKING_PENDING_PAYMENT, now + 30 * MIN, now + 90 * MIN, now + 5 * MIN, 50);
        Booking deadHold = booking(Constants.BOOKING_PENDING_PAYMENT, now + 30 * MIN, now + 90 * MIN, now - MIN, 50);
        Booking finished = booking(Constants.BOOKING_COMPLETED, now - 300 * MIN, now - 200 * MIN, 0, 100);
        Booking cancelled = booking(Constants.BOOKING_CANCELLED, now + 60 * MIN, now + 120 * MIN, 0, 100);
        List<Booking> all = new ArrayList<>(Arrays.asList(upcoming, holding, deadHold, finished, cancelled));

        // Upcoming: soonest first, includes the live hold, not the dead one
        assertEquals(Arrays.asList(holding, upcoming), BookingRepository.filter(all, BookingRepository.TAB_UPCOMING));
        assertEquals(Collections.singletonList(finished), BookingRepository.filter(all, BookingRepository.TAB_PAST));
        assertEquals(Collections.singletonList(cancelled), BookingRepository.filter(all, BookingRepository.TAB_CANCELLED));
        assertEquals(holding, BookingRepository.findNextUpcoming(all));
    }

    // ================================================================== cancellation policy

    @Test
    public void fullRefundWhenCancelledEarly() {
        long now = System.currentTimeMillis();
        CancellationPolicy p = new CancellationPolicy(60, 50);
        Booking b = booking(Constants.BOOKING_CONFIRMED, now + 120 * MIN, now + 180 * MIN, 0, 150);
        assertTrue(p.canCancel(b, now));
        assertTrue(p.isFullRefund(b, now));
        assertEquals(150, p.refundFor(b, now), 0.001);
    }

    @Test
    public void partialRefundWhenCancelledLate() {
        long now = System.currentTimeMillis();
        CancellationPolicy p = new CancellationPolicy(60, 50);
        Booking b = booking(Constants.BOOKING_CONFIRMED, now + 30 * MIN, now + 90 * MIN, 0, 75);
        assertTrue(p.canCancel(b, now));
        assertFalse(p.isFullRefund(b, now));
        assertEquals(37, p.refundFor(b, now), 0.001);   // floor(75 × 50%)
    }

    @Test
    public void cannotCancelAfterStartOrUnconfirmed() {
        long now = System.currentTimeMillis();
        CancellationPolicy p = new CancellationPolicy();
        Booking started = booking(Constants.BOOKING_CONFIRMED, now - MIN, now + 60 * MIN, 0, 100);
        Booking pending = booking(Constants.BOOKING_PENDING_PAYMENT, now + 60 * MIN, now + 120 * MIN, now + 5 * MIN, 100);
        assertFalse(p.canCancel(started, now));
        assertFalse(p.canCancel(pending, now));
        assertEquals(0, p.refundFor(started, now), 0.001);
    }

    // ================================================================== vehicle numbers

    @Test
    public void indianRegistrationNumbers() {
        assertEquals(0, VehicleNumberUtils.validate("GJ 01 AB 1234"));
        assertEquals(0, VehicleNumberUtils.validate("mh12de1433"));
        assertEquals(0, VehicleNumberUtils.validate("22 BH 1234 AB"));
        assertEquals(R.string.vehicle_err_state, VehicleNumberUtils.validate("ZZ01AB1234"));
        assertEquals(R.string.vehicle_err_format, VehicleNumberUtils.validate("GJ01AB12"));
        assertEquals("GJ 01 AB 1234", VehicleNumberUtils.format("gj01ab1234"));
    }
}

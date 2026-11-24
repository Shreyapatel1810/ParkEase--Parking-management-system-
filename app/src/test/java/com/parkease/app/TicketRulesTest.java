package com.parkease.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.parkease.app.models.Booking;
import com.parkease.app.models.Payment;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.TicketCodec;
import com.parkease.app.utils.TicketVerifier;
import com.parkease.app.utils.TicketVerifier.Verdict;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Step 13 – QR ticket format and the gate verification rules. */
public class TicketRulesTest {

    private static final long MIN = 60_000L;
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    // ================================================================== QR payload

    @Test
    public void tokenIs128BitHexAndUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            String t = TicketCodec.newToken();
            assertTrue(t, t.matches("^[a-f0-9]{32}$"));
            tokens.add(t);
        }
        assertEquals(5_000, tokens.size());
    }

    @Test
    public void encodeAndParseRoundTrip() {
        String qr = TicketCodec.encode("-Nx81kQ2abcdEFgh", TOKEN);
        assertEquals("PARKEASE1|-Nx81kQ2abcdEFgh|" + TOKEN, qr);
        TicketCodec.Scanned s = TicketCodec.parse(qr);
        assertNotNull(s);
        assertEquals("-Nx81kQ2abcdEFgh", s.bookingId);
        assertEquals(TOKEN, s.token);
    }

    @Test
    public void rejectsOtherQrCodes() {
        assertNull(TicketCodec.parse(null));
        assertNull(TicketCodec.parse("https://example.com"));
        assertNull(TicketCodec.parse("PARKEASE|TEST-BOOKING"));                 // old test QR
        assertNull(TicketCodec.parse("PARKEASE1|abc|short"));                    // token too short
        assertNull(TicketCodec.parse("PARKEASE1|a/b|" + TOKEN));                 // bad characters (path injection)
        assertNull(TicketCodec.parse("PARKEASE1|abc|" + TOKEN + "|extra"));
    }

    @Test
    public void tokenComparison() {
        assertTrue(TicketCodec.tokensMatch(TOKEN, TOKEN));
        assertFalse(TicketCodec.tokensMatch(TOKEN, "1123456789abcdef0123456789abcdef"));
        assertFalse(TicketCodec.tokensMatch(TOKEN, null));
        assertFalse(TicketCodec.tokensMatch(null, null));
    }

    // ================================================================== verification

    private static Booking booking(String status, long start, long end) {
        Booking b = new Booking();
        b.setId("BK1");
        b.setStatus(status);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setAmount(100);
        b.setTicketToken(TOKEN);
        b.setPaymentId("TXN1");
        return b;
    }

    private static Payment payment(String method, String status) {
        Payment p = new Payment();
        p.setTransactionId("TXN1");
        p.setBookingId("BK1");
        p.setMethod(method);
        p.setStatus(status);
        p.setAmount(100);
        return p;
    }

    private static final Payment PAID = payment(Constants.PAYMENT_UPI, Constants.PAYMENT_SUCCESS);

    private static Verdict verdict(Booking b, Payment p, String token, long now) {
        return TicketVerifier.verify(b, p, token, false, now).verdict;
    }

    @Test
    public void validPaidTicketCanEnter() {
        long now = System.currentTimeMillis();
        Booking b = booking(Constants.BOOKING_CONFIRMED, now + 10 * MIN, now + 120 * MIN);
        TicketVerifier.Result r = TicketVerifier.verify(b, PAID, TOKEN, false, now);
        assertEquals(Verdict.READY_FOR_ENTRY, r.verdict);
        assertFalse(r.cashDue);
    }

    @Test
    public void wrongOrMissingTokenIsInvalid() {
        long now = System.currentTimeMillis();
        Booking b = booking(Constants.BOOKING_CONFIRMED, now, now + 60 * MIN);
        assertEquals(Verdict.INVALID_TICKET, verdict(b, PAID, "ffffffffffffffffffffffffffffffff", now));
        assertEquals(Verdict.INVALID_TICKET, verdict(null, PAID, TOKEN, now));   // booking not found
    }

    @Test
    public void manualLookupSkipsTokenButNotOtherChecks() {
        long now = System.currentTimeMillis();
        Booking b = booking(Constants.BOOKING_CANCELLED, now, now + 60 * MIN);
        assertEquals(Verdict.CANCELLED, TicketVerifier.verify(b, PAID, null, true, now).verdict);
    }

    @Test
    public void statusChecks() {
        long now = System.currentTimeMillis();
        assertEquals(Verdict.CANCELLED, verdict(booking(Constants.BOOKING_CANCELLED, now, now + 60 * MIN), PAID, TOKEN, now));
        assertEquals(Verdict.EXPIRED, verdict(booking(Constants.BOOKING_EXPIRED, now, now + 60 * MIN), PAID, TOKEN, now));
        assertEquals(Verdict.ALREADY_COMPLETED, verdict(booking(Constants.BOOKING_COMPLETED, now, now + 60 * MIN), PAID, TOKEN, now));
        assertEquals(Verdict.NOT_PAID, verdict(booking(Constants.BOOKING_PENDING_PAYMENT, now, now + 60 * MIN), PAID, TOKEN, now));
    }

    @Test
    public void paymentChecks() {
        long now = System.currentTimeMillis();
        Booking b = booking(Constants.BOOKING_CONFIRMED, now, now + 60 * MIN);
        assertEquals(Verdict.NOT_PAID, verdict(b, null, TOKEN, now));
        assertEquals(Verdict.PAYMENT_PROBLEM, verdict(b, payment(Constants.PAYMENT_CARD, Constants.PAYMENT_FAILED), TOKEN, now));
        assertEquals(Verdict.PAYMENT_PROBLEM, verdict(b, payment(Constants.PAYMENT_UPI, Constants.PAYMENT_REFUNDED), TOKEN, now));

        Payment other = payment(Constants.PAYMENT_UPI, Constants.PAYMENT_SUCCESS);
        other.setBookingId("SOMEONE_ELSE");                       // payment of a different booking
        assertEquals(Verdict.NOT_PAID, verdict(b, other, TOKEN, now));

        TicketVerifier.Result cash = TicketVerifier.verify(b,
                payment(Constants.PAYMENT_CASH, Constants.PAYMENT_PENDING), TOKEN, false, now);
        assertEquals(Verdict.READY_FOR_ENTRY, cash.verdict);
        assertTrue(cash.cashDue);
    }

    @Test
    public void timeWindowForEntry() {
        long now = System.currentTimeMillis();
        TicketVerifier.Result early = TicketVerifier.verify(
                booking(Constants.BOOKING_CONFIRMED, now + 90 * MIN, now + 150 * MIN), PAID, TOKEN, false, now);
        assertEquals(Verdict.TOO_EARLY, early.verdict);
        assertEquals(60, early.minutesUntilEntry);
        // 30 minutes early is fine, late arrival before the end is fine
        assertEquals(Verdict.READY_FOR_ENTRY, verdict(booking(Constants.BOOKING_CONFIRMED, now + 30 * MIN, now + 90 * MIN), PAID, TOKEN, now));
        assertEquals(Verdict.READY_FOR_ENTRY, verdict(booking(Constants.BOOKING_CONFIRMED, now - 50 * MIN, now + 10 * MIN), PAID, TOKEN, now));
        assertEquals(Verdict.ENDED, verdict(booking(Constants.BOOKING_CONFIRMED, now - 120 * MIN, now - MIN), PAID, TOKEN, now));
    }

    @Test
    public void parkedVehicleCanOnlyExit() {
        long now = System.currentTimeMillis();
        Booking parked = booking(Constants.BOOKING_ACTIVE, now - 120 * MIN, now - 25 * MIN);
        TicketVerifier.Result r = TicketVerifier.verify(parked, PAID, TOKEN, false, now);
        assertEquals(Verdict.READY_FOR_EXIT, r.verdict);
        assertFalse(r.canEnter());                // a second entry is never offered
        assertEquals(25, r.overstayMinutes);
    }
}

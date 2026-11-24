package com.parkease.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STEP 13 – What goes inside the QR code, and how it is read back.
 *
 * QR text:  PARKEASE1|{bookingId}|{ticketToken}
 *
 *  - ticketToken is 128 random bits (32 hex characters) made with SecureRandom when the
 *    booking is confirmed, so a ticket can't be guessed or made up from a booking code.
 *  - The QR carries NO booking details (name, slot, times…). The scanner only uses the
 *    two values to look the booking up in Firebase and compares the token with the one
 *    saved there – the database is the only source of truth.
 */
public final class TicketCodec {

    public static final String PREFIX = Constants.QR_PREFIX + "1";
    private static final Pattern FORMAT =
            Pattern.compile("^" + PREFIX + "\\|([A-Za-z0-9_-]{1,40})\\|([a-f0-9]{32})$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private TicketCodec() { }

    /** A new secret ticket token: 16 random bytes as 32 lowercase hex characters. */
    @NonNull
    public static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    /** The text to put in the QR code. */
    @NonNull
    public static String encode(@NonNull String bookingId, @NonNull String token) {
        return PREFIX + "|" + bookingId + "|" + token;
    }

    /** What a scanned QR says (only used to look the booking up – never trusted as data). */
    public static final class Scanned {
        public final String bookingId;
        public final String token;

        Scanned(String bookingId, String token) {
            this.bookingId = bookingId;
            this.token = token;
        }
    }

    /** @return the booking reference, or null if this isn't a ParkEase ticket QR. */
    @Nullable
    public static Scanned parse(@Nullable String text) {
        if (text == null) return null;
        Matcher m = FORMAT.matcher(text.trim());
        return m.matches() ? new Scanned(m.group(1), m.group(2)) : null;
    }

    /** Compares tokens in constant time (doesn't leak how many characters matched). */
    public static boolean tokensMatch(@Nullable String a, @Nullable String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}

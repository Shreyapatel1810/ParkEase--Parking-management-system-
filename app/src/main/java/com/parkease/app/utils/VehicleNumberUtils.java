package com.parkease.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.parkease.app.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indian vehicle registration numbers.
 *
 * Accepted formats (spaces and dashes are ignored):
 * <ul>
 *   <li>Standard: STATE + RTO district + series + number, e.g. GJ 01 AB 1234, MH 12 A 1234,
 *       DL 3C AB 1234 → {@code [A-Z]{2} [0-9]{1,2} [A-Z]{1,3} [0-9]{4}}</li>
 *   <li>Bharat (BH) series: YEAR + BH + number + letters, e.g. 22 BH 1234 AB</li>
 * </ul>
 * The first two letters must be a real state / union-territory code.
 */
public final class VehicleNumberUtils {

    private static final Pattern STANDARD = Pattern.compile("^([A-Z]{2})([0-9]{1,2})([A-Z]{1,3})([0-9]{4})$");
    private static final Pattern BH_SERIES = Pattern.compile("^([0-9]{2})(BH)([0-9]{4})([A-Z]{1,2})$");

    /** State and union-territory codes used on Indian number plates. */
    private static final Set<String> STATE_CODES = new HashSet<>(Arrays.asList(
            "AN", "AP", "AR", "AS", "BR", "CG", "CH", "DD", "DL", "DN", "GA", "GJ", "HP", "HR",
            "JH", "JK", "KA", "KL", "LA", "LD", "MH", "ML", "MN", "MP", "MZ", "NL", "OD", "OR",
            "PB", "PY", "RJ", "SK", "TN", "TR", "TS", "UK", "UA", "UP", "WB"));

    public static final int MAX_LENGTH = 13;   // allows spaces while typing

    private VehicleNumberUtils() { }

    /** "gj 01-ab 1234 " → "GJ01AB1234" (the form stored in Firebase). */
    @NonNull
    public static String normalize(@Nullable String input) {
        return input == null ? "" : input.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** @return 0 if valid, otherwise the error message to show. */
    @StringRes
    public static int validate(@Nullable String input) {
        String n = normalize(input);
        if (n.isEmpty()) return R.string.err_required;
        Matcher m = STANDARD.matcher(n);
        if (m.matches()) {
            return STATE_CODES.contains(m.group(1)) ? 0 : R.string.vehicle_err_state;
        }
        return BH_SERIES.matcher(n).matches() ? 0 : R.string.vehicle_err_format;
    }

    /** "GJ01AB1234" → "GJ 01 AB 1234", "22BH1234AB" → "22 BH 1234 AB". Unknown formats are returned as is. */
    @NonNull
    public static String format(@Nullable String number) {
        String n = normalize(number);
        Matcher m = STANDARD.matcher(n);
        if (m.matches()) return m.group(1) + " " + m.group(2) + " " + m.group(3) + " " + m.group(4);
        m = BH_SERIES.matcher(n);
        if (m.matches()) return m.group(1) + " " + m.group(2) + " " + m.group(3) + " " + m.group(4);
        return n;
    }
}

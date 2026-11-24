package com.parkease.app.utils;

import android.util.Patterns;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.parkease.app.R;

import java.util.regex.Pattern;

/**
 * Input checks for forms. Each method returns 0 when the value is valid,
 * or a string resource id with the error message to show.
 *
 * Example:  int error = Validator.email(text);
 *           if (error != 0) emailLayout.setError(getString(error));
 */
public final class Validator {

    /** Letters, spaces, dot, apostrophe and hyphen; 2–60 characters; must start with a letter. */
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z .'-]{1,59}$");

    /** Indian mobile: 10 digits starting with 6, 7, 8 or 9. */
    private static final Pattern PHONE = Pattern.compile("^[6-9][0-9]{9}$");

    public static final int PASSWORD_MIN_LENGTH = 8;

    private Validator() { }

    @StringRes
    public static int required(@Nullable String value) {
        return isBlank(value) ? R.string.err_required : 0;
    }

    @StringRes
    public static int fullName(@Nullable String value) {
        if (isBlank(value)) return R.string.err_required;
        return NAME.matcher(value.trim()).matches() ? 0 : R.string.err_name_invalid;
    }

    @StringRes
    public static int email(@Nullable String value) {
        if (isBlank(value)) return R.string.err_required;
        return Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches() ? 0 : R.string.err_email_invalid;
    }

    @StringRes
    public static int phone(@Nullable String value) {
        if (isBlank(value)) return R.string.err_required;
        return PHONE.matcher(normalizePhone(value)).matches() ? 0 : R.string.err_phone_invalid;
    }

    /**
     * Strong password rules (registration only):
     * 8+ characters, upper case, lower case, number, symbol, no spaces.
     */
    @StringRes
    public static int strongPassword(@Nullable String value) {
        if (value == null || value.isEmpty()) return R.string.err_required;
        if (value.contains(" ")) return R.string.err_password_space;
        if (value.length() < PASSWORD_MIN_LENGTH) return R.string.err_password_short;
        if (!value.matches(".*[A-Z].*")) return R.string.err_password_upper;
        if (!value.matches(".*[a-z].*")) return R.string.err_password_lower;
        if (!value.matches(".*[0-9].*")) return R.string.err_password_digit;
        if (!value.matches(".*[^A-Za-z0-9].*")) return R.string.err_password_symbol;
        return 0;
    }

    @StringRes
    public static int confirmPassword(@Nullable String password, @Nullable String confirm) {
        if (confirm == null || confirm.isEmpty()) return R.string.err_required;
        return confirm.equals(password) ? 0 : R.string.err_password_mismatch;
    }

    /**
     * Password strength score for the meter on the registration screen:
     * 0 = empty, 1 = weak, 2 = fair, 3 = good, 4 = strong.
     */
    public static int passwordStrength(@Nullable String value) {
        if (value == null || value.isEmpty()) return 0;
        int score = 0;
        if (value.length() >= PASSWORD_MIN_LENGTH) score++;
        if (value.matches(".*[A-Z].*") && value.matches(".*[a-z].*")) score++;
        if (value.matches(".*[0-9].*")) score++;
        if (value.matches(".*[^A-Za-z0-9].*")) score++;
        return Math.max(1, score);
    }

    // ------------------------------------------------------------------ normalising

    /** Trims and lower-cases an email so "Rahul@Mail.com " and "rahul@mail.com" are the same. */
    public static String normalizeEmail(@Nullable String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** Keeps digits only and removes a leading +91 / 91 / 0, e.g. "+91 98765 43210" → "9876543210". */
    public static String normalizePhone(@Nullable String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        return digits;
    }

    /** Collapses repeated spaces: "  rahul   shah " → "rahul shah". */
    public static String normalizeName(@Nullable String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}

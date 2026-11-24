package com.parkease.app.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.database.DatabaseError;
import com.parkease.app.R;

/**
 * Turns technical Firebase errors into clear messages for the user.
 * The original error is still written to Logcat (tag "ParkEaseFirebase") for debugging.
 */
public final class FirebaseErrorMapper {

    private static final String TAG = "ParkEaseFirebase";

    private FirebaseErrorMapper() { }

    /** For Firebase Authentication errors (login, register, reset password). */
    @NonNull
    public static String fromAuth(@NonNull Context context, @Nullable Exception e) {
        Log.w(TAG, "Auth error", e);
        if (e instanceof FirebaseNetworkException) {
            return context.getString(R.string.err_network);
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return context.getString(R.string.err_too_many_requests);
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return context.getString(R.string.err_email_exists);          // duplicate email
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return context.getString(R.string.err_weak_password_server);
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(code)) {
                return context.getString(R.string.err_user_disabled);
            }
            // ERROR_USER_NOT_FOUND – same message as a wrong password, so attackers
            // can't use the login screen to find out which emails are registered
            return context.getString(R.string.err_invalid_credentials);
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) e).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) {
                return context.getString(R.string.err_email_invalid);
            }
            return context.getString(R.string.err_invalid_credentials);
        }
        return context.getString(R.string.err_generic);
    }

    /** For Realtime Database errors (reading / saving the profile). */
    @NonNull
    public static String fromDatabase(@NonNull Context context, @Nullable DatabaseError error) {
        if (error == null) return context.getString(R.string.err_generic);
        Log.w(TAG, "Database error " + error.getCode() + ": " + error.getMessage());
        switch (error.getCode()) {
            case DatabaseError.PERMISSION_DENIED:
                return context.getString(R.string.err_permission_denied);
            case DatabaseError.NETWORK_ERROR:
            case DatabaseError.DISCONNECTED:
            case DatabaseError.UNAVAILABLE:
                return context.getString(R.string.err_network);
            default:
                return context.getString(R.string.err_generic);
        }
    }

    /** For a database write that failed with a plain Exception. */
    @NonNull
    public static String fromDatabase(@NonNull Context context, @Nullable Exception e) {
        Log.w(TAG, "Database error", e);
        if (e != null && e.getMessage() != null
                && e.getMessage().toLowerCase().contains("permission")) {
            return context.getString(R.string.err_permission_denied);
        }
        return context.getString(R.string.err_generic);
    }
}

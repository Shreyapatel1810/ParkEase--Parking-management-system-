package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.parkease.app.R;
import com.parkease.app.database.UserDao;
import com.parkease.app.models.User;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.NetworkUtils;
import com.parkease.app.utils.SessionManager;
import com.parkease.app.utils.Validator;

import java.util.HashMap;
import java.util.Map;

/**
 * Profile data for the logged-in user, combining two stores:
 *
 * <pre>
 *            ┌──────── Firebase users/{uid} ────────┐   main, shared, always correct
 *  screen ◄──┤                                      │
 *            └──────── SQLite user_profile ─────────┘   copy on the phone, for speed/offline
 * </pre>
 *
 * Rules:
 *  - READ:   show the SQLite copy at once, then refresh it from Firebase ("sync").
 *  - WRITE:  send the change to Firebase first; only when Firebase accepts it, update SQLite.
 *            So the phone never shows a change that the server rejected.
 *  - Edits need internet, so the user gets a clear success or error straight away.
 */
public class UserRepository {

    private final Context context;
    private final UserDao userDao;
    private final AuthRepository authRepository;

    public UserRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.userDao = new UserDao(this.context);
        this.authRepository = new AuthRepository(this.context);
    }

    // ================================================================== READ

    /** The profile saved on this phone (instant, works offline). Null if none. */
    @Nullable
    public User getCachedProfile() {
        String uid = currentUid();
        return uid == null ? null : userDao.get(uid);
    }

    /** When the phone copy was last refreshed from Firebase (0 = never). */
    public long getLastSyncedAt() {
        String uid = currentUid();
        return uid == null ? 0 : userDao.getSyncedAt(uid);
    }

    /**
     * SYNC: downloads the latest profile from Firebase and saves it into SQLite.
     * (AuthRepository also checks "blocked" and logs the user out if needed.)
     */
    public void syncProfile(@NonNull DataCallback<User> callback) {
        authRepository.loadCurrentUserProfile(callback);
    }

    // ================================================================== UPDATE

    /** Changes name and mobile number. Email can't be changed here (it is the login ID). */
    public void updateDetails(@NonNull String fullName, @NonNull String phone,
                              @NonNull DataCallback<User> callback) {
        // Validate again here, so no screen can save bad data by mistake
        if (Validator.fullName(fullName) != 0 || Validator.phone(phone) != 0) {
            callback.onError(context.getString(R.string.err_generic));
            return;
        }
        FirebaseUser fbUser = readyUser(callback);
        if (fbUser == null) return;

        Map<String, Object> changes = new HashMap<>();
        changes.put("fullName", fullName);
        changes.put("phone", phone);
        changes.put("updatedAt", ServerValue.TIMESTAMP);

        userRef(fbUser.getUid()).updateChildren(changes)
                .addOnSuccessListener(unused -> {
                    userDao.updateDetails(fbUser.getUid(), fullName, phone);
                    fbUser.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName).build());
                    User updated = userDao.get(fbUser.getUid());
                    if (updated != null) SessionManager.saveUser(context, updated);
                    callback.onSuccess(updated);
                })
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    /** Saves a new profile photo (Base64 JPEG from ImageUtils). */
    public void updatePhoto(@NonNull String photoBase64, @NonNull DataCallback<User> callback) {
        writePhoto(photoBase64, callback);
    }

    /** DELETE: removes the profile photo (the initials are shown instead). */
    public void removePhoto(@NonNull DataCallback<User> callback) {
        writePhoto(null, callback);
    }

    private void writePhoto(@Nullable String photoBase64, @NonNull DataCallback<User> callback) {
        FirebaseUser fbUser = readyUser(callback);
        if (fbUser == null) return;

        Map<String, Object> changes = new HashMap<>();
        changes.put("photo", photoBase64);              // null deletes the field in Firebase
        changes.put("updatedAt", ServerValue.TIMESTAMP);

        userRef(fbUser.getUid()).updateChildren(changes)
                .addOnSuccessListener(unused -> {
                    userDao.updatePhoto(fbUser.getUid(), photoBase64);
                    callback.onSuccess(userDao.get(fbUser.getUid()));
                })
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    // ================================================================== PASSWORD

    /**
     * Changes the login password.
     * Firebase requires a recent login for this, so we first "re-authenticate" with the
     * current password, then set the new one. Neither password is stored anywhere by the app.
     */
    public void changePassword(@NonNull String currentPassword, @NonNull String newPassword,
                               @NonNull DataCallback<Void> callback) {
        FirebaseUser fbUser = readyUser(callback);
        if (fbUser == null) return;
        if (fbUser.getEmail() == null) {
            callback.onError(context.getString(R.string.err_session_expired));
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(fbUser.getEmail(), currentPassword);
        fbUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> fbUser.updatePassword(newPassword)
                        .addOnSuccessListener(done -> callback.onSuccess(null))
                        .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromAuth(context, e))))
                .addOnFailureListener(e -> {
                    String message = FirebaseErrorMapper.fromAuth(context, e);
                    // At this step "invalid credentials" can only mean a wrong current password
                    if (message.equals(context.getString(R.string.err_invalid_credentials))) {
                        message = context.getString(R.string.err_current_password_wrong);
                    }
                    callback.onError(message);
                });
    }

    // ================================================================== helpers

    @Nullable
    private String currentUid() {
        FirebaseUser u = authRepository.getCurrentFirebaseUser();
        return u != null ? u.getUid() : SessionManager.getCachedUid(context);
    }

    private DatabaseReference userRef(@NonNull String uid) {
        return FirebaseDatabase.getInstance().getReference(Constants.NODE_USERS).child(uid);
    }

    /** Returns the signed-in user, or reports why we can't continue (no Firebase/internet/session). */
    @Nullable
    private FirebaseUser readyUser(@NonNull DataCallback<?> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return null;
        }
        if (!NetworkUtils.isOnline(context)) {
            callback.onError(context.getString(R.string.err_no_internet));
            return null;
        }
        FirebaseUser user = authRepository.getCurrentFirebaseUser();
        if (user == null) {
            callback.onError(context.getString(R.string.err_session_expired));
        }
        return user;
    }
}

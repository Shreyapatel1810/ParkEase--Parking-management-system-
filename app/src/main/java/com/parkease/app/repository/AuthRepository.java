package com.parkease.app.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.parkease.app.R;
import com.parkease.app.database.DBHelper;
import com.parkease.app.database.UserDao;
import com.parkease.app.models.User;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.NetworkUtils;
import com.parkease.app.utils.SessionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything about accounts: register, log in, reset password, load profile, log out.
 * This is the ONLY class that talks to Firebase Authentication.
 *
 * Security rules this class follows:
 *  - Passwords go only to Firebase Authentication. They are never written to the
 *    Realtime Database, SQLite, SharedPreferences or Logcat.
 *  - New accounts always get role = "customer". The app has no code that can make an
 *    admin; that is done by a trusted person in the Firebase console, and the database
 *    rules (database.rules.json) reject any attempt by the app to change a role.
 *  - Blocked users are logged out immediately.
 */
public class AuthRepository {

    private static final String TAG = "AuthRepository";

    private final Context context;

    public AuthRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    // ================================================================== session

    /** The Firebase account that is currently signed in on this phone, or null. */
    @Nullable
    public FirebaseUser getCurrentFirebaseUser() {
        if (!FirebaseHelper.isConfigured(context)) return null;
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    public boolean isLoggedIn() {
        return getCurrentFirebaseUser() != null;
    }

    // ================================================================== register

    /**
     * Creates a Firebase Authentication account, then saves the profile (without the
     * password) to users/{uid} with role "customer".
     * If saving the profile fails, the new account is deleted again, so there are never
     * half-created accounts.
     */
    public void register(@NonNull String fullName, @NonNull String email, @NonNull String phone,
                         @NonNull String password, @NonNull DataCallback<User> callback) {
        if (!checkReady(callback)) return;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError(context.getString(R.string.err_generic));
                        return;
                    }
                    // Show the name in Firebase Authentication too (nice in the console)
                    firebaseUser.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName).build());

                    saveNewProfile(firebaseUser, fullName, email, phone, callback);
                })
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromAuth(context, e)));
    }

    private void saveNewProfile(@NonNull FirebaseUser firebaseUser, @NonNull String fullName,
                                @NonNull String email, @NonNull String phone,
                                @NonNull DataCallback<User> callback) {
        String uid = firebaseUser.getUid();

        // A Map (not the User object) so createdAt can be the SERVER's time
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", uid);
        profile.put("fullName", fullName);
        profile.put("email", email);
        profile.put("phone", phone);
        profile.put("role", Constants.ROLE_CUSTOMER);   // always customer – never admin
        profile.put("blocked", false);
        profile.put("createdAt", ServerValue.TIMESTAMP);

        usersRef().child(uid).setValue(profile)
                .addOnSuccessListener(unused -> {
                    User user = new User(uid, fullName, email, phone, Constants.ROLE_CUSTOMER);
                    user.setCreatedAt(System.currentTimeMillis());
                    SessionManager.saveUser(context, user);
                    new UserDao(context).upsert(user);    // first offline copy
                    callback.onSuccess(user);
                })
                .addOnFailureListener(e -> {
                    // Roll back: remove the Auth account so the email can be used again
                    String message = FirebaseErrorMapper.fromDatabase(context, e);
                    firebaseUser.delete().addOnCompleteListener(t -> {
                        FirebaseAuth.getInstance().signOut();
                        callback.onError(message);
                    });
                });
    }

    // ================================================================== login

    /** Signs in with Firebase Authentication, then loads and checks the profile. */
    public void login(@NonNull String email, @NonNull String password,
                      @NonNull DataCallback<User> callback) {
        if (!checkReady(callback)) return;

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> loadCurrentUserProfile(callback))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromAuth(context, e)));
    }

    /**
     * Reads users/{uid} for the signed-in account.
     * Logs out (and returns an error) if the profile is missing or the user is blocked.
     * Used after login and when the app starts with a saved session.
     */
    public void loadCurrentUserProfile(@NonNull DataCallback<User> callback) {
        FirebaseUser firebaseUser = getCurrentFirebaseUser();
        if (firebaseUser == null) {
            callback.onError(context.getString(R.string.err_session_expired));
            return;
        }

        // get() asks the server first (fresh role / blocked status) and only uses the
        // offline cache when there is no connection.
        usersRef().child(firebaseUser.getUid()).get()
                .addOnSuccessListener(snapshot -> handleProfile(snapshot, firebaseUser, callback))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    private void handleProfile(@NonNull DataSnapshot snapshot, @NonNull FirebaseUser firebaseUser,
                               @NonNull DataCallback<User> callback) {
        User user = snapshot.getValue(User.class);
        if (user == null) {
            logout();
            callback.onError(context.getString(R.string.err_profile_missing));
            return;
        }
        if (user.isBlocked()) {
            logout();
            callback.onError(context.getString(R.string.err_account_blocked));
            return;
        }
        if (user.getUid() == null) user.setUid(firebaseUser.getUid());
        SessionManager.saveUser(context, user);
        new UserDao(context).upsert(user);            // keep the offline copy in sync
        callback.onSuccess(user);
    }

    // ================================================================== forgot password

    /**
     * Asks Firebase to email a password-reset link.
     * Firebase does not reveal whether the email is registered (to protect privacy),
     * so the screen always shows the same "check your email" message on success.
     */
    public void sendPasswordReset(@NonNull String email, @NonNull DataCallback<Void> callback) {
        if (!checkReady(callback)) return;

        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromAuth(context, e)));
    }

    // ================================================================== logout

    /** Signs out of Firebase and deletes all personal data stored on this phone. */
    public void logout() {
        if (FirebaseHelper.isConfigured(context)) {
            FirebaseAuth.getInstance().signOut();
        }
        SessionManager.clearUser(context);
        try {
            DBHelper.getInstance(context).clearAll();   // offline tickets, searches, etc.
        } catch (Exception e) {
            Log.w(TAG, "Could not clear local database", e);
        }
    }

    // ================================================================== helpers

    private DatabaseReference usersRef() {
        return FirebaseDatabase.getInstance().getReference(Constants.NODE_USERS);
    }

    /** Stops early with a clear message if Firebase isn't set up or there is no internet. */
    private boolean checkReady(@NonNull DataCallback<?> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return false;
        }
        if (!NetworkUtils.isOnline(context)) {
            callback.onError(context.getString(R.string.err_no_internet));
            return false;
        }
        return true;
    }
}

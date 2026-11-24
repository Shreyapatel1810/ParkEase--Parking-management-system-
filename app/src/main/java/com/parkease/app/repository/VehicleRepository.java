package com.parkease.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.parkease.app.R;
import com.parkease.app.models.Vehicle;
import com.parkease.app.utils.Constants;
import com.parkease.app.utils.FirebaseErrorMapper;
import com.parkease.app.utils.FirebaseHelper;
import com.parkease.app.utils.NetworkUtils;
import com.parkease.app.utils.VehicleNumberUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The logged-in customer's vehicles.
 *
 * <pre>
 * vehicles/{uid}/{vehicleId}            = the vehicle
 * vehicleRegistrations/{uid}/{REGNO}    = vehicleId     (one entry per registration number)
 * </pre>
 * Duplicate prevention happens twice:
 *  1. In the app – a quick check so the user gets a clear message.
 *  2. On the server – every add/edit writes the vehicle AND its index entry in ONE atomic
 *     update; the database rules refuse it if the index already belongs to another vehicle.
 *     So the same number can never be saved twice, even by a modified app.
 */
public class VehicleRepository {

    private final Context context;

    public VehicleRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    // ================================================================== READ (live)

    /** My vehicles, newest first, updated live. */
    @NonNull
    public Subscription observeMyVehicles(@NonNull DataCallback<List<Vehicle>> callback) {
        FirebaseUser me = currentUser();
        if (me == null) {
            callback.onError(context.getString(FirebaseHelper.isConfigured(context)
                    ? R.string.err_session_expired : R.string.err_firebase_not_configured));
            return Subscription.EMPTY;
        }
        return Subscription.listen(db().child(Constants.NODE_VEHICLES).child(me.getUid()), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Vehicle> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Vehicle v = child.getValue(Vehicle.class);
                    if (v != null) {
                        v.setId(child.getKey());
                        list.add(v);
                    }
                }
                list.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                callback.onSuccess(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(FirebaseErrorMapper.fromDatabase(context, error));
            }
        });
    }

    // ================================================================== INSERT / UPDATE

    /**
     * Adds a new vehicle (existing == null) or edits one (existing != null).
     * Fields are validated again here so no screen can save bad data.
     */
    public void saveVehicle(@Nullable Vehicle existing, @NonNull String registrationInput,
                            @NonNull String vehicleType, @Nullable String nicknameInput,
                            @NonNull DataCallback<Vehicle> callback) {
        FirebaseUser me = currentUser();
        if (!checkReady(me, callback)) return;

        String reg = VehicleNumberUtils.normalize(registrationInput);
        int regError = VehicleNumberUtils.validate(reg);
        if (regError != 0) {
            callback.onError(context.getString(regError));
            return;
        }
        if (!vehicleType.matches("CAR|BIKE|SUV")) {
            callback.onError(context.getString(R.string.err_generic));
            return;
        }
        String nickname = nicknameInput == null ? "" : nicknameInput.trim().replaceAll("\\s+", " ");
        if (nickname.length() > 30) {
            callback.onError(context.getString(R.string.vehicle_err_nickname));
            return;
        }

        String uid = me.getUid();
        DatabaseReference index = db().child(Constants.NODE_VEHICLE_REGISTRATIONS).child(uid).child(reg);

        // 1) Friendly duplicate check
        index.get().addOnSuccessListener(snap -> {
            String ownerId = snap.getValue(String.class);
            boolean takenByAnother = ownerId != null && (existing == null || !ownerId.equals(existing.getId()));
            if (takenByAnother) {
                callback.onError(context.getString(R.string.vehicle_err_duplicate));
                return;
            }
            // 2) Atomic write (the server rules make the final decision)
            write(uid, existing, reg, vehicleType, nickname, callback);
        }).addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    private void write(@NonNull String uid, @Nullable Vehicle existing, @NonNull String reg,
                       @NonNull String type, @NonNull String nickname, @NonNull DataCallback<Vehicle> callback) {
        String vehicleId = existing != null ? existing.getId()
                : db().child(Constants.NODE_VEHICLES).child(uid).push().getKey();
        if (vehicleId == null) {
            callback.onError(context.getString(R.string.err_generic));
            return;
        }
        String base = Constants.NODE_VEHICLES + "/" + uid + "/" + vehicleId + "/";
        String indexBase = Constants.NODE_VEHICLE_REGISTRATIONS + "/" + uid + "/";

        Map<String, Object> updates = new HashMap<>();
        updates.put(base + "userId", uid);
        updates.put(base + "registrationNumber", reg);
        updates.put(base + "vehicleType", type);
        updates.put(base + "nickname", nickname.isEmpty() ? null : nickname);   // null removes it
        updates.put(base + "updatedAt", ServerValue.TIMESTAMP);
        if (existing == null) updates.put(base + "createdAt", ServerValue.TIMESTAMP);
        updates.put(indexBase + reg, vehicleId);
        // Number changed while editing → free the old number
        if (existing != null && existing.getRegistrationNumber() != null
                && !existing.getRegistrationNumber().equals(reg)) {
            updates.put(indexBase + existing.getRegistrationNumber(), null);
        }

        db().updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    Vehicle v = new Vehicle();
                    v.setId(vehicleId);
                    v.setUserId(uid);
                    v.setRegistrationNumber(reg);
                    v.setVehicleType(type);
                    v.setNickname(nickname.isEmpty() ? null : nickname);
                    v.setCreatedAt(existing != null ? existing.getCreatedAt() : System.currentTimeMillis());
                    callback.onSuccess(v);
                })
                .addOnFailureListener(e -> {
                    // Refused by the rules → almost certainly the same number was saved meanwhile
                    String msg = e.getMessage() != null && e.getMessage().toLowerCase().contains("permission")
                            ? context.getString(R.string.vehicle_err_duplicate)
                            : FirebaseErrorMapper.fromDatabase(context, e);
                    callback.onError(msg);
                });
    }

    // ================================================================== DELETE

    /** Deletes the vehicle and frees its registration number (one atomic update). */
    public void deleteVehicle(@NonNull Vehicle vehicle, @NonNull DataCallback<Void> callback) {
        FirebaseUser me = currentUser();
        if (!checkReady(me, callback)) return;
        String uid = me.getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put(Constants.NODE_VEHICLES + "/" + uid + "/" + vehicle.getId(), null);
        if (vehicle.getRegistrationNumber() != null) {
            updates.put(Constants.NODE_VEHICLE_REGISTRATIONS + "/" + uid + "/" + vehicle.getRegistrationNumber(), null);
        }
        db().updateChildren(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(FirebaseErrorMapper.fromDatabase(context, e)));
    }

    // ================================================================== helpers

    @Nullable
    private FirebaseUser currentUser() {
        if (!FirebaseHelper.isConfigured(context)) return null;
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    private boolean checkReady(@Nullable FirebaseUser me, @NonNull DataCallback<?> callback) {
        if (!FirebaseHelper.isConfigured(context)) {
            callback.onError(context.getString(R.string.err_firebase_not_configured));
            return false;
        }
        if (me == null) {
            callback.onError(context.getString(R.string.err_session_expired));
            return false;
        }
        if (!NetworkUtils.isOnline(context)) {
            callback.onError(context.getString(R.string.err_no_internet));
            return false;
        }
        return true;
    }

    private DatabaseReference db() {
        return FirebaseDatabase.getInstance().getReference();
    }
}

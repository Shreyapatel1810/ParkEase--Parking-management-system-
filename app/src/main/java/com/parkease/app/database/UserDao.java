package com.parkease.app.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parkease.app.database.DBContract.UserProfile;
import com.parkease.app.models.User;

/**
 * DAO = "Data Access Object": the only class that runs SQL on the user_profile table.
 *
 * Operations (CRUD):
 *  - INSERT / UPDATE → {@link #upsert(User)}       save the latest copy from Firebase
 *  - READ            → {@link #get(String)}        load the saved copy (works offline)
 *  - UPDATE          → {@link #updateDetails}, {@link #updatePhoto}  after a successful edit
 *  - DELETE          → {@link #delete(String)}     on logout (via DBHelper.clearAll)
 *
 * All values go through ContentValues / "?" placeholders, never string concatenation,
 * which protects against SQL injection.
 */
public class UserDao {

    private final DBHelper helper;

    public UserDao(@NonNull Context context) {
        this.helper = DBHelper.getInstance(context);
    }

    /**
     * INSERT or REPLACE: adds the row, or overwrites it if this uid already exists.
     * Called every time a fresh profile arrives from Firebase.
     */
    public void upsert(@NonNull User user) {
        ContentValues v = new ContentValues();
        v.put(UserProfile.UID, user.getUid());
        v.put(UserProfile.FULL_NAME, user.getFullName());
        v.put(UserProfile.EMAIL, user.getEmail());
        v.put(UserProfile.PHONE, user.getPhone());
        v.put(UserProfile.ROLE, user.getRole());
        v.put(UserProfile.BLOCKED, user.isBlocked() ? 1 : 0);
        v.put(UserProfile.PHOTO, user.getPhoto());
        v.put(UserProfile.CREATED_AT, user.getCreatedAt());
        v.put(UserProfile.UPDATED_AT, user.getUpdatedAt());
        v.put(UserProfile.SYNCED_AT, System.currentTimeMillis());
        helper.getWritableDatabase()
                .insertWithOnConflict(UserProfile.TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** READ: the saved profile, or null if this phone has no copy yet. */
    @Nullable
    public User get(@NonNull String uid) {
        try (Cursor c = helper.getReadableDatabase().query(UserProfile.TABLE, null,
                UserProfile.UID + " = ?", new String[]{uid}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            User u = new User();
            u.setUid(c.getString(c.getColumnIndexOrThrow(UserProfile.UID)));
            u.setFullName(c.getString(c.getColumnIndexOrThrow(UserProfile.FULL_NAME)));
            u.setEmail(c.getString(c.getColumnIndexOrThrow(UserProfile.EMAIL)));
            u.setPhone(c.getString(c.getColumnIndexOrThrow(UserProfile.PHONE)));
            u.setRole(c.getString(c.getColumnIndexOrThrow(UserProfile.ROLE)));
            u.setBlocked(c.getInt(c.getColumnIndexOrThrow(UserProfile.BLOCKED)) == 1);
            u.setPhoto(c.getString(c.getColumnIndexOrThrow(UserProfile.PHOTO)));
            u.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(UserProfile.CREATED_AT)));
            u.setUpdatedAt(c.getLong(c.getColumnIndexOrThrow(UserProfile.UPDATED_AT)));
            return u;
        }
    }

    /** When the saved copy was last refreshed from Firebase (0 = never). */
    public long getSyncedAt(@NonNull String uid) {
        try (Cursor c = helper.getReadableDatabase().query(UserProfile.TABLE,
                new String[]{UserProfile.SYNCED_AT}, UserProfile.UID + " = ?",
                new String[]{uid}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    /** UPDATE name and phone after Firebase accepted the change. Returns rows changed. */
    public int updateDetails(@NonNull String uid, @NonNull String fullName, @NonNull String phone) {
        ContentValues v = new ContentValues();
        v.put(UserProfile.FULL_NAME, fullName);
        v.put(UserProfile.PHONE, phone);
        v.put(UserProfile.UPDATED_AT, System.currentTimeMillis());
        return update(uid, v);
    }

    /** UPDATE the photo; pass null to remove it. */
    public int updatePhoto(@NonNull String uid, @Nullable String photoBase64) {
        ContentValues v = new ContentValues();
        if (photoBase64 == null) v.putNull(UserProfile.PHOTO);
        else v.put(UserProfile.PHOTO, photoBase64);
        v.put(UserProfile.UPDATED_AT, System.currentTimeMillis());
        return update(uid, v);
    }

    /** DELETE this user's saved copy. */
    public int delete(@NonNull String uid) {
        return helper.getWritableDatabase()
                .delete(UserProfile.TABLE, UserProfile.UID + " = ?", new String[]{uid});
    }

    private int update(@NonNull String uid, @NonNull ContentValues values) {
        return helper.getWritableDatabase()
                .update(UserProfile.TABLE, values, UserProfile.UID + " = ?", new String[]{uid});
    }
}

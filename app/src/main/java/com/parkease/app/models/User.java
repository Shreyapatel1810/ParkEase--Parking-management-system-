package com.parkease.app.models;

import androidx.annotation.NonNull;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.parkease.app.utils.Constants;

/**
 * A ParkEase user profile, stored in Firebase Realtime Database at  users/{uid}.
 *
 * Firebase converts between this class and the database automatically, which is why it has:
 *  - a public empty constructor
 *  - public getters and setters for every saved field
 *
 * The PASSWORD IS NOT HERE: passwords are handled only by Firebase Authentication,
 * which stores them securely (hashed) on Google's servers. ParkEase never saves them.
 *
 * Database shape:
 * <pre>
 * users/{uid}: { uid, fullName, email, phone, role, blocked, createdAt, updatedAt?, photo? }
 * </pre>
 */
@IgnoreExtraProperties
public class User {

    private String uid;
    private String fullName;
    private String email;
    private String phone;
    private String role;        // "customer" or "admin" – admin is set only in the Firebase console
    private boolean blocked;    // set by an admin to stop a user from logging in
    private long createdAt;     // server time in milliseconds
    private long updatedAt;     // server time of the last profile edit (0 = never edited)
    private String photo;       // small JPEG encoded as Base64 text, or null for no photo

    /** Required by Firebase. */
    public User() { }

    public User(String uid, String fullName, String email, String phone, String role) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    // ------------------------------------------------------------------ getters / setters

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    // ------------------------------------------------------------------ helpers (not saved)

    @Exclude
    public boolean hasPhoto() {
        return photo != null && !photo.isEmpty();
    }

    @Exclude
    public boolean isAdmin() {
        return Constants.ROLE_ADMIN.equals(role);
    }

    /** First name for greetings, e.g. "Rahul" from "Rahul Shah". */
    @Exclude
    @NonNull
    public String getFirstName() {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        return fullName.trim().split("\\s+")[0];
    }

    /** Up to two initials for avatars, e.g. "RS". */
    @Exclude
    @NonNull
    public String getInitials() {
        if (fullName == null || fullName.trim().isEmpty()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }
}

package com.parkease.app.models;

import androidx.annotation.NonNull;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * A customer review of a parking place, stored at  reviews/{locationId}/{reviewerUid}.
 * Using the reviewer's uid as the key means each customer can have only ONE review per
 * place (writing again just updates it).
 */
@IgnoreExtraProperties
public class Review {

    @Exclude
    private String reviewerId;    // the Firebase key

    private String userName;
    private int rating;           // 1 to 5 stars
    private String comment;
    private long createdAt;

    /** Required by Firebase. */
    public Review() { }

    @Exclude public String getReviewerId() { return reviewerId; }
    @Exclude public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    /** "RS" from "Rahul Shah" (for the round avatar). */
    @Exclude
    @NonNull
    public String getInitials() {
        if (userName == null || userName.trim().isEmpty()) return "?";
        String[] parts = userName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }
}

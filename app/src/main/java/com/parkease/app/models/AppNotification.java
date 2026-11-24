package com.parkease.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * A message for one user, stored at  notifications/{uid}/{notifId}.
 * type: BOOKING / REMINDER / CANCEL / PAYMENT / SYSTEM
 */
@IgnoreExtraProperties
public class AppNotification {

    @Exclude
    private String id;

    private String title;
    private String message;
    private String type;
    private boolean isRead;
    private long createdAt;

    /** Required by Firebase. */
    public AppNotification() { }

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    // Firebase field name is "isRead"
    @com.google.firebase.database.PropertyName("isRead")
    public boolean isRead() { return isRead; }

    @com.google.firebase.database.PropertyName("isRead")
    public void setRead(boolean read) { isRead = read; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

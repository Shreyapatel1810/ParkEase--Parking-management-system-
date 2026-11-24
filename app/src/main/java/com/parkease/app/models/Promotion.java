package com.parkease.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * An offer or announcement shown on the customer home screen,
 * stored at  promotions/{promoId}. Admins manage these.
 */
@IgnoreExtraProperties
public class Promotion {

    @Exclude
    private String id;

    private String title;
    private String message;
    private String code;       // optional promo code, e.g. "FIRST20"
    private boolean active;
    private int priority;      // higher shows first

    /** Required by Firebase. */
    public Promotion() { }

    @Exclude public String getId() { return id; }
    @Exclude public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}

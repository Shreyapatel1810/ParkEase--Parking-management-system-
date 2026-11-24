package com.parkease.app.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.parkease.app.utils.Constants;

/**
 * One 30-minute block of one slot that someone has taken, stored at
 * slotSchedule/{locationId}/{yyyyMMdd}/{slotId}/{HHmm}.
 *
 * status HOLD   = reserved for a few minutes while the customer pays (expires at holdUntil)
 * status BOOKED = paid / confirmed booking
 */
@IgnoreExtraProperties
public class ScheduleBlock {

    private String bookingId;
    private String userId;
    private String status;
    private long holdUntil;

    /** Required by Firebase. */
    public ScheduleBlock() { }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getHoldUntil() { return holdUntil; }
    public void setHoldUntil(long holdUntil) { this.holdUntil = holdUntil; }

    /** Still blocks the slot? (confirmed, or a hold that hasn't expired yet) */
    public boolean isActive(long now) {
        if (Constants.SCHEDULE_BOOKED.equals(status)) return true;
        return Constants.SCHEDULE_HOLD.equals(status) && holdUntil > now;
    }
}

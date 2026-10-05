package com.trainconcierge.timeline;

/**
 * Enumeration of all timeline and audit event types for a Journey.
 *
 * <p>Phase 18 — Journey Timeline & Audit History Module.</p>
 */
public enum TimelineEventType {
    BOOKING_CREATED,
    HOTEL_ADDED,
    CAB_ADDED,
    SEAT_ALERT_GENERATED,
    TRAIN_STATUS_CHANGED,
    DISRUPTION_DETECTED,
    RECOMMENDATION_GENERATED,
    REBOOKING_COMPLETED,
    HOTEL_RESCHEDULED,
    CAB_RESCHEDULED,
    NOTIFICATION_GENERATED
}

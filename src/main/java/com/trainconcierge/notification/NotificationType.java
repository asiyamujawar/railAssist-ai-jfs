package com.trainconcierge.notification;

/**
 * Category of notification sent to a user.
 *
 * <p>Phase 17 — added HOTEL_*, CAB_*, and additional REBOOKING_* types
 * to support the full notification lifecycle across all services.</p>
 */
public enum NotificationType {
    // Train & Disruption
    DISRUPTION_ALERT,           // train delayed / cancelled
    REBOOKING_SUGGESTION,       // alternative route suggested
    REBOOKING_CONFIRMED,        // new booking confirmed after disruption

    // Seat Alerts
    SEAT_AVAILABLE,             // watched seat became available

    // Booking Lifecycle
    BOOKING_CONFIRMED,          // booking payment confirmed
    BOOKING_CANCELLED,          // booking cancelled

    // Hotel
    HOTEL_BOOKING_CONFIRMED,    // hotel reservation created
    HOTEL_BOOKING_CANCELLED,    // hotel reservation cancelled
    HOTEL_RESCHEDULED,          // hotel check-in/out rescheduled

    // Cab
    CAB_BOOKING_CONFIRMED,      // cab/taxi pickup booked
    CAB_BOOKING_CANCELLED,      // cab booking cancelled
    CAB_RESCHEDULED,            // cab pickup rescheduled

    // Journey
    JOURNEY_REMINDER,           // upcoming journey reminder

    // General
    GENERAL                     // miscellaneous system notification
}

package com.trainconcierge.booking;

/**
 * Lifecycle states of a Booking.
 */
public enum BookingStatus {
    PENDING,        // created, awaiting payment
    CONFIRMED,      // payment successful
    CANCELLED,      // cancelled by user or system
    REFUNDED,       // refund processed
    COMPLETED,      // journey completed
    REBOOKED        // replaced by a new booking after disruption
}

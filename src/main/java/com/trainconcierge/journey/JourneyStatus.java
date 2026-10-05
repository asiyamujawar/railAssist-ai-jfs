package com.trainconcierge.journey;

/**
 * Overall status of a Journey (which may span multiple legs / bookings).
 */
public enum JourneyStatus {
    PLANNED,        // future journey, not yet started
    IN_PROGRESS,    // passenger is currently travelling
    DISRUPTED,      // active disruption affecting this journey
    COMPLETED,      // journey finished successfully
    CANCELLED,      // entire journey cancelled
    REBOOKED        // alternative arrangements made after disruption
}

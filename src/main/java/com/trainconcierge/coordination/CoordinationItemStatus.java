package com.trainconcierge.coordination;

/**
 * Status of individual travel components (Hotel, Cab) during travel coordination.
 */
public enum CoordinationItemStatus {
    /** Component reservation was successfully updated/rescheduled. */
    SUCCESS,

    /** No existing active reservation found for this journey — no update required. */
    NO_RESERVATION,

    /** Reschedule/update attempt failed (e.g. invalid dates, provider rejection). */
    FAILED,

    /** Component update was skipped (e.g. due to prior failure or configuration). */
    SKIPPED
}

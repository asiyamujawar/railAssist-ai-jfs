package com.trainconcierge.rebooking;

/**
 * Status of an autonomous or manual rebooking attempt.
 */
public enum RebookingStatus {
    PENDING,            // triggered, waiting for processing
    AWAITING_CONSENT,   // option presented to passenger, waiting approval
    IN_PROGRESS,        // rebooking being executed
    COMPLETED,          // new booking confirmed
    DECLINED,           // passenger declined the suggested rebooking
    FAILED,             // rebooking attempt failed (no alternatives, etc.)
    EXPIRED             // consent window passed without response
}

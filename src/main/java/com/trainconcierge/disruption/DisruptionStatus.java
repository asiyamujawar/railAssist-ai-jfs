package com.trainconcierge.disruption;

/**
 * Lifecycle state of a disruption event.
 */
public enum DisruptionStatus {
    /** Newly detected disruption event, awaiting processing/recommendation. */
    DETECTED,

    /** Active workflow is evaluating recommendations or processing rebooking. */
    PROCESSING,

    /** Disruption has been handled or train service returned to normal. */
    RESOLVED,

    /** Automated handling failed and requires manual intervention. */
    FAILED
}

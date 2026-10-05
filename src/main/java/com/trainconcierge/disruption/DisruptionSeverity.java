package com.trainconcierge.disruption;

/**
 * Severity level of a disruption — drives rebooking and notification urgency.
 */
public enum DisruptionSeverity {
    LOW,      // minor delay < 15 min
    MEDIUM,   // delay 15–60 min
    HIGH,     // delay > 60 min or partial cancellation
    CRITICAL  // full cancellation or major incident
}

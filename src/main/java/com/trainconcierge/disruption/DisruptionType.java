package com.trainconcierge.disruption;

/**
 * Classifies the cause of a train disruption.
 */
public enum DisruptionType {
    DELAY,
    CANCELLATION,
    DIVERSION,
    PARTIAL_CANCELLATION,
    PLATFORM_CHANGE,
    SPEED_RESTRICTION,
    ENGINEERING_WORKS,
    WEATHER,
    INCIDENT,
    UNKNOWN
}

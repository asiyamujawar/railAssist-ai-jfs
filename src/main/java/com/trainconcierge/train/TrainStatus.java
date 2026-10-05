package com.trainconcierge.train;

/**
 * Operational status of a Train at a point in time.
 */
public enum TrainStatus {
    ON_TIME,
    DELAYED,
    CANCELLED,
    PLATFORM_CHANGED,
    DIVERTED,
    ARRIVED,
    DEPARTED,
    UNKNOWN
}

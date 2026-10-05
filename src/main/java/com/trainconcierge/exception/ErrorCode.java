package com.trainconcierge.exception;

/**
 * Machine-readable error codes returned in every error response.
 *
 * Clients use these codes to drive UI behaviour (e.g. redirect to login on
 * AUTH_TOKEN_INVALID) without parsing message strings.
 *
 * Naming convention: DOMAIN_REASON (screaming snake-case).
 */
public enum ErrorCode {

    // ── Resource ──────────────────────────────────────────────────────────
    RESOURCE_NOT_FOUND,
    RESOURCE_ALREADY_EXISTS,

    // ── Validation ────────────────────────────────────────────────────────
    VALIDATION_FAILED,
    INVALID_REQUEST_BODY,
    CONSTRAINT_VIOLATION,

    // ── Business ──────────────────────────────────────────────────────────
    BOOKING_ALREADY_CANCELLED,
    BOOKING_NOT_CANCELLABLE,
    SEAT_NOT_AVAILABLE,
    INSUFFICIENT_SEATS,
    PASSENGER_COUNT_INVALID,
    BOOKING_NOT_OWNER,
    SCHEDULE_CANCELLED,
    DISRUPTION_ALREADY_RESOLVED,
    INVALID_OPERATION,
    HOTEL_INVALID_DATES,
    HOTEL_NOT_FOUND,
    HOTEL_NOT_OWNER,
    HOTEL_RESCHEDULE_NO_CHANGES,
    CAB_INVALID_PICKUP_TIME,
    CAB_NOT_FOUND,
    CAB_NOT_OWNER,
    CAB_RESCHEDULE_NO_CHANGES,
    SIMULATION_INVALID_DELAY,
    SIMULATION_INVALID_STATUS,
    SEAT_ALERT_NOT_FOUND,
    SEAT_ALERT_NOT_OWNER,
    SEAT_ALERT_ALREADY_EXISTS,
    SEAT_ALERT_ALREADY_INACTIVE,
    DISRUPTION_NOT_OWNER,
    DISRUPTION_ALREADY_REBOOKED,
    RECOMMENDATION_NOT_ELIGIBLE,

    // ── Auth ──────────────────────────────────────────────────────────────
    AUTH_REQUIRED,
    AUTH_TOKEN_INVALID,
    AUTH_TOKEN_EXPIRED,
    ACCESS_DENIED,

    // ── Database / Integrity ──────────────────────────────────────────────
    DATA_INTEGRITY_VIOLATION,
    DATABASE_ERROR,

    // ── Internal ──────────────────────────────────────────────────────────
    INTERNAL_SERVER_ERROR
}

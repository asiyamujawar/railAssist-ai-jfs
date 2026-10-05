package com.trainconcierge.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.trainconcierge.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Structured error response returned by {@link com.trainconcierge.exception.GlobalExceptionHandler}
 * for all non-2xx responses.
 *
 * <p>Fields:</p>
 * <ul>
 *   <li>{@code timestamp}       — UTC instant when the error occurred</li>
 *   <li>{@code status}          — HTTP status code (e.g. 404)</li>
 *   <li>{@code errorCode}       — machine-readable {@link ErrorCode} (e.g. RESOURCE_NOT_FOUND)</li>
 *   <li>{@code message}         — human-readable description safe to show clients</li>
 *   <li>{@code path}            — request URI that triggered the error</li>
 *   <li>{@code validationErrors}— field → message map, present only for validation failures</li>
 * </ul>
 *
 * <p>Internal stack traces, SQL messages, and cause chains are NEVER included.</p>
 *
 * <p>Example — validation failure:</p>
 * <pre>{@code
 * {
 *   "timestamp": "2026-10-01T05:00:00Z",
 *   "status": 400,
 *   "errorCode": "VALIDATION_FAILED",
 *   "message": "Request validation failed. Check validationErrors for details.",
 *   "path": "/api/v1/bookings",
 *   "validationErrors": {
 *     "email": "must be a well-formed email address",
 *     "numberOfSeats": "must be greater than 0"
 *   }
 * }
 * }</pre>
 *
 * <p>Example — resource not found:</p>
 * <pre>{@code
 * {
 *   "timestamp": "2026-10-01T05:00:00Z",
 *   "status": 404,
 *   "errorCode": "RESOURCE_NOT_FOUND",
 *   "message": "Booking not found with bookingReference: 'TC-20261001-9999'",
 *   "path": "/api/v1/bookings/TC-20261001-9999"
 * }
 * }</pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private final Instant timestamp = Instant.now();

    private final int status;

    private final ErrorCode errorCode;

    private final String message;

    private final String path;

    /** Present only when errorCode == VALIDATION_FAILED or CONSTRAINT_VIOLATION. */
    private final Map<String, String> validationErrors;
}

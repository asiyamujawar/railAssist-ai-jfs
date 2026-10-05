package com.trainconcierge.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Generic success response wrapper used across all REST endpoints.
 *
 * <p>All 2xx responses are wrapped in this class. Error responses
 * use {@link ErrorResponse} instead — never this class.</p>
 *
 * <p>Example — single resource:</p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "message": "Booking confirmed.",
 *   "data": { "bookingReference": "TC-20261001-0001", "status": "CONFIRMED" },
 *   "timestamp": "2026-10-01T05:00:00Z"
 * }
 * }</pre>
 *
 * <p>Example — list:</p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": [ { ... }, { ... } ],
 *   "timestamp": "2026-10-01T05:00:00Z"
 * }
 * }</pre>
 *
 * @param <T> the type of the {@code data} payload
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;

    private final String message;

    private final T data;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    // ─── Factory methods ──────────────────────────────────────────────────

    /** 200 OK with data only. */
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    /** 200 OK with message and data. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /** 200 OK with message only (e.g. delete confirmation). */
    public static <T> ApiResponse<T> ok(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    /** 201 Created with message and data. */
    public static <T> ApiResponse<T> created(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }
}

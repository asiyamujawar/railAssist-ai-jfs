package com.trainconcierge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a domain business rule is violated — more specific than
 * {@link BadRequestException} because it always carries a typed {@link ErrorCode}.
 * Maps to HTTP 422 Unprocessable Entity.
 *
 * <p>Use for rules like "cannot rebook an already rebooked booking",
 * "cannot cancel a completed journey", "disruption already resolved".</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * throw new BusinessRuleException(
 *     "Booking TC-001 has already been cancelled.",
 *     ErrorCode.BOOKING_ALREADY_CANCELLED);
 * }</pre>
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessRuleException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

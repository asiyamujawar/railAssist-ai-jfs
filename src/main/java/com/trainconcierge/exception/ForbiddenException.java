package com.trainconcierge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an authenticated user attempts an action they are not
 * permitted to perform (e.g. accessing another user's booking).
 * Maps to HTTP 403 Forbidden.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * throw new ForbiddenException("You do not have permission to cancel this booking.");
 * }</pre>
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.ACCESS_DENIED;

    public ForbiddenException(String message) {
        super(message);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

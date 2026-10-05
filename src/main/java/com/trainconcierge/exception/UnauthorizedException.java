package com.trainconcierge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a request is made without valid authentication credentials.
 * Maps to HTTP 401 Unauthorized.
 *
 * <p>Distinct from {@link ForbiddenException} (403) which means authenticated
 * but lacking permission.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * throw new UnauthorizedException("Authentication token is missing or invalid.");
 * throw new UnauthorizedException("AUTH_TOKEN_EXPIRED", ErrorCode.AUTH_TOKEN_EXPIRED);
 * }</pre>
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    private final ErrorCode errorCode;

    public UnauthorizedException(String message) {
        super(message);
        this.errorCode = ErrorCode.AUTH_REQUIRED;
    }

    public UnauthorizedException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.trainconcierge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an attempt is made to create a resource that already exists
 * (e.g. registering a user with an email that is already taken).
 * Maps to HTTP 409 Conflict.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * throw new DuplicateResourceException("User", "email", "user@example.com");
 * throw new DuplicateResourceException("Train number '12301' is already registered.");
 * }</pre>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.RESOURCE_ALREADY_EXISTS;

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

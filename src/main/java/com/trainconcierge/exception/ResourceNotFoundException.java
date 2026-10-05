package com.trainconcierge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource does not exist in the system.
 * Maps to HTTP 404 Not Found.
 *
 * <p>Usage examples:</p>
 * <pre>{@code
 * throw new ResourceNotFoundException("Booking", "bookingReference", "TC-9999");
 * throw new ResourceNotFoundException("Train not found with id: 42");
 * }</pre>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

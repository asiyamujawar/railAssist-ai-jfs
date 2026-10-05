package com.trainconcierge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the client sends a syntactically valid but semantically
 * incorrect request (e.g. booking a cancelled train).
 * Maps to HTTP 400 Bad Request.
 *
 * <p>Use this for business-rule violations caught before persistence.
 * For bean-validation failures use {@code @Valid} + {@code MethodArgumentNotValidException}.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * throw new BadRequestException("Cannot book a cancelled schedule.");
 * throw new BadRequestException("Seat count must be between 1 and 6.", ErrorCode.SEAT_NOT_AVAILABLE);
 * }</pre>
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    private final ErrorCode errorCode;

    public BadRequestException(String message) {
        super(message);
        this.errorCode = ErrorCode.INVALID_OPERATION;
    }

    public BadRequestException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

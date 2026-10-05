package com.trainconcierge.exception;

import com.trainconcierge.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>Design principles:</p>
 * <ul>
 *   <li>Every response uses {@link ErrorResponse} — no raw strings, no Spring
 *       default error objects.</li>
 *   <li>Internal details (stack traces, SQL messages, cause chains) are NEVER
 *       included in the response body.</li>
 *   <li>All exceptions are logged with full context at the appropriate level:
 *       WARN for client errors (4xx), ERROR for server errors (5xx).</li>
 *   <li>The request path is captured via {@link HttpServletRequest} and
 *       always included in the response for traceability.</li>
 * </ul>
 *
 * <p>Handler precedence (most specific → least specific):</p>
 * <ol>
 *   <li>Domain exceptions: {@link ResourceNotFoundException}, {@link DuplicateResourceException},
 *       {@link BadRequestException}, {@link BusinessRuleException},
 *       {@link UnauthorizedException}, {@link ForbiddenException}</li>
 *   <li>Spring Security: {@link AuthenticationException}, {@link AccessDeniedException}</li>
 *   <li>Spring MVC: {@link MethodArgumentNotValidException},
 *       {@link HttpMessageNotReadableException}</li>
 *   <li>Jakarta Validation: {@link ConstraintViolationException}</li>
 *   <li>Spring Data: {@link DataIntegrityViolationException}</li>
 *   <li>Catch-all: {@link Exception}</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─────────────────────────────────────────────────────────────────────
    // Domain exceptions
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {

        log.warn("Duplicate resource [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex, HttpServletRequest request) {

        log.warn("Bad request [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {

        log.warn("Business rule violation [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {

        log.warn("Unauthorized access [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {

        log.warn("Forbidden [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Spring Security exceptions
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED,
                "Authentication is required to access this resource.", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action.", request, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation — @Valid on @RequestBody
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = (error instanceof FieldError fe) ? fe.getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });

        log.warn("Validation failed [{}]: {}", request.getRequestURI(), errors);
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Request validation failed. Check validationErrors for details.", request, errors);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation — @Validated on @PathVariable / @RequestParam
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            String path = cv.getPropertyPath().toString();
            // Strip method prefix: "methodName.paramName" → "paramName"
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.put(field, cv.getMessage());
        }

        log.warn("Constraint violation [{}]: {}", request.getRequestURI(), errors);
        return build(HttpStatus.BAD_REQUEST, ErrorCode.CONSTRAINT_VIOLATION,
                "One or more parameters failed validation.", request, errors);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Malformed JSON / unreadable request body
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        // Log at DEBUG — the cause is always client error, never expose the parse detail
        log.debug("Malformed request body [{}]: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST_BODY,
                "Request body is missing or malformed. Please check your JSON syntax.", request, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Missing required query / path parameters
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing required parameter [{}]: {}", request.getRequestURI(), ex.getParameterName());

        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(ex.getParameterName(),
                "Required parameter '" + ex.getParameterName() + "' is missing.");

        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Required request parameter is missing.", request, errors);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Database integrity violations (unique constraints, FK violations)
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        // Log full cause internally — never expose SQL to client
        log.error("Data integrity violation [{}]", request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, ErrorCode.DATA_INTEGRITY_VIOLATION,
                "The request conflicts with existing data. A duplicate or required value may be missing.",
                request, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Catch-all — never expose internal details to clients
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception [{}]", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later or contact support.",
                request, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Builder helper
    // ─────────────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors) {

        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}

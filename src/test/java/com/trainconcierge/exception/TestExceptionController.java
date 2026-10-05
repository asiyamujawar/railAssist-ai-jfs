package com.trainconcierge.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

/**
 * Minimal controller used ONLY by {@link GlobalExceptionHandlerTest}.
 * Each endpoint deliberately throws a specific exception to exercise the handler.
 * This class is in src/test — it is never loaded in production.
 */
@RestController
@RequestMapping("/test")
class TestExceptionController {

    @GetMapping("/not-found")
    void notFound() {
        throw new ResourceNotFoundException("Booking", "bookingReference", "TC-9999");
    }

    @GetMapping("/duplicate")
    void duplicate() {
        throw new DuplicateResourceException("User", "email", "existing@example.com");
    }

    @GetMapping("/bad-request")
    void badRequest() {
        throw new BadRequestException("Cannot book a cancelled schedule.");
    }

    @GetMapping("/business-rule")
    void businessRule() {
        throw new BusinessRuleException(
                "Booking TC-001 has already been cancelled.",
                ErrorCode.BOOKING_ALREADY_CANCELLED);
    }

    @GetMapping("/unauthorized")
    void unauthorized() {
        throw new UnauthorizedException("Authentication token is missing.");
    }

    @GetMapping("/forbidden")
    void forbidden() {
        throw new ForbiddenException("You cannot access another user's booking.");
    }

    @GetMapping("/internal-error")
    void internalError() {
        throw new NullPointerException("internal detail that must never reach the client");
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody ValidationRequest request) {
        // body is intentionally empty — validation fires before this is reached
    }

    // ── Inner DTO for validation tests ────────────────────────────────────

    @Data
    static class ValidationRequest {

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        private String email;

        @Min(value = 1, message = "Number of seats must be at least 1")
        private int numberOfSeats;
    }
}

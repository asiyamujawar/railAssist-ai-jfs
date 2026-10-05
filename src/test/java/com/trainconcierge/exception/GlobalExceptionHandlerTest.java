package com.trainconcierge.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.common.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * Uses a minimal test controller ({@link TestExceptionController}) that deliberately
 * throws each exception type. No database, no security, no full context required.
 *
 * Verified scenarios:
 * - 404  ResourceNotFoundException
 * - 409  DuplicateResourceException
 * - 400  BadRequestException
 * - 422  BusinessRuleException
 * - 401  UnauthorizedException
 * - 403  ForbiddenException
 * - 400  MethodArgumentNotValidException (bean validation)
 * - 400  HttpMessageNotReadableException (malformed JSON)
 * - 500  Generic Exception (internal server error — no details exposed)
 */
@WebMvcTest(
    controllers = TestExceptionController.class,
    excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            OAuth2ClientAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.trainconcierge\\.auth\\..*"
    )
)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────
    // 404 — Resource Not Found
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /test/not-found → 404 with RESOURCE_NOT_FOUND errorCode")
    void handleResourceNotFoundException_returns404() throws Exception {
        String body = mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(404);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(resp.getMessage()).contains("Booking");
        assertThat(resp.getPath()).isEqualTo("/test/not-found");
        assertThat(resp.getTimestamp()).isNotNull();
        assertThat(resp.getValidationErrors()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 409 — Duplicate Resource
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /test/duplicate → 409 with RESOURCE_ALREADY_EXISTS errorCode")
    void handleDuplicateResourceException_returns409() throws Exception {
        String body = mockMvc.perform(get("/test/duplicate"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(409);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 400 — Bad Request
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /test/bad-request → 400 with INVALID_OPERATION errorCode")
    void handleBadRequestException_returns400() throws Exception {
        String body = mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(400);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.INVALID_OPERATION);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 422 — Business Rule
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /test/business-rule → 422 with BOOKING_ALREADY_CANCELLED errorCode")
    void handleBusinessRuleException_returns422() throws Exception {
        String body = mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(422);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.BOOKING_ALREADY_CANCELLED);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 401 — Unauthorized
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /test/unauthorized → 401 with AUTH_REQUIRED errorCode")
    void handleUnauthorizedException_returns401() throws Exception {
        String body = mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 403 — Forbidden
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /test/forbidden → 403 with ACCESS_DENIED errorCode")
    void handleForbiddenException_returns403() throws Exception {
        String body = mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 400 — Validation (@Valid on @RequestBody)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /test/validate with missing fields → 400 VALIDATION_FAILED with field errors")
    void handleMethodArgumentNotValidException_returns400WithFieldErrors() throws Exception {
        // Send a body with an empty email and zero seats — both violate @NotBlank / @Min
        String requestBody = """
                {
                  "email": "",
                  "numberOfSeats": 0
                }
                """;

        String body = mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(400);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(resp.getValidationErrors()).isNotNull();
        assertThat(resp.getValidationErrors()).containsKey("email");
        assertThat(resp.getValidationErrors()).containsKey("numberOfSeats");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 400 — Malformed JSON
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /test/validate with malformed JSON → 400 INVALID_REQUEST_BODY")
    void handleHttpMessageNotReadable_returns400() throws Exception {
        String body = mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(400);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST_BODY);
        // Internal parse details must NOT be exposed
        assertThat(resp.getMessage()).doesNotContain("com.fasterxml");
        assertThat(resp.getMessage()).doesNotContain("JsonParseException");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 500 — Unexpected internal error (no details exposed)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /test/internal-error → 500 with no internal detail exposed")
    void handleGenericException_returns500WithoutInternalDetails() throws Exception {
        String body = mockMvc.perform(get("/test/internal-error"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getStatus()).isEqualTo(500);
        assertThat(resp.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        // The NullPointerException message must NOT appear in the response
        assertThat(resp.getMessage()).doesNotContain("NullPointerException");
        assertThat(resp.getMessage()).doesNotContain("null");
        assertThat(resp.getMessage()).contains("unexpected error");
        assertThat(resp.getValidationErrors()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Structural contract — all error responses have required fields
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All error responses include timestamp, status, errorCode, message, path")
    void allErrorResponses_haveRequiredFields() throws Exception {
        String body = mockMvc.perform(get("/test/not-found"))
                .andReturn().getResponse().getContentAsString();

        ErrorResponse resp = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(resp.getTimestamp()).isNotNull();
        assertThat(resp.getStatus()).isPositive();
        assertThat(resp.getErrorCode()).isNotNull();
        assertThat(resp.getMessage()).isNotBlank();
        assertThat(resp.getPath()).isNotBlank();
    }
}

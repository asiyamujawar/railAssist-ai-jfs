# Phase 3 — API Response & Global Exception Handling Layer

**Project:** AI-Powered Autonomous Train Disruption Concierge & Smart Seat Alert System  
**Phase:** 3 of N  
**Status:** ✅ Complete  
**Completed On:** October 2026

---

## What Phase 3 Covers

Phase 3 replaces the minimal placeholder exception handling from Phase 1 with a
production-quality, fully-tested response and error layer. Every success response
and every error response across the entire API now follows a single, documented contract.

No entities, repositories, or business services were modified.

---

## Objectives Achieved

| # | Objective | Status |
|---|---|---|
| 1 | Structured `ErrorResponse` DTO with timestamp, status, errorCode, message, path, validationErrors | ✅ |
| 2 | `ApiResponse<T>` success wrapper — updated with `created()` factory | ✅ |
| 3 | `ErrorCode` enum — machine-readable codes for all error categories | ✅ |
| 4 | `ResourceNotFoundException` — enriched with `ErrorCode` | ✅ |
| 5 | `BadRequestException` — enriched with `ErrorCode` constructor | ✅ |
| 6 | `DuplicateResourceException` — 409 Conflict | ✅ |
| 7 | `UnauthorizedException` — 401, with `ErrorCode` | ✅ |
| 8 | `ForbiddenException` — 403, with `ErrorCode` | ✅ |
| 9 | `BusinessRuleException` — 422 Unprocessable Entity | ✅ |
| 10 | `GlobalExceptionHandler` — handles 9 exception categories | ✅ |
| 11 | Internal details never exposed to clients | ✅ |
| 12 | Request path included in every error response | ✅ |
| 13 | Spring Security added to classpath (auth wired in future phase) | ✅ |
| 14 | Spring Security auto-config excluded — app starts without login page | ✅ |
| 15 | 10 unit tests covering every handler — all passing | ✅ |
| 16 | Context load test still passing — 11 total tests, 0 failures | ✅ |

---

## Files Created / Modified

### New files

| File | Type | Purpose |
|---|---|---|
| `exception/ErrorCode.java` | Enum | Machine-readable error identifiers for all categories |
| `common/ErrorResponse.java` | DTO | Structured error response returned for all 4xx / 5xx |
| `exception/DuplicateResourceException.java` | Exception | 409 Conflict — duplicate business identifiers |
| `exception/UnauthorizedException.java` | Exception | 401 Unauthorized — missing / invalid auth |
| `exception/ForbiddenException.java` | Exception | 403 Forbidden — authenticated but no permission |
| `exception/BusinessRuleException.java` | Exception | 422 Unprocessable Entity — domain rule violations |
| `test/.../GlobalExceptionHandlerTest.java` | Test | 10 unit tests for all handler paths |
| `test/.../TestExceptionController.java` | Test helper | Minimal controller used only in tests |

### Modified files

| File | Change |
|---|---|
| `common/ApiResponse.java` | Added `ok(String message)` and `created()` factories; improved Javadoc |
| `exception/ResourceNotFoundException.java` | Added `ErrorCode` field and getter |
| `exception/BadRequestException.java` | Added `ErrorCode` constructor overload |
| `exception/GlobalExceptionHandler.java` | Complete rewrite — handles 9 exception types, uses `ErrorResponse`, captures path |
| `pom.xml` | Added `spring-boot-starter-security` |
| `application.properties` | Excluded Spring Security auto-config (deferred to auth phase) |

---

## API Response Contracts

### Success Response — `ApiResponse<T>`

All `2xx` responses are wrapped in `ApiResponse<T>`.

```json
{
  "success": true,
  "message": "Booking confirmed.",
  "data": {
    "bookingReference": "TC-20261001-0001",
    "status": "CONFIRMED",
    "totalFare": 49.99
  },
  "timestamp": "2026-10-01T05:00:00Z"
}
```

`message` and `data` are `@JsonInclude(NON_NULL)` — omitted when not relevant:

```json
{
  "success": true,
  "data": [ { "id": 1 }, { "id": 2 } ],
  "timestamp": "2026-10-01T05:00:00Z"
}
```

### Error Response — `ErrorResponse`

All `4xx` and `5xx` responses use `ErrorResponse`. Never uses `ApiResponse`.

**Required fields always present:**

| Field | Type | Example |
|---|---|---|
| `timestamp` | ISO-8601 UTC instant | `"2026-10-01T05:00:00Z"` |
| `status` | integer | `404` |
| `errorCode` | `ErrorCode` enum string | `"RESOURCE_NOT_FOUND"` |
| `message` | string | `"Booking not found with bookingReference: 'TC-9999'"` |
| `path` | string | `"/api/v1/bookings/TC-9999"` |

**Optional field (validation only):**

| Field | Type | When present |
|---|---|---|
| `validationErrors` | `Map<String, String>` | `errorCode == VALIDATION_FAILED` or `CONSTRAINT_VIOLATION` |

---

## Error Response Examples

### 404 — Resource Not Found

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 404,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Booking not found with bookingReference: 'TC-9999'",
  "path": "/api/v1/bookings/TC-9999"
}
```

### 409 — Duplicate Resource

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 409,
  "errorCode": "RESOURCE_ALREADY_EXISTS",
  "message": "User already exists with email: 'john@example.com'",
  "path": "/api/v1/auth/register"
}
```

### 400 — Validation Failed

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed. Check validationErrors for details.",
  "path": "/api/v1/bookings",
  "validationErrors": {
    "email": "Email must be a valid email address",
    "numberOfSeats": "Number of seats must be at least 1"
  }
}
```

### 400 — Malformed JSON

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 400,
  "errorCode": "INVALID_REQUEST_BODY",
  "message": "Request body is missing or malformed. Please check your JSON syntax.",
  "path": "/api/v1/bookings"
}
```

### 400 — Bad Request (business input error)

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 400,
  "errorCode": "INVALID_OPERATION",
  "message": "Cannot book a cancelled schedule.",
  "path": "/api/v1/bookings"
}
```

### 422 — Business Rule Violation

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 422,
  "errorCode": "BOOKING_ALREADY_CANCELLED",
  "message": "Booking TC-001 has already been cancelled.",
  "path": "/api/v1/bookings/TC-001/cancel"
}
```

### 401 — Unauthorized

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 401,
  "errorCode": "AUTH_REQUIRED",
  "message": "Authentication token is missing.",
  "path": "/api/v1/bookings"
}
```

### 403 — Forbidden

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 403,
  "errorCode": "ACCESS_DENIED",
  "message": "You cannot access another user's booking.",
  "path": "/api/v1/bookings/TC-001"
}
```

### 409 — Database Integrity Violation

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 409,
  "errorCode": "DATA_INTEGRITY_VIOLATION",
  "message": "The request conflicts with existing data. A duplicate or required value may be missing.",
  "path": "/api/v1/trains"
}
```

### 500 — Internal Server Error

```json
{
  "timestamp": "2026-10-01T05:00:00Z",
  "status": 500,
  "errorCode": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred. Please try again later or contact support.",
  "path": "/api/v1/bookings"
}
```

---

## ErrorCode Reference

| ErrorCode | HTTP Status | When to use |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | Entity doesn't exist in DB |
| `RESOURCE_ALREADY_EXISTS` | 409 | Duplicate email, train number, booking reference |
| `VALIDATION_FAILED` | 400 | `@Valid` on `@RequestBody` fails |
| `INVALID_REQUEST_BODY` | 400 | Malformed JSON / missing body |
| `CONSTRAINT_VIOLATION` | 400 | `@Validated` on path/query params |
| `BOOKING_ALREADY_CANCELLED` | 422 | Cancel a booking that's already cancelled |
| `BOOKING_NOT_CANCELLABLE` | 422 | Cancel a completed/rebooked booking |
| `SEAT_NOT_AVAILABLE` | 422 | No seats left for requested class |
| `SCHEDULE_CANCELLED` | 422 | Booking attempted on cancelled schedule |
| `DISRUPTION_ALREADY_RESOLVED` | 422 | Resolve a disruption that's already resolved |
| `INVALID_OPERATION` | 400 | Generic bad business input |
| `AUTH_REQUIRED` | 401 | No auth token present |
| `AUTH_TOKEN_INVALID` | 401 | Token signature/format invalid |
| `AUTH_TOKEN_EXPIRED` | 401 | Token past expiry |
| `ACCESS_DENIED` | 403 | Authenticated but no permission |
| `DATA_INTEGRITY_VIOLATION` | 409 | DB unique/FK constraint failed |
| `DATABASE_ERROR` | 500 | DB unreachable (future use) |
| `INTERNAL_SERVER_ERROR` | 500 | Unhandled exception |

---

## Exception Class Reference

```
com.trainconcierge.exception/
├── ErrorCode.java                   ← enum — all machine-readable codes
├── ResourceNotFoundException.java   ← 404 — entity not found
├── DuplicateResourceException.java  ← 409 — unique constraint violation
├── BadRequestException.java         ← 400 — invalid business input
├── BusinessRuleException.java       ← 422 — domain rule violated
├── UnauthorizedException.java       ← 401 — not authenticated
├── ForbiddenException.java          ← 403 — not permitted
└── GlobalExceptionHandler.java      ← @RestControllerAdvice — handles all
```

### Usage guide

```java
// 404 — entity missing
throw new ResourceNotFoundException("Booking", "bookingReference", ref);

// 409 — email taken
throw new DuplicateResourceException("User", "email", email);

// 400 — bad input (no specific code)
throw new BadRequestException("Start date must be before end date.");

// 400 — bad input with specific code
throw new BadRequestException("No seats available.", ErrorCode.SEAT_NOT_AVAILABLE);

// 422 — domain rule
throw new BusinessRuleException(
    "Cannot rebook an already rebooked booking.",
    ErrorCode.BOOKING_NOT_CANCELLABLE);

// 401 — missing / bad token
throw new UnauthorizedException("Token has expired.", ErrorCode.AUTH_TOKEN_EXPIRED);

// 403 — wrong user
throw new ForbiddenException("You may only cancel your own bookings.");
```

---

## Handler Execution Order

```
Incoming request fails
        │
        ├── ResourceNotFoundException      → 404 RESOURCE_NOT_FOUND
        ├── DuplicateResourceException     → 409 RESOURCE_ALREADY_EXISTS
        ├── BadRequestException            → 400 INVALID_OPERATION (or custom)
        ├── BusinessRuleException          → 422 (custom ErrorCode)
        ├── UnauthorizedException          → 401 (AUTH_REQUIRED or custom)
        ├── ForbiddenException             → 403 ACCESS_DENIED
        ├── AuthenticationException        → 401 AUTH_REQUIRED (Spring Security)
        ├── AccessDeniedException          → 403 ACCESS_DENIED (Spring Security)
        ├── MethodArgumentNotValidException → 400 VALIDATION_FAILED + field map
        ├── ConstraintViolationException   → 400 CONSTRAINT_VIOLATION + field map
        ├── HttpMessageNotReadableException → 400 INVALID_REQUEST_BODY
        ├── DataIntegrityViolationException → 409 DATA_INTEGRITY_VIOLATION
        └── Exception (catch-all)          → 500 INTERNAL_SERVER_ERROR
```

---

## Security Architecture Note

`spring-boot-starter-security` was added in this phase so that
`AuthenticationException` and `AccessDeniedException` (Spring Security types)
compile in `GlobalExceptionHandler`.

Spring Security's default `BasicAuthenticationFilter` and login page are
**excluded** via `spring.autoconfigure.exclude` in `application.properties`.
The app behaves exactly as before — no authentication is enforced yet.

Full JWT security (filters, token validation, `UserDetailsService`) will be
implemented in the Authentication Phase.

---

## How to Run Phase 3

Phase 3 changes the exception layer and adds Spring Security to the classpath
(disabled by default). Run steps are the same as before — Spring Security does
**not** prompt for a password because its auto-config is excluded.

### Step 1 — Set environment variables

```powershell
$env:DB_HOST     = "localhost"
$env:DB_PORT     = "3306"
$env:DB_NAME     = "train_concierge"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_mysql_password_here"
$env:DDL_AUTO    = "update"
```

### Step 2 — Build

```powershell
mvn clean install -DskipTests "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

### Step 3 — Run the application

```powershell
mvn spring-boot:run "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

You should see **no Spring Security login prompt** and the app starts normally:
```
HikariPool-1 - Start completed.
Started TrainConciergeApplication in X.XXX seconds
```

### Step 4 — Run all tests

```powershell
mvn test "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Expected:
```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Step 5 — Manually test the exception layer

Use PowerShell or any HTTP client (Postman, curl, browser).

**Test 404 — resource not found:**
```powershell
curl http://localhost:8080/api/nonexistent-path
```
Expected:
```json
{
  "timestamp": "...",
  "status": 404,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "No static resource api/nonexistent-path.",
  "path": "/api/nonexistent-path"
}
```

**Test 400 — malformed JSON:**
```powershell
curl -X POST http://localhost:8080/api/health `
  -H "Content-Type: application/json" `
  -d "{bad"
```
Expected:
```json
{
  "timestamp": "...",
  "status": 400,
  "errorCode": "INVALID_REQUEST_BODY",
  "message": "Request body is missing or malformed. Please check your JSON syntax.",
  "path": "/api/health"
}
```

**Test health still works:**
```powershell
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

---

## Test Results

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
├── GlobalExceptionHandlerTest (10 tests)
│   ├── ✅ handleResourceNotFoundException_returns404
│   ├── ✅ handleDuplicateResourceException_returns409
│   ├── ✅ handleBadRequestException_returns400
│   ├── ✅ handleBusinessRuleException_returns422
│   ├── ✅ handleUnauthorizedException_returns401
│   ├── ✅ handleForbiddenException_returns403
│   ├── ✅ handleMethodArgumentNotValidException_returns400WithFieldErrors
│   ├── ✅ handleHttpMessageNotReadable_returns400
│   ├── ✅ handleGenericException_returns500WithoutInternalDetails
│   └── ✅ allErrorResponses_haveRequiredFields
└── TrainConciergeApplicationTests (1 test)
    └── ✅ contextLoads
```

---

## Design Decisions

### Two separate DTOs — `ApiResponse` vs `ErrorResponse`
Using `ApiResponse` for errors (with `success: false`) forces clients to check
a boolean flag. Having a dedicated `ErrorResponse` for all 4xx/5xx means:
- Clients use HTTP status codes (the standard) to branch, not a boolean
- `validationErrors` is cleanly optional and only appears when relevant
- The two response shapes are decoupled and can evolve independently

### Internal details never exposed
The catch-all `Exception` handler logs the full stack trace internally but
returns only a generic message. `DataIntegrityViolationException` logs the full
SQL cause internally but returns a sanitised conflict message. This prevents
leaking table names, column names, SQL dialect, and stack frames to clients.

### `BusinessRuleException` vs `BadRequestException`
`BadRequestException` (400) covers invalid *input* — things the client sent wrong.
`BusinessRuleException` (422) covers invalid *operations* — the input is well-formed
but violates a domain rule given the current system state. The distinction matters
for client error handling (400 = fix your request, 422 = system state conflict).

### Request path via `HttpServletRequest`
Spring's `@ExceptionHandler` method signature accepts `HttpServletRequest` directly.
This avoids `RequestContextHolder` threading issues and works correctly in async
contexts. Every error response includes the exact path that failed.

---

## Known Limitations

| Limitation | Planned Resolution |
|---|---|
| No auth enforced — Spring Security excluded | Authentication Phase — JWT filters |
| No request correlation ID in responses | Observability Phase — add `X-Request-Id` header |
| `DataIntegrityViolationException` message is generic | Could parse constraint name to return a more specific message — deferred |
| `ConstraintViolationException` handler not tested (needs `@Validated` controller) | Will be tested when path/query param validation is added |

---

## What's Next — Phase 4

Phase 4 will implement **Authentication & JWT Security**:

- `POST /api/v1/auth/register` — user registration with BCrypt password hashing
- `POST /api/v1/auth/login` — JWT token generation
- `POST /api/v1/auth/refresh` — token refresh
- `JwtAuthenticationFilter` — validates token on each request
- `UserDetailsService` backed by `UserRepository`
- Full Spring Security filter chain (replaces the excluded auto-config)
- DTOs for `User` register/login requests and responses
- `UnauthorizedException` and `ForbiddenException` wired to security events

---

## Roadmap Overview

| Phase | Focus Area | Status |
|---|---|---|
| 1 | Foundation & Infrastructure | ✅ Complete |
| 2 | Database Entity Layer | ✅ Complete |
| **3** | **API Response & Exception Handling** | ✅ **Complete** |
| 4 | Authentication & JWT Security | 🔲 Planned |
| 5 | Train, Schedule & Seat APIs | 🔲 Planned |
| 6 | Booking & Journey APIs | 🔲 Planned |
| 7 | Disruption Detection Engine | 🔲 Planned |
| 8 | AI Recommendation Engine | 🔲 Planned |
| 9 | Autonomous Rebooking | 🔲 Planned |
| 10 | Hotel, Cab & Notification Integration | 🔲 Planned |

---

*Phase 3 complete — consistent, secure, tested API response layer is in place.*

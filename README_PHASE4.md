# Phase 4 — Authentication & Authorization (Spring Security + JWT)

> **Status:** ✅ Complete — All 27 tests pass (16 auth integration + 10 exception handler + 1 context load).

---

## 📋 What's Implemented in This Phase

| # | Requirement | Status |
|---|---|---|
| 1 | Registration & Login DTOs (`RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserProfileResponse`) | ✅ |
| 2 | User registration with email uniqueness validation | ✅ |
| 3 | Password hashing with BCrypt (strength 12) | ✅ |
| 4 | Login via Spring `AuthenticationManager` | ✅ |
| 5 | Signed JWT access tokens (HMAC-SHA-256 via JJWT 0.12.5) | ✅ |
| 6 | Custom JWT authentication filter (`JwtAuthenticationFilter`) | ✅ |
| 7 | Stateless session management (`SessionCreationPolicy.STATELESS`) | ✅ |
| 8 | Spring Security `UserDetailsService` (`CustomUserDetailsService`) | ✅ |
| 9 | Endpoint protection (permit register/login/docs/health, auth everything else) | ✅ |
| 10 | `GET /api/auth/me` — authenticated user profile | ✅ |
| 11 | Helper method `getCurrentAuthenticatedUser()` for future ownership checks | ✅ |
| 12 | JWT secrets & expiration from environment variables (`JWT_SECRET`, `JWT_EXPIRATION_MS`) | ✅ |
| 13 | No password/token logging (passwords excluded from serialisation; filter only DEBUG-logs messages) | ✅ |
| 14 | Consistent 401/403 responses via `JwtAuthenticationEntryPoint` + `JwtAccessDeniedHandler` | ✅ |
| 15 | 16 integration tests (register, login, invalid creds, expired token, protected endpoints…) | ✅ |

---

## 🏗️ Security Architecture Explained

### Components Overview

```
                    ┌─────────────────────────────────────────────────┐
                    │              HTTP Request                       │
                    └─────────────────────┬───────────────────────────┘
                                          │
                                          ▼
                    ┌─────────────────────────────────────────────────┐
                    │        CorsFilter (CORS preflight)              │
                    └─────────────────────┬───────────────────────────┘
                                          │
                                          ▼
                    ┌─────────────────────────────────────────────────┐
                    │     JwtAuthenticationFilter                     │
                    │  • Extracts "Bearer <token>" from Authorization │
                    │  • Validates signature & expiration via         │
                    │    JwtTokenProvider                              │
                    │  • On valid token → sets Authentication in     │
                    │    SecurityContextHolder                        │
                    │  • On invalid/expired → writes 401 JSON error   │
                    │    directly (short-circuits chain)              │
                    └─────────────────────┬───────────────────────────┘
                                          │
                                          ▼
                    ┌─────────────────────────────────────────────────┐
                    │  UsernamePasswordAuthenticationFilter           │
                    │  (skipped — STATELESS + no form login)          │
                    └─────────────────────┬───────────────────────────┘
                                          │
                                          ▼
                    ┌─────────────────────────────────────────────────┐
                    │  Authorization (HttpSecurity.authorizeRequests) │
                    │  • Whitelisted paths → permitAll                │
                    │  • Everything else → authenticated              │
                    │  • Role checks via hasRole("ADMIN")             │
                    └─────────────────────┬───────────────────────────┘
                                          │
                 ┌────────────────────────┼────────────────────────┐
                 ▼                         ▼                         ▼
    Authentication Entry Point   Access Denied Handler       Your Controller
    (no token at all → 401)     (no permission → 403)        (token OK → 2xx)
```

### Key Design Decisions

**1. Stateless Sessions (`SessionCreationPolicy.STATELESS`)**
- Spring Security never creates an `HttpSession`.
- Every request is authenticated purely from the JWT — no server-side session storage.
- Required for horizontally scalable REST APIs and mobile clients.

**2. BCrypt at Cost Factor 12**
- `BCryptPasswordEncoder(12)` — ~250ms per hash on modern hardware.
- Tunable: raise the `int` argument (e.g. 14) as hardware improves.

**3. JWT Claims**
- `sub` (subject) = user email (matches `UserDetails.getUsername()`)
- `iat` (issued at)
- `exp` (expiration) = `iat + jwt.expiration-ms`
- Role info is looked up fresh on every request from the DB (not embedded in JWT) so that role revocations take effect immediately without waiting for token expiry.

**4. 401 vs 403 Distinction**
| Scenario | HTTP Status | Handler |
|---|---|---|
| No `Authorization` header at all | **401 Unauthorized** | `JwtAuthenticationEntryPoint` |
| Header present but token is malformed / bad signature / expired | **401 Unauthorized** | `JwtAuthenticationFilter` (short-circuit) |
| Token is valid, but user lacks required role (e.g. ROLE_ADMIN) | **403 Forbidden** | `JwtAccessDeniedHandler` |
| Token is valid, but accessing another user's booking (future modules) | **403 Forbidden** | Throw `ForbiddenException` from service layer |

---

## 📁 Files Created / Modified

### New Files (auth package)

| File | Purpose |
|---|---|
| [dto/RegisterRequest.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/dto/RegisterRequest.java) | Registration DTO with Bean Validation (email format, password strength regex) |
| [dto/LoginRequest.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/dto/LoginRequest.java) | Login DTO (email + password) |
| [dto/AuthResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/dto/AuthResponse.java) | Token response payload (accessToken, expiresIn, user info) |
| [dto/UserProfileResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/dto/UserProfileResponse.java) | `/me` response (no password hash ever returned) |
| [CustomUserDetailsService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/CustomUserDetailsService.java) | Implements `UserDetailsService` — loads user from `UserRepository` by email |
| [JwtTokenProvider.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/JwtTokenProvider.java) | Generates, parses, validates JJWT-signed tokens |
| [JwtAuthenticationFilter.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/JwtAuthenticationFilter.java) | `OncePerRequestFilter` — extracts Bearer token, validates, sets `SecurityContext` |
| [JwtAuthenticationEntryPoint.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/JwtAuthenticationEntryPoint.java) | 401 handler for anonymous requests to protected URLs |
| [JwtAccessDeniedHandler.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/JwtAccessDeniedHandler.java) | 403 handler for authenticated-but-unprivileged requests |
| [SecurityConfig.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/SecurityConfig.java) | Central `SecurityFilterChain`, `AuthenticationProvider`, `PasswordEncoder` beans |
| [AuthService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/AuthService.java) | Business logic: `register()`, `login()`, `getCurrentUserProfile()` |
| [AuthController.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/AuthController.java) | REST endpoints: `POST /register`, `POST /login`, `GET /me` |

### Test Files

| File | Purpose |
|---|---|
| [application.properties](file:///d:/MegaProject/TrainConceirge/src/test/resources/application.properties) | Test config (H2 in-memory, static JWT secret, quiet logs) |
| [AuthIntegrationTest.java](file:///d:/MegaProject/TrainConceirge/src/test/java/com/trainconcierge/auth/AuthIntegrationTest.java) | **16 end-to-end integration tests** (MockMvc) |

### Modified Files

| File | Change |
|---|---|
| [SecurityConfig.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/SecurityConfig.java) → `/api/health` added to permitAll | Public health check now correctly whitelisted |
| [TrainConciergeApplicationTests.java](file:///d:/MegaProject/TrainConceirge/src/test/java/com/trainconcierge/TrainConciergeApplicationTests.java) | Removed inline `@TestPropertySource` — uses shared `application.properties` |
| [GlobalExceptionHandlerTest.java](file:///d:/MegaProject/TrainConceirge/src/test/java/com/trainconcierge/exception/GlobalExceptionHandlerTest.java) | Extended exclude-auto-config list + added filter for `auth.*` package to avoid loading `SecurityConfig` in `@WebMvcTest` slice |

---

## 🔧 Environment Configuration (`.env`)

Copy `.env.example` → `.env` and populate these values:

```bash
# ── Database ──────────────────────────────────────────────────
DB_HOST=localhost
DB_PORT=3306
DB_NAME=train_concierge
DB_USERNAME=root
DB_PASSWORD=your_mysql_password

# ── JPA DDL ────────────────────────────────────────────────────
DDL_AUTO=update

# ── JWT (REQUIRED for Phase 4) ────────────────────────────────
# Generate a secure 256-bit base64 key:
#   Linux/macOS: openssl rand -base64 32
#   Windows:     [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
JWT_SECRET=your_base64_encoded_32_byte_secret_here

# Access token lifetime in milliseconds (default 86400000 = 24 hours)
JWT_EXPIRATION_MS=86400000
```

### Production Recommendations

| Setting | Development | Production |
|---|---|---|
| `JWT_SECRET` length | Any ≥ 256 bits (32 bytes) | At least 512 bits (64 bytes) + rotate quarterly |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | `3600000` (1h) + consider refresh tokens |
| `DDL_AUTO` | `update` | `validate` + Flyway/Liquibase migrations |

---

## 🚀 Running This Phase

### ⚡ Quick Start — Two Commands to Verify & Run

> TL;DR — for the verified working build, execute these in your terminal:

```bash
cd d:\MegaProject\TrainConceirge
mvn clean test        # 27/27 passing
mvn spring-boot:run   # then use Postman per README_PHASE4 instructions
```

Expected output from `mvn clean test`:

```
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

After `mvn spring-boot:run` starts successfully on port **8080**, jump to:
- Step 4 — Health Check below (smoke-test without auth)
- [📮 API Endpoints Reference](#-api-endpoints-reference) for Postman examples

---

### Prerequisites

1. **Java 17+** installed
2. **Maven 3.9+** (or use the included wrapper)
3. **MySQL 8+** running (or skip to H2 tests below)
4. `.env` file configured (see above) — or pass env vars inline

### Step 1: Compile & Verify Build

```bash
cd d:\MegaProject\TrainConceirge
mvn clean compile
```

Expected output: `BUILD SUCCESS` (80 source files compiled).

### Step 2: Run All Tests (27 tests, H2 in-memory)

No MySQL required — tests use auto-configured H2.

```bash
mvn test
```

Expected output:

```
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

#### Breakdown of the 27 tests:

| Test Class | Count | Coverage |
|---|---|---|
| `AuthIntegrationTest` | **16** | Registration success + duplicate + validation + missing body; Login success + wrong password + non-existent + validation; `/me` success + no token + malformed + expired + empty bearer + wrong scheme; public health allowed; case-insensitive email |
| `GlobalExceptionHandlerTest` | **10** | 404/409/400/422/401/403 exception handlers, bean validation, malformed JSON, 500 no-leak, response contract |
| `TrainConciergeApplicationTests` | **1** | Full Spring context loads cleanly |

Run only auth tests for faster iteration:

```bash
mvn test -Dtest=AuthIntegrationTest
```

### Step 3: Start the Application (With MySQL)

```bash
# Ensure MySQL is running and DB is created:
#   CREATE DATABASE train_concierge;

# Option A — use .env values (if using env-file plugin / manual export)
mvn spring-boot:run

# Option B — pass env vars inline (PowerShell):
$env:DB_PASSWORD="yourpass"; $env:JWT_SECRET="dGhpcy1pcy1hLXZlcnktc2VjdXJlLXNlY3JldC1rZXktZm9yLWRldmVsb3BtZW50LW9ubHk="; mvn spring-boot:run
```

The app starts on **http://localhost:8080** by default.

### Step 4: Smoke Test (Health Check — no auth required)

```bash
curl http://localhost:8080/api/health
```

Expected (200 OK):

```json
{
  "success": true,
  "message": "Service is healthy",
  "data": {
    "application": "AI-Powered Train Disruption Concierge",
    "version": "0.0.1-SNAPSHOT",
    "status": "UP",
    "timestamp": "..."
  },
  "timestamp": "..."
}
```

### Step 5: Verify Protected Endpoint Requires Auth

```bash
curl http://localhost:8080/api/auth/me
```

Expected (**401 Unauthorized**) — proves security is active:

```json
{
  "timestamp": "...",
  "status": 401,
  "errorCode": "AUTH_REQUIRED",
  "message": "Authentication is required to access this resource.",
  "path": "/api/auth/me"
}
```

---

### ✅ Phase 4 Running Checklist — Summary

Copy-paste these three commands end-to-end to validate the entire phase:

```bash
cd d:\MegaProject\TrainConceirge
mvn clean test        # 27/27 passing
mvn spring-boot:run   # then use Postman per README_PHASE4 instructions
```

| Command | Expected Result |
|---|---|
| `cd d:\MegaProject\TrainConceirge` | No output — enters project directory |
| `mvn clean test` | `Tests run: 27, Failures: 0, Errors: 0` + `BUILD SUCCESS` |
| `mvn spring-boot:run` | `Started TrainConciergeApplication in X.XXX seconds` on port 8080 |

After the app is running, proceed to **📮 API Endpoints Reference** for Postman request bodies and step-by-step testing.

---

## 📮 API Endpoints Reference

### 1. Register a New User

**`POST /api/auth/register`** — Public (no auth required)

#### Postman Setup:
- Method: **POST**
- URL: `http://localhost:8080/api/auth/register`
- Headers: `Content-Type: application/json`
- Body (raw JSON):

```json
{
  "firstName": "Rahul",
  "lastName": "Sharma",
  "email": "rahul.sharma@example.com",
  "password": "Railway@2026",
  "phoneNumber": "+919876543210"
}
```

#### Password Rules (`RegisterRequest` validator):
- 8–128 characters
- At least 1 digit, 1 lowercase, 1 uppercase, 1 special character (`@#$%^&+=!`)
- No whitespace

#### Success Response (201 Created):

```json
{
  "success": true,
  "message": "User registered successfully.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyYWh1bC5zaGFybWFAZXhhbXBsZS5jb20iLCJpYXQiOjE3Mjc4MjkzNzcsImV4cCI6MTcyNzkxNTc3N30.signed_hash",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "expiresAt": "2026-10-03T05:02:57.414182Z",
    "userId": 1,
    "firstName": "Rahul",
    "lastName": "Sharma",
    "email": "rahul.sharma@example.com",
    "role": "ROLE_USER"
  },
  "timestamp": "2026-10-02T05:02:57.414182Z"
}
```

👉 **Save `data.accessToken`** — you'll need it as the Bearer token for subsequent requests.

#### Duplicate Email Response (409 Conflict):

```json
{
  "timestamp": "...",
  "status": 409,
  "errorCode": "RESOURCE_ALREADY_EXISTS",
  "message": "User already exists with email: 'rahul.sharma@example.com'",
  "path": "/api/auth/register"
}
```

---

### 2. Login (Get Token)

**`POST /api/auth/login`** — Public (no auth required)

#### Postman Setup:
- Method: **POST**
- URL: `http://localhost:8080/api/auth/login`
- Headers: `Content-Type: application/json`
- Body (raw JSON):

```json
{
  "email": "rahul.sharma@example.com",
  "password": "Railway@2026"
}
```

#### Success Response (200 OK):

Same structure as registration — includes a fresh `accessToken`.

#### Invalid Credentials Response (401 Unauthorized):

```json
{
  "timestamp": "...",
  "status": 401,
  "errorCode": "AUTH_TOKEN_INVALID",
  "message": "Invalid email or password.",
  "path": "/api/auth/login"
}
```

---

### 3. Get Current User Profile

**`GET /api/auth/me`** — Protected (requires valid Bearer token)

#### Postman Setup:
- Method: **GET**
- URL: `http://localhost:8080/api/auth/me`
- Headers:
  - `Authorization: Bearer <your_access_token_from_register_or_login>`

#### How to set the token in Postman:
1. Go to the **Authorization** tab
2. Select **Type = Bearer Token**
3. Paste the token into the **Token** field (no `Bearer ` prefix needed — Postman prepends it automatically)

#### Success Response (200 OK):

```json
{
  "success": true,
  "message": "User profile retrieved successfully.",
  "data": {
    "id": 1,
    "firstName": "Rahul",
    "lastName": "Sharma",
    "email": "rahul.sharma@example.com",
    "phoneNumber": "+919876543210",
    "role": "ROLE_USER",
    "enabled": true,
    "preferredNotificationChannel": null,
    "createdAt": "2026-10-02T05:02:57Z",
    "updatedAt": "2026-10-02T05:02:57Z"
  },
  "timestamp": "..."
}
```

⚠️ Notice the password hash is **never** included in responses — enforced by the DTO.

---

## 🧪 Postman Testing Checklist (Manual)

Follow this sequence to manually verify the auth flow:

| Step | Action | Expected Status |
|---|---|---|
| 1 | `GET /api/health` | 200 (public, no auth) |
| 2 | `GET /api/auth/me` without header | 401 AUTH_REQUIRED |
| 3 | `POST /api/auth/register` with valid body | 201 — receive token |
| 4 | `POST /api/auth/register` with same email | 409 RESOURCE_ALREADY_EXISTS |
| 5 | `POST /api/auth/register` with weak password ("abc") | 400 VALIDATION_FAILED |
| 6 | `GET /api/auth/me` with token from step 3 | 200 — see your profile |
| 7 | `POST /api/auth/login` with correct creds | 200 — new token |
| 8 | `POST /api/auth/login` with wrong password | 401 AUTH_TOKEN_INVALID |
| 9 | `GET /api/auth/me` with "Bearer garbage.value.here" | 401 AUTH_TOKEN_INVALID |
| 10 | `GET /api/auth/me` with "Basic <base64>" instead of Bearer | 401 AUTH_REQUIRED |
| 11 | `GET /api/auth/me` with an **expired** token | 401 AUTH_TOKEN_EXPIRED |

### How to Generate an Expired Token for Step 11

Create a small Java snippet (or use the test helper `generateExpiredToken()` from `AuthIntegrationTest`):

```java
SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
String expired = Jwts.builder()
    .subject("rahul.sharma@example.com")
    .issuedAt(new Date(System.currentTimeMillis() - 3600_000))  // issued 1 hour ago
    .expiration(new Date(System.currentTimeMillis() - 10_000))   // expired 10 seconds ago
    .signWith(key)
    .compact();
```

---

## 🔒 Securing Future Modules: "Users can access only their own bookings & journeys"

Phase 4 provides the `AuthService.getCurrentAuthenticatedUser()` helper exactly for this purpose. In Phase 5+, enforce ownership at the **service layer**:

```java
// BookingService.java pattern example:
@Transactional(readOnly = true)
public BookingResponse getBooking(String bookingReference) {
    User currentUser = authService.getCurrentAuthenticatedUser();  // ← from Phase 4

    Booking booking = bookingRepository.findByBookingReference(bookingReference)
            .orElseThrow(() -> new ResourceNotFoundException("Booking", "reference", bookingReference));

    // ── Ownership check ──────────────────────────────────────────────
    if (!booking.getUserId().equals(currentUser.getId())
        && currentUser.getRole() != UserRole.ROLE_ADMIN) {
        throw new ForbiddenException("You do not have permission to view this booking.");
    }

    return toResponse(booking);
}
```

This approach means:
- **Horizontal privilege escalation is impossible** even if an attacker guesses booking references.
- Admins (ROLE_ADMIN) can bypass ownership checks for support scenarios.
- Checks are uniformly applied regardless of which controller/entry-point triggers the action.

---

## 🐛 Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` or `JWT_EXPIRATION_MS` | Env vars not set | Copy `.env.example` to `.env` and populate, or inline with `$env:JWT_SECRET=...` before `mvn spring-boot:run` |
| `JWT signature validation failed` | Token signed with different secret | Ensure the same `JWT_SECRET` is used for both generation and validation environments |
| 403 when trying to access `/api/admin/*` | User has ROLE_USER instead of ROLE_ADMIN | Either grant admin via DB (`UPDATE users SET role = 'ROLE_ADMIN' WHERE id = 1;`) or use a different endpoint |
| Tests fail with "Failed to load ApplicationContext" | `info.app.version` missing in test properties | This is already fixed in `src/test/resources/application.properties` — pull the latest changes |
| `BCryptPasswordEncoder` version mismatch | Not an issue in this phase — BCrypt 12 used | If changing cost factor, ensure old password hashes are migrated |

---

## 📚 Interview Questions (Java Full Stack — Auth Focus)

### Q1. Why `SessionCreationPolicy.STATELESS` in a JWT-based API?
A. Because every request carries its own credentials (the JWT) in the `Authorization` header. Server-side sessions would:
- Break horizontal scaling (sticky sessions not required for load balancers)
- Violate REST "stateless" constraint
- Add unnecessary memory/database overhead for `JSESSIONID`

### Q2. Why validate roles from the DB on every request instead of embedding them in the JWT?
A. Tokens are immutable once signed. If an admin revokes a user's ROLE_ADMIN access, the change would take effect **only after token expiry** (up to 24h) if roles lived inside the JWT. A DB lookup per request ensures immediate consistency for security-critical role changes.

### Q3. Difference between 401 and 403 in Spring Security?
A. **401 Unauthorized** = *I don't know who you are* (missing credentials / bad credentials / expired token). **403 Forbidden** = *I know exactly who you are, but you're not allowed to do this* (valid token, insufficient role, accessing another user's resource).

### Q4. What is a `OncePerRequestFilter`, and why not just use a regular `Filter`?
A. `OncePerRequestFilter` is a Spring guarantee that `doFilterInternal` runs **exactly once per HTTP request**, even if the filter is registered multiple times in the `FilterChain` (e.g., via forward/include dispatcher or nested ERROR dispatch). Critical for JWT auth to avoid double-parsing the token.

### Q5. What attack vectors does BCrypt mitigate? Why not MD5/SHA-256?
A. BCrypt is:
- **Salted automatically** (each hash gets a random 128-bit salt — defeats rainbow tables)
- **Adaptive / slow** (cost factor 12 = ~250ms per hash → brute forcing 1M guesses = 2.5 GPU-days vs microseconds for SHA-256)
- **Memory-hard** properties make GPU/FPGA acceleration less cost-effective
MD5/SHA-256 are cryptographic hashes but fast and unsalted → trivial to crack with precomputed rainbow tables.

---

End of Phase 4. Proceed to Phase 5 (Booking & Journey ownership enforcement + business logic).

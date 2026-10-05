# Phase 7 — Booking & Journey Management

> **Status:** ✅ Complete — **84 tests pass** (10 BookingJourney + 22 Schedule/Seat + 20 Train Mgmt + 27 Auth + 4 Exception + 1 Context).

---

## 📋 What's Implemented in This Phase (13 Requirements + 4 Endpoints)

| # | Requirement (User's 13-point list) | Status |
|---|---|---|
| 1 | Booking request & response DTOs — `BookTrainRequest`, `BookingResponse`, `BookingJourneyResponse`, `PaginatedBookingResponse` | ✅ |
| 2 | Authenticated users book an available train schedule (`POST /api/bookings`) | ✅ |
| 3 | Validate passenger count: `@Min(1) @Max(9)` on DTO + explicit service-layer range check | ✅ |
| 4 | Validate available seats before confirming: `available >= N` service check + atomic `UPDATE ... WHERE available >= N` | ✅ |
| 5 | `@Transactional` service for booking creation + inventory reduction (single unit-of-work; any exception rolls back) | ✅ |
| 6 | Unique booking reference: `TC-YYYYMMDD-XXXXXX` format; `uq_booking_reference` DB constraint + generator retry loop | ✅ |
| 7 | Store booking status (`BookingStatus` enum) + fares (`baseFare`, `totalFare`, `currency`) | ✅ |
| 8 | Create corresponding `Journey` record (1:1 with booking via `journeyService.createJourneyForBooking`) | ✅ |
| 9 | Booking history for authenticated user: `GET /api/bookings/my` with pagination + sorting | ✅ |
| 10 | Booking cancellation with correct seat restoration: `PATCH /api/bookings/{id}/cancel` → atomic `releaseSeatsAtomically` | ✅ |
| 11 | Prevent cross-user view/modify: `booking.getUser().getId().equals(currentUser.getId())` in both read + cancel → 403 | ✅ |
| 12 | Maintain booking history instead of deleting: cancel = `status=CANCELLED` + `cancelledAt` timestamp; row stays in DB | ✅ |
| 13 | Tests: successful booking, insufficient seats, duplicate requests, cancellation, ownership validation | ✅ (10 integration tests) |
| — | Simulated payment/confirmation only — NO real payment or railway ticket issuance | ✅ Explicit non-goal |
| — | Full request/response payloads documented — see Section 5 | ✅ |

⚠️ **Simulated booking confirmation only.** No payment processor, no IRCTC / railway ticketing system, no seat-coach assignment, no QR barcode tickets. Confirmed status is written immediately.

---

## 🏗️ Module Architecture

```
               ┌─────────────────────────────────────────────────────────────┐
               │       Phase 7 — Booking + Journey Transactional Core        │
               └─────────────────────────────────────────────────────────────┘

 Public Controllers (any authenticated USER — JWT Bearer required; no admin split):
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ BookingController  ── /api/bookings                                           │
 │   POST   /                    → createBooking (@Valid BookTrainRequest)       │
 │   GET    /my                  → getMyBookings(pageable + sort)                │
 │   GET    /{id}                → getBookingById (ownership guard → 403)        │
 │   PATCH  /{id}/cancel         → cancelBooking (ownership + status guards)     │
 │   returns ResponseEntity<ApiResponse<BookingResponse | PaginatedBookingResponse>>│
 └──────────────────────────────────────────────────────────────────────────────┘

 Service Layer (@Transactional — business rules + atomic seat ops):
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ BookingService                            │ JourneyService                    │
 │  • createBooking:                         │  • createJourneyForBooking(...)    │
 │    1. resolve currentUser                 │     (1:1 Journey ← Booking link)  │
 │    2. passengerCount 1-9 (BadRequest)     │  • markCancelled(Journey)         │
 │    3. load schedule (JOIN FETCH train)    │  • findByUser(User)               │
 │    4. schedule.cancelled? → 422            │  • toResponse(j) → Journey DTO    │
 │    5. train.active? → 422                  └──────────────────────────────────┘
 │    6. load SeatAvailability (schedule+cls)
 │    7. available >= passengers? → 422 INSUFFICIENT_SEATS
 │    8. bookingRepository.existsActiveDuplicate(user,sched,cls)? → 409 CONFLICT
 │    9. seatAvailabilityService.bookSeats(saId,N) → atomic UPDATE WHERE guard
 │       ↳ false = concurrent race → 422 retry-message
 │   10. calc totalFare = perSeatFare × N (BigDecimal, 2dp)
 │   11. build Booking (CONFIRMED, ref gen, confirmedAt=now) + save
 │   12. journeyService.createJourneyForBooking → persist Journey + set booking.journey
 │   13. log + toResponse
 │  • getMyBookings(page,size,sortBy,sortDir):  resolve sort → Page → map toResponse
 │  • getBookingById(id):  fetch details → owner? owner : 403 ForbiddenException
 │  • cancelBooking(id):
 │       a. fetch + owner-check
 │       b. already CANCELLED/REFUNDED → 422 BOOKING_ALREADY_CANCELLED
 │       c. COMPLETED → 422 BOOKING_NOT_CANCELLABLE
 │       d. releaseSeats(saId, N) → return capped at total, never exceeds
 │       e. booking.status=CANCELLED, cancelledAt=now; save
 │       f. journeyService.markCancelled(booking.journey)
 └──────────────────────────────────────────────────────────────────────────────┘

 Persistence Layer:
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ BookingRepository              │   JourneyRepository & SeatAvailabilityRepo  │
 │  • findByIdWithDetails (4x     │   (reused from Phase 6 — critical:          │
 │    JOIN FETCH user+schedule+   │    • bookSeatsAtomically   — UPDATE + WHERE │
 │    train+LEFT FETCH journey)   │      available >= N → ROW-LOCK, NO OVERSELL │
 │  • findPageByUserWithDetails   │    • releaseSeatsAtomically — LEAST/GREATEST│
 │    (pageable + countQuery)     │      + booked >= N guard (no negative)      │
 │  • findByBookingReference      │    • @Modifying(clearAutomatically=true) on │
 │  • existsByBookingReference    │      all 3 UPDATEs (Phase-6-fix: prevents   │
 │  • existsActiveDuplicate (JPQL │      stale Hibernate L1 cache after UPDATE) │
 │    WHERE status IN CONFIRMED/  │    • @Version optimistic locking            │
 │    PENDING/COMPLETED) → 409    │                                            │
 │  @UniqueConstraint             │                                            │
 │    uq_booking_reference        │                                            │
 └──────────────────────────────────────────────────────────────────────────────┘
 BookingReferenceGenerator: Date(YYYYMMDD) + monotonic counter; up-to-100 loop
   checks existsByBookingReference, falls back to nanoTime-style suffix as guard.
```

### Endpoint Matrix (4 Endpoints — all require JWT Bearer)

| Method | Path | Auth | Role | Purpose |
|---|---|---|---|---|
| **POST** | `/api/bookings` | Bearer | Any authenticated | Create a booking: validate schedule, seats, duplicate-guard; deduct seats atomically; write Booking + Journey; return 201 |
| **GET** | `/api/bookings/my` | Bearer | Any authenticated | Pagination + sortable caller's booking history. Query params: `page`, `size` (cap 100), `sortBy` (status / journeyDate / totalFare / updatedAt / **createdAt** default), `sortDir` (asc/**desc**) |
| **GET** | `/api/bookings/{id}` | Bearer | Any authenticated | Single booking detail. Returns 404 if id unknown; **403 if caller ≠ owner**. |
| **PATCH** | `/api/bookings/{id}/cancel` | Bearer | Any authenticated | Mark booking CANCELLED, restore seats, mark Journey CANCELLED. Re-cancel → 422. Cancel COMPLETED → 422. Cross-user → 403. |

---

## 📁 Files Created / Modified

### New Files

| File | Purpose |
|---|---|
| [BookingJourneyIntegrationTest.java](file:///d:/MegaProject/TrainConceirge/src/test/java/com/trainconcierge/booking/BookingJourneyIntegrationTest.java) | **10 integration tests** covering all ACs: (1) happy-path booking decrements inventory + creates journey, (2) insufficient seats 422, (3) duplicate 409, (4) cancel success (seats restored + history preserved), (5) re-cancel 422 + COMPLETED-not-cancellable 422, (6) cross-user GET & PATCH both 403, (7) /my pagination & sort, (8) 401 unauth + 400 validation (0 passengers / 10 passengers / null scheduleId / no seatClass / null passengers) + 404 schedule, (9) fare math = per-seat × N, unique refs, default currency INR, (10) cancelled schedule 422 / inactive train 422. |

### Pre-existing Files (Verified Working — No Changes Needed)

| File | Role in this phase |
|---|---|
| [Booking.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/Booking.java) | Entity: `bookingReference` (uq), `User ← Booking`, `TrainSchedule ← Booking`, `Journey ← Booking`, `BookingStatus`, `SeatClass`, `numberOfSeats`, `baseFare/totalFare (BigDecimal 10,2)`, `currency(3)`, `seatNumbers`, `confirmedAt/cancelledAt`, `replacementBooking` (nullable for future rebooking) |
| [BookingStatus.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/BookingStatus.java) | Enum: PENDING → CONFIRMED → COMPLETED; or CONFIRMED → CANCELLED / REFUNDED; plus REBOOKED. Cancel gating: CANCELLED/REFUNDED → 422 `BOOKING_ALREADY_CANCELLED`; COMPLETED → 422 `BOOKING_NOT_CANCELLABLE` |
| [BookTrainRequest.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/dto/BookTrainRequest.java) | POST payload: `@NotNull scheduleId`, `@NotNull seatClass`, `@NotNull @Min(1) @Max(9) Integer passengerCount`, `@Size(max=3) currency` default `"INR"` |
| [BookingResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/dto/BookingResponse.java) | Response DTO: id, bookingReference, userId, passengerName, scheduleId, trainId, trainNumber/Name, origin/destination, journeyDate, scheduledDeparture/Arrival, platform, seatClass, numberOfSeats, baseFare/totalFare/currency, status, confirmedAt/cancelledAt, seatNumbers, journey (nested Journey DTO), createdAt/updatedAt |
| [BookingJourneyResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/dto/BookingJourneyResponse.java) | Nested journey summary: id, origin, destination, travelDate, status, totalCost, currency, notes, timestamps |
| [PaginatedBookingResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/dto/PaginatedBookingResponse.java) | content[] + pageNumber, pageSize, totalElements, totalPages, first, last, empty |
| [BookingService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/BookingService.java) | `@Transactional createBooking` (13-step flow above), `@Transactional(readOnly=true) getMyBookings`, `@Transactional(readOnly=true) getBookingById`, `@Transactional cancelBooking`. Uses `AuthService.getCurrentAuthenticatedUser()` for ownership. |
| [BookingController.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/BookingController.java) | 4 endpoints exactly as spec; `@Valid` on POST body; all responses wrapped in `ApiResponse<T>` via `ResponseEntity<ApiResponse<...>>` |
| [BookingRepository.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/BookingRepository.java) | JOIN FETCH queries, paginated findByUser, `existsActiveDuplicate` JPQL status-filter, unique-ref exists check |
| [BookingReferenceGenerator.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/BookingReferenceGenerator.java) | TC- prefix + yyyyMMdd + hex counter; 100× retry vs DB; nano fallback. |
| [Journey.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/journey/Journey.java) + [JourneyRepository.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/journey/JourneyRepository.java) | User FK, origin/destination stations, travelDate, JourneyStatus, totalCost, currency, notes |
| [JourneyService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/journey/JourneyService.java) | `createJourneyForBooking(user, schedule, booking, fare, currency)` → builds Journey, saves, sets `booking.journey`; `markCancelled(journey)`; `toResponse(j)` |
| [JourneyStatus.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/journey/JourneyStatus.java) | PLANNED / IN_PROGRESS / DISRUPTED / COMPLETED / CANCELLED / REBOOKED |
| [SeatAvailabilityService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAvailabilityService.java) | `bookSeats(id,N)` / `releaseSeats(id,N)` boolean → delegates to repo atomics; 0/N guard. Reused from Phase 6. |
| [SeatAvailabilityRepository.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAvailabilityRepository.java) | 3 `@Modifying(clearAutomatically=true)` atomic queries. Reused from Phase 6. |
| [SecurityConfig.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/auth/SecurityConfig.java) | `/api/bookings/**` falls under `.anyRequest().authenticated()` → JWT required; `/api/admin/**` = ADMIN-only separate (booking has no admin endpoints this phase). |
| [GlobalExceptionHandler.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/exception/GlobalExceptionHandler.java) | `BusinessRuleException → 422`, `ForbiddenException → 403`, `DuplicateResourceException → 409`, `ResourceNotFoundException → 404`, `BadRequestException → 400`, `MethodArgumentNotValidException → 400` with `validationErrors{}` map, `AuthenticationException → 401`. |
| [ErrorCode.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/exception/ErrorCode.java) | Booking-specific codes used: `INSUFFICIENT_SEATS`, `PASSENGER_COUNT_INVALID`, `SCHEDULE_CANCELLED`, `INVALID_OPERATION`, `BOOKING_ALREADY_CANCELLED`, `BOOKING_NOT_CANCELLABLE`, `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, `VALIDATION_FAILED`, `RESOURCE_ALREADY_EXISTS`, `AUTH_REQUIRED`. |

---

## 🚀 Running This Phase

### ⚡ Quick Start — Two Commands to Verify

```powershell
cd d:\MegaProject\TrainConceirge

# 84/84 tests pass on H2 in-memory (NO MySQL, no env vars needed):
mvn clean test

# Run with MySQL + auto-seeded trains/schedules/users for Postman/curl play:
$env:JWT_SECRET="phase7-local-dev-secret-key-please-change-min-32"
$env:JWT_EXPIRATION_MS="86400000"
# $env:DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD as needed (see Phase 6 docs)
mvn spring-boot:run
```

Expected from `mvn clean test`:
```
[INFO] Tests run: 84, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Test breakdown:**

| Test Class | Count | What's Covered |
|---|---|---|
| **BookingJourneyIntegrationTest** (NEW, Phase 7) | 10 | (1) create + seats + journey, (2) insufficient seats 422 no side-effect, (3) duplicate 409, (4) cancel success → status CANCELLED + seats restored + still retrievable (history), (5) re-cancel 422 + COMPLETED cannot cancel 422, (6) cross-user GET & PATCH both 403 + /my shows only owner's rows, (7) /my page0 size=2 desc → correct pagination totals + ordering, page1 last-item = first-booked, (8) no-token → 401, 0-pax / 10-pax / null schedule / null class → 400 with specific validationErrors field messages, nonexistent schedule → 404, (9) totalFare = per-seat × N exact, unique TC-* refs, default INR currency, (10) schedule.cancelled=true → 422 SCHEDULE_CANCELLED, train.active=false → 422 INVALID_OPERATION |
| ScheduleSeatAvailabilityIntegrationTest | 22 | Phase 6 (schedule + inventory re-used by booking) |
| TrainManagementIntegrationTest | 20 | Phase 5 (train CRUD) |
| AuthIntegrationTest | 27 | Phase 4 (JWT login used by all endpoints) |
| GlobalExceptionHandlerTest | 4 | Phase 3 |
| TrainConciergeApplicationTests | 1 | Spring context loads |

Run **only the Phase-7 booking tests**:
```powershell
mvn test -Dtest=BookingJourneyIntegrationTest
```

---

### Prerequisites

1. **Java 17+** (`java --version` → openjdk 17 or equivalent)
2. **Maven 3.9+** (`mvn -v`)
3. **MySQL 8+** — only when running the app with `spring-boot:run` (dev profile). **Tests never need MySQL** — they use H2 in-memory (configured automatically via [application-test.properties](file:///d:/MegaProject/TrainConceirge/src/test/resources/application.properties)).
4. **Environment variables** for running the web server locally:

   ```powershell
   # Windows PowerShell (required — see .env.example for MySQL fields too)
   $env:JWT_SECRET="cGhhc2U3LWxvY2FsLWRldi1zZWNyZXQta2V5LXBsZWFzZS1jaGFuZ2UtbWluLTMy"
   $env:JWT_EXPIRATION_MS="86400000"   # 24 hours in ms
   ```

---

### Step-by-Step Running & Validation (PowerShell on Windows)

#### Step 1 — Run the Full Test Suite (84 tests, ~55s)

This runs on H2 in-memory — no MySQL, no external dependencies:

```powershell
cd d:\MegaProject\TrainConceirge
mvn clean test
```

Look for **Tests run: 84, Failures: 0, Errors: 0, Skipped: 0** and `BUILD SUCCESS`.

#### Step 2 — Start App with Auto-Seeded Dev Data

```powershell
$env:JWT_SECRET="cGhhc2U3LWxvY2FsLWRldi1zZWNyZXQta2V5LXBsZWFzZS1jaGFuZ2UtbWluLTMy"
$env:JWT_EXPIRATION_MS="86400000"
# If MySQL isn't configured, tests still pass; app will fail datasource init.
# Set your MySQL vars per Phase 6 README if needed.
mvn spring-boot:run
```

On first boot you should see:
```
═══════════════════════════════════════════════════════════
Phase 5: Seeding Train Management sample data...
  ▸ 15 trains, 150 schedules, 600 seat rows
  ▸ admin@trainconcierge.dev / Admin@123
  ▸ user@trainconcierge.dev / User@123
═══════════════════════════════════════════════════════════
```

**Pre-seeded credentials for testing:**

| Email | Password | Role |
|---|---|---|
| `admin@trainconcierge.dev` | `Admin@123` | ROLE_ADMIN — train/schedule write + read all |
| `user@trainconcierge.dev` | `User@123` | ROLE_USER — book, cancel, view own bookings |

#### Step 3 — Health Check

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/health -Method Get
# -> { status: "UP", ... }
```

#### Step 4 — Obtain a JWT via PowerShell

```powershell
$loginBody = @{
    email    = "user@trainconcierge.dev"
    password = "User@123"
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
    -Method Post -Body $loginBody -ContentType "application/json"

$USER_TOKEN = $loginResp.data.accessToken
# $USER_TOKEN will be a long eyJ... string; keep it for the rest of the session
```

Admin (if needed — booking endpoints are USER endpoints, admin can still book):
```powershell
$adminLogin = @{ email="admin@trainconcierge.dev"; password="Admin@123" } | ConvertTo-Json
$ADMIN_TOKEN = (Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
    -Method Post -Body $adminLogin -ContentType "application/json").data.accessToken
```

#### Step 5 — Find an Available Schedule to Book

You need a valid `scheduleId` + know a seat class with availability. First list schedules:
```powershell
$schedulesResp = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/schedules?page=0&size=3" `
    -Method Get -Headers @{ Authorization = "Bearer $USER_TOKEN" }

$schedulesResp.data.content | Format-List id, trainNumber, trainName, scheduledDate, cancelled
```

Then check availability for one schedule (e.g., id = 1):
```powershell
$avail = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/schedules/1/availability" `
    -Method Get -Headers @{ Authorization = "Bearer $USER_TOKEN" }
$avail.data | Format-Table id, scheduleId, seatClass, totalSeats, availableSeats, bookedSeats, fare
```

Pick any class where `availableSeats >= your passenger count (1-9)`.

---

## 📮 Full API Reference — Request/Response Payloads + PowerShell Examples

---

### 1. `POST /api/bookings` — Create Booking

- **URL**: `http://localhost:8080/api/bookings`
- **Method**: `POST`
- **Auth**: Bearer (any authenticated user)
- **Content-Type**: `application/json`
- **Request Payload** (`BookTrainRequest`):

```json
{
  "scheduleId": 1,
  "seatClass": "SECOND",
  "passengerCount": 2,
  "currency": "INR"
}
```

| Field | Type | Required? | Constraints |
|---|---|---|---|
| `scheduleId` | `Long` | ✅ Yes | must match an existing train schedule |
| `seatClass` | `Enum` (`FIRST` / `BUSINESS` / `SECOND` / `SLEEPER`) | ✅ Yes | must be configured for that schedule |
| `passengerCount` | `Integer` | ✅ Yes | `@Min(1) @Max(9)` — 1-9 passengers per booking |
| `currency` | `String(3)` | No | ISO 4217; default `"INR"` |

- **Success Response — HTTP 201 Created** wrapped in `ApiResponse<BookingResponse>`:

```json
{
  "success": true,
  "message": "Booking created successfully.",
  "data": {
    "id": 42,
    "bookingReference": "TC-20261002-0000A1",
    "userId": 2,
    "passengerName": "Demo Passenger",
    "scheduleId": 1,
    "trainId": 1,
    "trainNumber": "12001",
    "trainName": "Shatabdi Express",
    "originStation": "New Delhi",
    "destinationStation": "Kanpur",
    "journeyDate": "2026-10-02",
    "scheduledDeparture": "05:30:00",
    "scheduledArrival": "11:30:00",
    "platform": "1",
    "seatClass": "SECOND",
    "numberOfSeats": 2,
    "baseFare": 500.0,
    "totalFare": 900.0,
    "currency": "INR",
    "status": "CONFIRMED",
    "confirmedAt": "2026-10-02T07:23:11.123456Z",
    "cancelledAt": null,
    "seatNumbers": null,
    "journey": {
      "id": 42,
      "originStation": "New Delhi",
      "destinationStation": "Kanpur",
      "travelDate": "2026-10-02",
      "status": "PLANNED",
      "totalCost": 900.0,
      "currency": "INR",
      "notes": null,
      "createdAt": "2026-10-02T07:23:11.123456Z",
      "updatedAt": "2026-10-02T07:23:11.123456Z"
    },
    "createdAt": "2026-10-02T07:23:11.123456Z",
    "updatedAt": "2026-10-02T07:23:11.123456Z"
  },
  "timestamp": "2026-10-02T07:23:11Z"
}
```

- **Error Responses**:

| Scenario | Status | `errorCode` | Notes |
|---|---|---|---|
| Missing Bearer token | 401 | `AUTH_REQUIRED` | JwtAuthenticationEntryPoint |
| `scheduleId` null / missing | 400 | `VALIDATION_FAILED` | `validationErrors.scheduleId` |
| `passengerCount` 0 or 10 or null | 400 | `VALIDATION_FAILED` | `validationErrors.passengerCount` — message per DTO |
| `seatClass` null | 400 | `VALIDATION_FAILED` | `validationErrors.seatClass` |
| Schedule id not found | 404 | `RESOURCE_NOT_FOUND` | |
| Seat class not found for schedule | 404 | `RESOURCE_NOT_FOUND` | |
| `schedule.cancelled == true` | 422 | `SCHEDULE_CANCELLED` | BusinessRuleException |
| Train master record inactive | 422 | `INVALID_OPERATION` | |
| `available < passengerCount` | 422 | `INSUFFICIENT_SEATS` | |
| Concurrent booking race loses atomic WHERE | 422 | `INSUFFICIENT_SEATS` | retry-message logged |
| Same user already has CONFIRMED/PENDING/COMPLETED booking for (schedule + seatClass) | 409 | `RESOURCE_ALREADY_EXISTS` | DuplicateResourceException |
| Any uncaught exception during transaction | — | — | Entire transaction rolls back (no partial bookings/inventory) |

**PowerShell example:**
```powershell
$body = @{ scheduleId = 1; seatClass = "SECOND"; passengerCount = 2; currency = "INR" } | ConvertTo-Json
$booking = Invoke-RestMethod -Uri "http://localhost:8080/api/bookings" `
    -Method Post -Headers @{ Authorization = "Bearer $USER_TOKEN" } `
    -Body $body -ContentType "application/json" -StatusCodeVariable status
Write-Host "Status: $status | Ref: $($booking.data.bookingReference) | Fare: $($booking.data.totalFare)"
$BOOKING_ID = $booking.data.id
```

---

### 2. `GET /api/bookings/my` — My Booking History (Paginated)

- **URL**: `http://localhost:8080/api/bookings/my`
- **Method**: `GET`
- **Auth**: Bearer (returns ONLY this user's own bookings; never cross-user rows)
- **Query Params** (all optional):

| Param | Default | Allowed |
|---|---|---|
| `page` | `0` | `>= 0` (clamped) |
| `size` | `10` | `1-100` (clamped to min 1, max 100) |
| `sortBy` | `createdAt` | `status` / `journeyDate` (= schedule.scheduledDate) / `totalFare` / `updatedAt` / `createdAt` |
| `sortDir` | `desc` | `asc` or `desc` |

- **Success Response — HTTP 200**:

```json
{
  "success": true,
  "message": "Booking history retrieved successfully.",
  "data": {
    "content": [
      {
        "id": 42,
        "bookingReference": "TC-20261002-0000A1",
        "userId": 2,
        "passengerName": "Demo Passenger",
        "scheduleId": 1,
        "trainId": 1,
        "trainNumber": "12001",
        "trainName": "Shatabdi Express",
        "originStation": "New Delhi",
        "destinationStation": "Kanpur",
        "journeyDate": "2026-10-02",
        "scheduledDeparture": "05:30:00",
        "scheduledArrival": "11:30:00",
        "platform": "1",
        "seatClass": "SECOND",
        "numberOfSeats": 2,
        "baseFare": 500.0,
        "totalFare": 900.0,
        "currency": "INR",
        "status": "CONFIRMED",
        "confirmedAt": "2026-10-02T07:23:11Z",
        "cancelledAt": null,
        "seatNumbers": null,
        "journey": { /* BookingJourneyResponse */ },
        "createdAt": "...",
        "updatedAt": "..."
      }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 7,
    "totalPages": 1,
    "first": true,
    "last": true,
    "empty": false
  },
  "timestamp": "..."
}
```

- **Errors**: 401 without token.

**PowerShell example:**
```powershell
$history = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/bookings/my?page=0&size=5&sortBy=totalFare&sortDir=desc" `
    -Method Get -Headers @{ Authorization = "Bearer $USER_TOKEN" }
Write-Host "Bookings: $($history.data.totalElements) total, page $($history.data.pageNumber)/$($history.data.totalPages)"
$history.data.content | Format-Table bookingReference, trainNumber, seatClass, numberOfSeats, totalFare, status, journeyDate
```

---

### 3. `GET /api/bookings/{id}` — Single Booking Detail

- **URL**: `http://localhost:8080/api/bookings/{id}`  (replace `{id}` with numeric id, e.g. `42`)
- **Method**: `GET`
- **Auth**: Bearer
- **Rules**:
  - 404 if id unknown.
  - 403 if caller ≠ booking owner.

- **Success Response (HTTP 200)**: identical to a single `content` entry from /my, wrapped in `ApiResponse<BookingResponse>`.
- **Error Response (HTTP 403 — cross-user)**:
```json
{
  "status": 403,
  "errorCode": "ACCESS_DENIED",
  "message": "You are not permitted to access this booking.",
  "path": "/api/bookings/42"
}
```

**PowerShell example:**
```powershell
$detail = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/bookings/$BOOKING_ID" `
    -Method Get -Headers @{ Authorization = "Bearer $USER_TOKEN" }
$detail.data | Format-List id, bookingReference, status, seatClass, numberOfSeats, totalFare,
    originStation, destinationStation, journeyDate,
    @{ n = "journey"; e = { "$($_.journey.status) / $($_.journey.totalCost)" } }
```

---

### 4. `PATCH /api/bookings/{id}/cancel` — Cancel Booking

- **URL**: `http://localhost:8080/api/bookings/{id}/cancel`
- **Method**: `PATCH`  (no body required)
- **Auth**: Bearer
- **Rules**:
  - Owner only → 403 otherwise.
  - Booking already `CANCELLED` or `REFUNDED` → HTTP **422** `BOOKING_ALREADY_CANCELLED`.
  - Booking `COMPLETED` → HTTP **422** `BOOKING_NOT_CANCELLABLE` (journey already happened).
  - `REBOOKED` → currently cancellable (treat as soft-cancel before rebooking finalises).

- **Success Response — HTTP 200**:
```json
{
  "success": true,
  "message": "Booking cancelled successfully. Seats have been released.",
  "data": {
    "id": 42,
    "bookingReference": "TC-20261002-0000A1",
    "status": "CANCELLED",
    "cancelledAt": "2026-10-02T08:10:15.999Z",
    "journey": {
      "id": 42,
      "status": "CANCELLED"
      // ... rest of BookingJourneyResponse
    }
    // ... rest of BookingResponse fields (unchanged except status/cancelledAt/journey.status)
  },
  "timestamp": "2026-10-02T08:10:15Z"
}
```

**Key invariants after cancellation**:
- `SeatAvailability.availableSeats` for the class **increases by** `booking.numberOfSeats` (capped at `totalSeats` via `LEAST(total, available + N)`).
- `SeatAvailability.bookedSeats` **decreases by** N (`GREATEST(0, booked - N)` + `booked >= N` guard → no negatives).
- Booking row still exists; GET by id still works; /my history still contains it. **Soft status change only — no DELETE.**

- **Error Responses**:

| Scenario | Status | errorCode |
|---|---|---|
| No Bearer token | 401 | AUTH_REQUIRED |
| Booking id unknown | 404 | RESOURCE_NOT_FOUND |
| Caller ≠ owner | 403 | ACCESS_DENIED |
| status is CANCELLED or REFUNDED | 422 | BOOKING_ALREADY_CANCELLED |
| status is COMPLETED | 422 | BOOKING_NOT_CANCELLABLE |

**PowerShell example:**
```powershell
$cancel = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/bookings/$BOOKING_ID/cancel" `
    -Method Patch -Headers @{ Authorization = "Bearer $USER_TOKEN" }
Write-Host "Status: $($cancel.data.status) | Cancelled at: $($cancel.data.cancelledAt)"
Write-Host "Journey status: $($cancel.data.journey.status)"
# Re-cancel attempt should fail:
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/bookings/$BOOKING_ID/cancel" `
        -Method Patch -Headers @{ Authorization = "Bearer $USER_TOKEN" }
} catch {
    Write-Host "Re-cancel failed as expected: HTTP $($_.Exception.Response.StatusCode.value__)"
}
```

---

## 🧾 Booking & Journey Entity Quick Reference

### BookingStatus Lifecycle

```
PENDING ──(payment ok, simulated)──▶ CONFIRMED ──(journey completes)──▶ COMPLETED
                                       │
                                       ├─(user cancels)──▶ CANCELLED
                                       ├─(refund issued)──▶ REFUNDED
                                       └─(rebooked after disruption)──▶ REBOOKED
```

Cancel gates prevent leaving `CANCELLED`/`REFUNDED`/`COMPLETED`.

### JourneyStatus Lifecycle

```
PLANNED ──(boarding)──▶ IN_PROGRESS ──(arrived)──▶ COMPLETED
   │                       │
   │                       └─(active disruption)──▶ DISRUPTED ──(alternative)──▶ REBOOKED
   │
   └─(cancel)──▶ CANCELLED
```

### BookingResponse vs BookTrainRequest — Field Cross-Reference

| **BookTrainRequest** | → | **BookingResponse** (derived) |
|---|---|---|
| `scheduleId` | → | scheduleId, trainId, trainNumber, trainName, originStation, destinationStation, journeyDate (= schedule.scheduledDate), scheduledDeparture/Arrival, platform |
| `seatClass` | → | seatClass (plus fare math uses SeatAvailability.fare) |
| `passengerCount` | → | numberOfSeats (used as N in inventory + fare math) |
| `currency` (default INR) | → | currency + used on Journey.currency too |
| (auth context) | → | userId, passengerName (= first + last of authenticated user), owner-guard, journey.user reference |
| (generator) | → | bookingReference = TC-yyyyMMdd-hex; unique uq_booking_reference |
| (now) | → | confirmedAt = Instant.now(); status=CONFIRMED |
| (linked JourneyService) | → | journey sub-object (origin/dest/travelDate from schedule+train, PLANNED status, totalCost = totalFare) |

---

## 🚨 Booking-Specific Error Codes Cheat Sheet

| Code | When | HTTP |
|---|---|---|
| `AUTH_REQUIRED` | Missing/invalid JWT on any of the 4 endpoints | 401 |
| `VALIDATION_FAILED` | Jakarta `@Valid` DTO violations (null scheduleId / passengerCount out of 1-9 / null seatClass). Check `validationErrors.{field}`. | 400 |
| `RESOURCE_NOT_FOUND` | scheduleId doesn't exist / seat-class not configured for schedule / booking id unknown. | 404 |
| `RESOURCE_ALREADY_EXISTS` | Active duplicate (user + schedule + seatClass) — `existsActiveDuplicate` pre-guard. | 409 |
| `ACCESS_DENIED` | Cross-user GET or PATCH cancel. | 403 |
| `INSUFFICIENT_SEATS` | available < passengerCount OR concurrent race loses atomic UPDATE. | 422 |
| `PASSENGER_COUNT_INVALID` | Explicit 1-9 service guard (used if DTO bypassed via direct service call). | 400 |
| `SCHEDULE_CANCELLED` | Booking requested on schedule where `cancelled=true`. | 422 |
| `INVALID_OPERATION` | Booking on inactive train (plus other generic rule violations). | 422 |
| `BOOKING_ALREADY_CANCELLED` | Cancel PATCH for CANCELLED or REFUNDED status. | 422 |
| `BOOKING_NOT_CANCELLABLE` | Cancel PATCH for COMPLETED status. | 422 |

---

## 💼 Interview Prep (Java Full-Stack + Backend)

Focus on the narrative **"Booking engine with transactional + concurrency-safety + ownership"**.

### Q1: How do you prevent seat overselling in a concurrent booking system?

**Answer sketch**:
- **Read-then-write is wrong**:
  ```
  // ❌ Naive:
  SeatAvailability sa = repo.findById(id);
  if (sa.getAvailableSeats() < N) throw ...;
  sa.setAvailableSeats(sa.getAvailableSeats() - N);
  repo.save(sa);  // two concurrent threads both pass the if-check → OVERSELL
  ```
- **✅ Use a single atomic SQL UPDATE + WHERE guard**:
  ```sql
  UPDATE seat_availability
     SET availableSeats = availableSeats - :N,
         bookedSeats    = bookedSeats    + :N
   WHERE id = :id
     AND availableSeats >= :N     -- row-level lock + condition combined
     AND totalSeats     >= :N;
  ```
  The database takes an exclusive row lock during UPDATE; if rows-affected = 1 → success, 0 → insufficient. No app-level race possible.
- **Spring integration**: `@Modifying(clearAutomatically = true)` on the repository `@Query` so the Hibernate Persistence Context (L1 cache) doesn't return stale values.
- **`@Transactional`** on the service method (`createBooking`) ensures the booking row + inventory update either BOTH commit or BOTH roll back.
- **Defence-in-depth** (our code does all four):
  1. Pre-flight service check (`sa.getAvailableSeats() < N` → fast fail, avoids DB round trip for obvious cases).
  2. Atomic SQL UPDATE with WHERE guard → actual concurrency correctness.
  3. SeatAvailability entity `@Version` for optimistic locking fallback.
  4. DB `CHECK(available >= 0)` / `CHECK(booked >= 0)` constraints if your schema defines them — last line.

### Q2: How do you make sure a user can't view or modify another user's booking?

**Answer sketch**:
- Put the **ownership check in the service layer** (not controller, not repo only) because business rules live in services.
- Use the **security context** to resolve the current user (don't trust `userId` in request body — it's never in the booking request DTO). In our case `AuthService.getCurrentAuthenticatedUser()` via JWT filter + `SecurityContextHolder`.
- After fetching the booking by id, compare **by FK**: `booking.getUser().getId().equals(currentUser.getId())`. Fail with `ForbiddenException` (→ HTTP 403, `ACCESS_DENIED`) if false.
- Do this **for both reads and writes** (get-by-id and cancel).
- For the `/my` list endpoint, the WHERE-clause is implicit: `bookingRepository.findPageByUserWithDetails(currentUser, pageable)` — repo queries by `user = :user` FK; no possibility of cross-user rows.
- **Don't do**: `bookingRepository.findById(id)` in controller, compare controller-side, leak existence to attackers by returning 404 vs 403 (we intentionally return 403 so even the existence of id=42 doesn't leak to non-owners — better: return 404 *and* a 403 message? Pick a consistent policy; ours: 404 for unknown, 403 for known-but-not-yours — slightly leaks existence, but simpler; in a hardened version return 404 in both cases).

### Q3: Why use soft-delete (status = CANCELLED) instead of `DELETE FROM bookings`?

**Answer sketch**:
1. **Audit & history** — customers + support expect to see cancelled bookings in "My Trips". Financial/accounting reports need cancelled rows.
2. **Referential integrity** — `journey`, `rebooking_history`, `notification` tables often have FKs into `bookings.id`; DELETE forces complex ON DELETE cascade or orphan rows.
3. **Future rebooking flow** — when a disruption happens (Phase 8+) we set `booking.replacementBooking` pointer + `status=REBOOKED`; the original must stay around.
4. **Data recovery** — if cancel is mistaken (user clicks by accident / bug), undeleting is trivial (`status=CONFIRMED`) vs recovering a deleted row (backups, audit tables, etc.).
5. **Analytics** — "What's our cancellation rate per route? per class? per day?" impossible if rows are deleted.

### Q4: How do you generate unique booking references?

**Answer sketch**:
- **Format**: `TC-YYYYMMDD-XXXXXX` (prefix, date part, monotonic/random hex suffix). Benefits: human-readable, sortable-by-date, customer support can read it over the phone.
- **Implementation layers**:
  1. In-memory `AtomicLong` counter (seeded with `(System.currentTimeMillis() & mask)` to avoid collisions across JVM restarts within the same day).
  2. Format with `String.format("%s-%s-%06X", ...)` and suffix-extend to 8 hex on the rare retry-fork.
  3. **In-app uniqueness check** (up to 100 iterations): `if (!bookingRepository.existsByBookingReference(ref)) return ref;` else increment counter and retry.
  4. **Database unique constraint** `uq_booking_reference(column=bookingReference)` as the hard guard — if two app instances (scaled horizontally) somehow produce the same ref, the DB enforces uniqueness; app returns 500 / retries instead of silently corrupting data. (For production: add a DB sequence per-day or UUIDv7.)

### Q5: Explain BookingStatus states + why cancelling COMPLETED is forbidden.

**Answer sketch**:
- States & meaning:
  - `PENDING` — created, waiting for a payment step (skipped in this phase because payment is simulated; our create writes CONFIRMED directly).
  - `CONFIRMED` — seats held, fare recorded, passenger expects to travel.
  - `CANCELLED` — user/system cancelled; seats released (this is **not** a hard delete).
  - `REFUNDED` — CANCELLED + money returned (extra bookkeeping state; our system simulates, but status exists for Phase 8 payment integration).
  - `COMPLETED` — passenger has travelled; schedule date passed or admin-marked.
  - `REBOOKED` — disruption module replaced booking with a new one; `replacementBooking` FK points at the substitute.
- **Why can't a COMPLETED booking be cancelled?** Business invariant: service has already been rendered (passenger travelled). Cancelling it would incorrectly release seats back to inventory (train already departed, seats are meaningless) and mess up revenue reporting. Gate it with a simple status check → `422 BOOKING_NOT_CANCELLABLE`. For refunds post-travel use a separate **dispute/chargeback workflow**, not the cancel endpoint.

### Q6: Walk through createBooking transactionally — what can go wrong at each step?

(If asked in a live interview, trace the 13-step flow from the Architecture diagram above. Pay attention to when seat deduction happens relative to `save(booking)` — we **deduct first, then write the booking row**, because a failed atomic UPDATE is fast and rollback-free; writing the booking first then deducting risks the JPA flush writing a CONFIRMED row then a later concurrency failure leaving the booking orphan. Our code does: seats-decrement → save Booking → save Journey. All in one `@Transactional`; any RuntimeException triggers rollback.)

---

## Summary of Files Changed / Added This Phase

| Type | Count | Details |
|---|---|---|
| New test | 1 | `BookingJourneyIntegrationTest.java` — 10 scenarios |
| New doc | 1 | `README_PHASE7.md` (this file) |
| Verified reused | ~30 | booking + journey packages, SeatAvailability atomics, Security, GlobalExceptionHandler, ErrorCode |
| Total full-suite tests | 84 | 10 new + 74 prior-phase |

> Next: Phase 8 — Rebooking & Disruption management (reacts to cancelled/disrupted schedules, automatically proposes alternatives, notifies passengers). Coming next!

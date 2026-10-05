# TrainConcierge Phase 8 — Hotel Booking Simulation (Spec Mode)

## 1. Problem & Users

**Problem**: The TrainConcierge system already has a skeleton `HotelBooking` entity + repository but no REST API, no simulation adapter, and no way for end-users to attach hotel stays to a journey. During disruption recovery (Phase ~10), the system will need to re-book a user's hotel automatically; therefore this module must be self-contained with a swappable hotel adapter interface so a real provider can be dropped in later without touching the service orchestration layer.

**Users** (of this module):
- Authenticated passenger (`ROLE_USER`) → creates a hotel reservation against their own journey, reschedules (new check-in / check-out dates).
- Future disruption workflow → triggers hotel reschedule via the adapter interface (same service).

## 2. Goals & Non-Goals

### Goals
1. Produce 3 public REST endpoints (`POST /api/hotels`, `GET /api/hotels/journey/{journeyId}`, `PATCH /api/hotels/{id}/reschedule`) with JWT auth + horizontal ownership check (403 on cross-user).
2. Use the pre-existing `hotel_bookings` `HotelBooking` entity and add any missing fields (status enum, modification audit, hotel reference generator, etc.).
3. Record modification audit entries (oldCheckIn / newCheckIn / oldCheckOut / newCheckOut / rescheduledAt / simulatorUsed) for every `/reschedule` call.
4. Add a `MockHotelService` adapter under a dedicated interface contract, with clearly-worded simulation messages ("SIMULATED", no claim of real hotel API call).
5. Use honest status enum values: `CONFIRMED_SIMULATED`, `RESCHEDULED_SIMULATED` (plus `CANCELLED_SIMULATED` placeholder if needed later).
6. Validate: `checkOutDate.isAfter(checkInDate)`, numberOfNights correctly computed, journey belongs to caller.

### Non-Goals
- ❌ No real hotel payment / reservation API call.
- ❌ No actual room inventory, no rate lookup, no cancellation API (cancel endpoint omitted by requirement).
- ❌ No admin CRUD (public `/api/admin/hotels` endpoints are not in scope).
- ❌ No DB migration; use `ddl-auto=update` (existing project convention).

## 3. Requirements

### Functional Requirements (FRs)
**FR-1 — DTOs**: Create request DTOs (`CreateHotelBookingRequest`, `RescheduleHotelRequest`) and response DTOs (`HotelBookingResponse` with nested modification history list; list wrapper for journey endpoint).

**FR-2 — Create reservation (POST /api/hotels)**:
- Accepts `journeyId`, hotel name/address/city, check-in, check-out (optional: totalCost, currency, numberOfNights).
- `journeyId` must refer to an existing Journey owned by the caller → 404 if journey absent; 403 if journey.user != caller.
- Validates check-out strictly after check-in → 400 with VALIDATION_FAILED or 422 HOTEL_INVALID_DATES.
- Automatically computes `numberOfNights = DAYS.between(checkIn, checkOut)` when not provided, otherwise sanity-checks.
- Auto-generates a hotel booking reference with pattern `HOTEL-YYMMDD-XXXX`.
- Calls `HotelProviderAdapter.confirmReservation(...)` → default impl is `MockHotelService` which returns a simulated `externalBookingRef` = `SIM-HOTEL-<rand>` and confirmation flag true. Never writes strings that suggest a real hotel was called (e.g. use "Reservation simulated locally" not "Hilton confirmed").
- Stores entity with status `CONFIRMED_SIMULATED` and initial `status=CONFIRMED_SIMULATED` confirmedAt=now.
- Returns 201 CREATED with ApiResponse wrapper + full response DTO.

**FR-3 — Get hotel bookings for a journey (GET /api/hotels/journey/{journeyId})**:
- Looks up journey by id → 404 if absent → 403 if journey.user != caller.
- Returns list of `HotelBookingResponse` ordered by check-in asc (or created desc, deterministic).

**FR-4 — Reschedule (PATCH /api/hotels/{id}/reschedule)**:
- Requires new check-in and/or new check-out dates in payload (at least one; both if only one given → validate pair).
- Re-validates: new check-out after new check-in.
- Ownership: 404 if not found; 403 if user.id != booking.user.id.
- Captures previous check-in / check-out before mutation.
- Calls `HotelProviderAdapter.reschedule(externalBookingRef, oldCheckIn, oldCheckOut, newCheckIn, newCheckOut)` → MockHotelService returns simulated flag + simulated providerRef.
- Persists the update to HotelBooking (new dates, new numberOfNights, status = `RESCHEDULED_SIMULATED`).
- Appends a modification audit row: `oldCheckInDate`, `newCheckInDate`, `oldCheckOutDate`, `newCheckOutDate`, `rescheduledAt`, `simulationProvider = "MOCK"`.
- Returns 200 OK with updated response containing the modification history.

**FR-5 — Ownership**: Horizontal access check on every endpoint. No endpoint exposes another user's hotel data. For `/journey/{journeyId}` the ownership is checked at the *journey* FK (because the user could query other-user's journey-id → block before listing hotels). For `/hotels/{id}*` ownership is checked at the HotelBooking user FK.

**FR-6 — Status values**: Status strings MUST use explicit `_SIMULATED` suffix (CONFIRMED_SIMULATED, RESCHEDULED_SIMULATED, optionally CANCELLED_SIMULATED stored as enum or string — enum preferred because DTO type-safe). Do not store `CONFIRMED` (which is used by train booking).

**FR-7 — Modification history**:
- Requires a new `HotelModificationAudit` entity or embed history inside a `@Lob`/JSON column (preferred approach: proper entity for queryability in disruption module).
- `toResponse` always includes the ordered list of modifications; for a freshly-created record the list is empty.

**FR-8 — Independence**:
- `HotelProviderAdapter` is a Java interface with at least: `confirmReservation(HotelBooking booking)` and `reschedule(booking, oldCheckIn, oldCheckOut, newCheckIn, newCheckOut)`.
- Exactly one `@Primary @ConditionalOnMissingBean` bean = `MockHotelService` (no Spring profile required; production override uses bean name without touching service code).
- HotelService never references disruption classes, so it can be invoked later without changes.

### Non-Functional Requirements
- **NFR-1 (Security)**: Endpoints inherit authenticated filter; no `permitAll()` on these URLs. No IDOR (403 cross-user).
- **NFR-2 (Hibernate)**: Always JOIN FETCH user and journey in repository queries. No LAZY proxy traversal outside session.
- **NFR-3 (DTO segregation)**: Never return HotelBooking entity directly. Only `HotelBookingResponse` DTO.
- **NFR-4 (Test isolation)**: All new test class uses `@BeforeEach` full delete in correct FK order, deterministic test users/tokens.
- **NFR-5 (Honesty)**: No message text claims a real hotel was contacted. Every mock adapter log/message/response suffix contains "Simulated" or "SIM-".

## 4. Constraints, Dependencies, Assumptions

**Constraints**:
- Tech stack: same as Phase 1-7 — Spring Boot 3.2.4, JPA/Hibernate, JJWT 0.12.5, JUnit 5, MockMvc, H2 for tests, MySQL for runtime.
- No changes to existing train/booking endpoints.
- `HotelBooking` entity must be extended without breaking `BaseEntity` semantics.

**Dependencies**:
- Reuses existing `AuthService.getCurrentAuthenticatedUser()`, `JourneyRepository`, ErrorCode + ForbiddenException/BusinessRuleException/ResourceNotFoundException/BadRequestException, `ApiResponse` wrapper, and GlobalExceptionHandler.

**Assumptions**:
- Total cost and currency are caller-provided (no rate lookup); if omitted default to `0.00 GBP`.
- Number of nights for POST is computed when not provided; otherwise validated.
- "Manual modification" in requirement 6 = reschedule PATCH endpoint (that is the only manual mutation operation implemented).
- No cancellation endpoint per requirement (only 3 APIs listed).

## 5. Acceptance Criteria

All ACs below are independently verifiable during Review.

- **AC-1 (rule)**: POST /api/hotels with valid payload → 201, status = CONFIRMED_SIMULATED, reference starts with `HOTEL-`, `externalBookingRef` starts with `SIM-HOTEL-`, `numberOfNights` = DAYS.between(checkIn, checkOut). Row stored.
- **AC-2 (rule)**: POST /api/hotels with checkOut <= checkIn → 400 or 422, errorCode = HOTEL_INVALID_DATES (or VALIDATION_FAILED with clear field error). No row stored.
- **AC-3 (rule)**: POST /api/hotels with journeyId of another user's journey → 403 ACCESS_DENIED.
- **AC-4 (rule)**: GET /api/hotels/journey/{journeyId} returns list with only that journey's hotels; 403 if not the journey owner; 404 if journey absent.
- **AC-5 (rule)**: PATCH /api/hotels/{id}/reschedule → status changes to RESCHEDULED_SIMULATED, new dates and numberOfNights are correct, an audit entry (old/new check-in/out + rescheduledAt) is persisted.
- **AC-6 (rule)**: PATCH /api/hotels/{id}/reschedule with invalid date pair (new check-out <= new check-in) → 400/422 error. Dates and status UNCHANGED in DB.
- **AC-7 (rule)**: PATCH /api/hotels/{id}/reschedule on another user's hotel → 403.
- **AC-8 (rule)**: HotelProviderAdapter interface exists, MockHotelService is its concrete bean, service calls only adapter (point-to-point verification in source).
- **AC-9 (rule)**: All statuses are either `CONFIRMED_SIMULATED` or `RESCHEDULED_SIMULATED` (in DTO and in persisted data) — no plain `CONFIRMED` status stored on hotel_bookings.
- **AC-10 (rubric)**: README completeness (payload docs + commands + sample run). Scale 1-5. Anchors: 1=absent/wrong, 3=payloads partial, 5=full request+response for 3 endpoints + PowerShell step-by-step commands + interview Q&A ≥ 3 questions. Threshold ≥4.

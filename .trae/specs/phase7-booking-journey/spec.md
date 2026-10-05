# TrainConcierge Phase 7 - Booking & Journey Management - Product Requirements Document

## Overview
- **Summary**: Implement a secure, transactional booking and journey management subsystem that lets authenticated passengers reserve seats on available train schedules, cancel their bookings, and view their booking history — with full ownership isolation, seat inventory integrity, and journey record creation.
- **Purpose**: Enable the core user value proposition of TrainConcierge: purchasing train tickets online. This phase turns the schedule/availability catalogue (Phase 6) into a usable booking engine.
- **Target Users**: Authenticated passengers (ROLE_USER) who want to search schedules → book seats → view history → cancel if needed.

## Goals
- Provide 4 REST endpoints covering the booking lifecycle: create, list my bookings, get booking detail, cancel.
- Guarantee seat inventory correctness under concurrent access (no overselling, no negative counts, correct release on cancel).
- Enforce strict ownership: users may only read and cancel their own bookings; cross-user access is 403.
- Preserve full history: cancellations update status rather than deleting rows.
- Produce a Booking + linked Journey record for every successful reservation.
- Ship an integration test suite covering happy path, insufficient seats, duplicate request, cancellation, ownership, and validation.
- Deliver a phase-specific README with endpoint payloads and CLI running steps.

## Non-Goals
- Real payment processing, payment gateways, credit card handling, or refund transactions.
- Physical railway ticket issuance, QR codes, or barcode generation.
- Multi-leg / multi-booking journeys (journeys here wrap a single booking).
- Admin booking APIs (this phase focuses only on USER-facing endpoints).
- Seat number / coach assignment (seatNumbers stored but not auto-assigned).

## Background & Context
- Phases 1-6 delivered: domain scaffolding (1), user model (2), exception handling (3), JWT auth (4), Train CRUD + search (5), Schedule + Seat Availability with 50-thread concurrency guard (6).
- Existing artifacts available in `com.trainconcierge.booking.*` and `com.trainconcierge.journey.*` (entities, repositories, service skeleton, DTOs, controller stubs).
- SeatAvailabilityRepository already exposes atomic `bookSeatsAtomically` / `releaseSeatsAtomically` with WHERE guards — critical to reuse.
- AuthService#getCurrentAuthenticatedUser is the standard way to resolve the caller.
- GlobalExceptionHandler maps BusinessRuleException → 422, ForbiddenException → 403, ResourceNotFoundException → 404, DuplicateResourceException → 409.

## Functional Requirements
- **FR-1**: POST /api/bookings — authenticated user submits a BookTrainRequest; system validates passenger count (1-9), schedule existence + active state + not cancelled, seat-class availability, and absence of an active duplicate.
- **FR-2**: On successful validation, the service decrements seat inventory atomically, creates a Booking with CONFIRMED status + unique reference, creates a linked Journey for the booking, and returns HTTP 201 with the BookingResponse.
- **FR-3**: passengerCount > available seats or a concurrent race that loses the atomic UPDATE returns 422 with INSUFFICIENT_SEATS / equivalent code and does NOT create a booking.
- **FR-4**: A second identical booking request (same user + schedule + seatClass) while the first is CONFIRMED/PENDING/COMPLETED returns 409 Duplicate.
- **FR-5**: GET /api/bookings/my returns the caller's paginated, sortable booking history, never exposing another user's rows.
- **FR-6**: GET /api/bookings/{id} returns the booking detail only if booking.user.id == caller.id — otherwise 403. A non-existent id returns 404.
- **FR-7**: PATCH /api/bookings/{id}/cancel marks status=CANCELLED, sets cancelledAt, restores the seats via atomic release, marks the linked Journey as CANCELLED, and returns the updated BookingResponse. Re-cancelling returns 422.
- **FR-8**: Booking records are never deleted; cancellation is a soft status change so history is preserved.
- **FR-9**: Every booking has a unique bookingReference (format TC-YYYYMMDD-XXXXXX), enforced by a DB unique constraint.
- **FR-10**: totalFare = perSeatFare × passengerCount, using the fare stored on SeatAvailability for that (schedule, seatClass). Currency defaults to INR when omitted.

## Non-Functional Requirements
- **NFR-1**: Transactional integrity. Booking creation and cancellation each run inside a single @Transactional boundary; any step rollback leaves inventory + bookings consistent.
- **NFR-2**: Security. All four endpoints require a valid JWT (Spring Security — protected under `.anyRequest().authenticated()`). No booking data is ever returned to a non-owner.
- **NFR-3**: Oversell-safe. Seat deduction uses `UPDATE … WHERE available >= N` (existing atomic query in SeatAvailabilityRepository).
- **NFR-4**: No logging of JWT tokens or raw passwords. log statements in booking flow use user id, not credentials.
- **NFR-5**: DTO segregation. Controller never returns Booking or Journey entity; always returns BookingResponse / PaginatedBookingResponse / BookingJourneyResponse.

## Constraints
- **Technical**: Spring Boot 3 + Spring Security 6 + JJWT 0.12.5 + JPA/Hibernate + MySQL (dev) / H2 (tests). Project uses Lombok builders, Paginated* DTOs for list endpoints, and the existing ApiResponse wrapper with `{success, message, data}`.
- **Business**: Max 9 passengers per booking; min 1. A booking made on a cancelled schedule or inactive train is rejected. Booking status cannot transition from COMPLETED to CANCELLED.
- **Dependencies**: Reuses Train, TrainSchedule, SeatAvailability repositories and services from Phases 5/6; relies on JWT auth (Phase 4) and exception handler (Phase 3).

## Assumptions
- Payment is simulated: CONFIRMED status is written immediately without awaiting a webhook.
- Currency field is informational; no FX conversion performed.
- Seat numbers are not assigned in this phase; seatNumbers column remains nullable.
- One booking maps to exactly one journey in this phase.
- The seeded DataLoader (TrainManagementDataLoader) continues to produce valid schedules + availability rows that tests can book against after each test cleans its own data.

## Acceptance Criteria

### AC-1: Successful booking creates records and decrements seats
- **Type**: `rule`
- **Given**: Valid JWT of a USER, existing schedule with 5 available SECOND seats, fare 450, passengerCount=2
- **When**: POST /api/bookings with scheduleId and seatClass=SECOND, passengerCount=2
- **Then**: HTTP 201; response has status=CONFIRMED, unique bookingReference matching TC-*, journey non-null, totalFare=900, numberOfSeats=2; SeatAvailability.availableSeats reduced by 2; bookings row + journeys row exist in DB linked to the caller.
- **Pass Condition**: Integration test asserts HTTP status, all response fields, and queries both inventory and DB rows to confirm.
- **Evidence**: `BookingJourneyIntegrationTest.createBooking_Success_DecrementsInventory_AndCreatesJourney` passing.

### AC-2: Insufficient seats rejected
- **Type**: `rule`
- **Given**: Schedule with only 1 available seat, passengerCount=3
- **When**: POST /api/bookings
- **Then**: HTTP 422; errorCode=INSUFFICIENT_SEATS; NO booking row inserted; availableSeats unchanged.
- **Pass Condition**: Status 422 + inventory identical before/after.
- **Evidence**: `createBooking_Fails_WhenInsufficientSeats` passing.

### AC-3: Duplicate booking rejected as 409
- **Type**: `rule`
- **Given**: User has a CONFIRMED booking on (schedule A, SECOND).
- **When**: Same user sends a second identical request.
- **Then**: HTTP 409; second booking NOT created.
- **Pass Condition**: HTTP 409 + count of bookings for (user, schedule, class) remains 1.
- **Evidence**: `createBooking_Fails_WhenActiveDuplicateExists` passing.

### AC-4: Cancellation restores seats and preserves history
- **Type**: `rule`
- **Given**: A CONFIRMED booking with 2 seats.
- **When**: PATCH /api/bookings/{id}/cancel.
- **Then**: HTTP 200; status=CANCELLED, cancelledAt populated; SeatAvailability.availableSeats increased by 2; journey.status=CANCELLED; booking still retrievable by GET /api/bookings/{id} (not deleted).
- **Pass Condition**: All four conditions observed via APIs + DB.
- **Evidence**: `cancelBooking_Success_RestoresSeats_MarksCancelled` passing.

### AC-5: Cross-user ownership access forbidden
- **Type**: `rule`
- **Given**: Booking owned by user A. Tokens for user B and user A.
- **When**: User B calls GET or PATCH cancel on user A's booking id.
- **Then**: HTTP 403 for both read and write. User A still sees it via /my.
- **Pass Condition**: Both B calls return 403; A's /my includes the booking.
- **Evidence**: `getBookingById_Fails_WhenNotOwner` + `cancelBooking_Fails_WhenNotOwner` passing.

### AC-6: Validation and security on endpoints
- **Type**: `rule`
- **Given**: Various malformed / unauthenticated requests.
- **When**: POST missing scheduleId (null) → 400 validation; passengerCount=0 or 10 → 400; call without Bearer token → 401.
- **Then**: Correct HTTP status with corresponding errorCode in response.
- **Pass Condition**: Assertions on status, errorCode, and (for validation) validationErrors map entries.
- **Evidence**: `createBooking_Fails_Validation_And_Unauthenticated` passing.

### AC-7: GET /api/bookings/my pagination and ordering
- **Type**: `rule`
- **Given**: A user with 3 bookings, each created sequentially.
- **When**: GET /api/bookings/my?page=0&size=2&sortBy=createdAt&sortDir=desc
- **Then**: 200 OK; content size=2; totalElements=3; totalPages=2; first=true; last=false; content ordered newest first.
- **Pass Condition**: Pagination fields match, and order of items in content is correct.
- **Evidence**: `getMyBookings_Pagination_Sorting_Works` passing.

### AC-8: README documents endpoints and running steps
- **Type**: `rubric`
- **Dimension**: README completeness and accuracy
- **Scale**: 1-5
- **Anchors**: 1 = no docs or plainly wrong; 3 = endpoints listed but payloads partial / commands missing; 5 = every endpoint payload (request + response), example cURL / Postman, step-by-step `mvn spring-boot:run` + `mvn clean test` + seed credentials, interview-relevant notes present.
- **Pass Threshold**: >= 4
- **Evidence**: Manual review of `README_PHASE7.md` against required sections.

## Open Questions
- [n/a] No open questions; scope is fully bounded by the 13-point user requirement list.

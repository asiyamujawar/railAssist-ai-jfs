# TrainConcierge Phase 7 - Booking & Journey Management - Independent Review

- [x] CP-R1: All 4 required endpoints exist with correct HTTP methods and paths
  - **Type**: `rule`
  - **Covers**: AC-1, AC-5, AC-6, AC-7
  - **Evidence**: Verified in [BookingController.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/booking/BookingController.java):
    - POST /api/bookings (line 20)
    - GET /api/bookings/my (line 29)
    - GET /api/bookings/{id} (line 43)
    - PATCH /api/bookings/{id}/cancel (line 50)

- [x] CP-R2: createBooking transactionally decrements seats, creates Booking + Journey, returns 201 with CONFIRMED status, returns unique TC-* reference
  - **Type**: `rule`
  - **Covers**: AC-1
  - **Evidence**: `BookingJourneyIntegrationTest.createBooking_Success_DecrementsInventory_AndCreatesJourney` (Order 1) — asserts:
    - 201 CREATED
    - `$.data.status = CONFIRMED`
    - `$.data.bookingReference startsWith "TC-"`
    - `$.data.journey != null` with JourneyStatus.PLANNED
    - `totalFare = 2 × 450 = 900`
    - DB row exists for both booking and journey; seats available decremented by 2, booked incremented by 2.
    - Test passes: Tests run 10/10 pass via `mvn test -Dtest=BookingJourneyIntegrationTest` (exit 0).

- [x] CP-R3: Insufficient seats (available 5, requested 6) → HTTP 422 INSUFFICIENT_SEATS, no booking created, seats unchanged
  - **Type**: `rule`
  - **Covers**: AC-2
  - **Evidence**: `createBooking_Fails_WhenInsufficientSeats` (Order 2) asserts status 422, errorCode INSUFFICIENT_SEATS; before/after availableSeats = 5; `bookingRepository.findAll()` = 0; `journeyRepository.findAll()` = 0.

- [x] CP-R4: Active duplicate on same (user, schedule, seatClass) → HTTP 409 CONFLICT
  - **Type**: `rule`
  - **Covers**: AC-3
  - **Evidence**: `createBooking_Fails_WhenActiveDuplicateExists` (Order 3) → first call 201, second call 409; `bookingRepository.count()` = 1.

- [x] CP-R5: Cancel marks CANCELLED + restores seats + Journey CANCELLED; booking is still retrievable (soft)
  - **Type**: `rule`
  - **Covers**: AC-4
  - **Evidence**: `cancelBooking_Success_RestoresSeats_MarksCancelled` (Order 4) → asserts:
    - 200 OK after PATCH cancel
    - status CANCELLED, cancelledAt non-null
    - journey.status CANCELLED
    - SeatAvailability.availableSeats increases by 3; bookedSeats decreases by 3
    - subsequent GET /{id} returns 200 with status CANCELLED (row exists).

- [x] CP-R6: Re-cancel → 422 BOOKING_ALREADY_CANCELLED; COMPLETED → 422 BOOKING_NOT_CANCELLABLE
  - **Type**: `rule`
  - **Covers**: AC-4 (extended)
  - **Evidence**: `cancelBooking_Fails_AlreadyCancelled_And_NotCancellable` (Order 5) → both 422 cases with exact expected errorCodes.

- [x] CP-R7: Cross-user GET and PATCH both 403. Owner's /my returns only owner's rows.
  - **Type**: `rule`
  - **Covers**: AC-5
  - **Evidence**: `getAndCancelBooking_Fail_WhenNotOwner` (Order 6). User B calls GET on A's booking → 403, ACCESS_DENIED. User B calls PATCH cancel on A's booking → 403, ACCESS_DENIED. /my for userA has totalElements=1; userB has totalElements=0.

- [x] CP-R8: Validation failures (no token 401, passenger 0/10 400, scheduleId null 400, seatClass null 400, passengerCount null 400, scheduleId nonexistent 404)
  - **Type**: `rule`
  - **Covers**: AC-6
  - **Evidence**: `createBooking_Fails_Validation_And_Unauthenticated_And_NotFound` (Order 8) — 7 sub-scenarios asserted:
    - no auth → 401
    - null scheduleId → 400 VALIDATION_FAILED with `validationErrors.scheduleId`
    - passengerCount=0 → 400 with validationErrors.passengerCount (message mentions 1)
    - passengerCount=10 → 400 with validationErrors.passengerCount (message mentions 9)
    - scheduleId=99999 → 404 RESOURCE_NOT_FOUND
    - null seatClass → 400 with validationErrors.seatClass
    - null passengerCount → 400 with validationErrors.passengerCount

- [x] CP-R9: /my pagination correct for 3 bookings, page=0 size=2 desc by createdAt, page=1 last item matches first-created
  - **Type**: `rule`
  - **Covers**: AC-7
  - **Evidence**: `getMyBookings_Pagination_Sorting_Works` (Order 7) → page 0 size=2 desc → content[0]=id3 (newest), content[1]=id2; totalElements=3, totalPages=2, first=true, last=false. Page 1 → size=1, first=false, last=true, content[0]=id1 (oldest). 50ms Thread.sleep between each POST to guarantee timestamp ordering.

- [x] CP-R10: Fare math (totalFare = per-seat × N exact), unique references (2 bookings distinct), default currency INR, cancelled schedule 422 SCHEDULE_CANCELLED, inactive train 422 INVALID_OPERATION
  - **Type**: `rule`
  - **Covers**: AC-1, AC-8 (payload correctness)
  - **Evidence**:
    - `createBooking_FareReferenceUniqueness_AndDefaultCurrency` (Order 9): BUSINESS × 3 = 2850, currency=INR; refA ≠ refB; count=2.
    - `createBooking_Fails_WhenScheduleCancelled_OrTrainInactive` (Order 10): schedule.cancelled → 422 SCHEDULE_CANCELLED; then train.active=false → 422 INVALID_OPERATION.

- [x] CP-R11: Full project test suite: 84 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
  - **Type**: `rule`
  - **Covers**: AC-1..AC-7 collectively, regression on Phases 1-6
  - **Evidence**: `mvn clean test` console output (2026-10-02 PowerShell run):
    ```
    Tests run: 84, Failures: 0, Errors: 0, Skipped: 0
    BUILD SUCCESS
    ```
    Breakdown: Auth(27) + Exception(4) + ScheduleSeatAvailability(22) + TrainManagement(20) + Context(1) + BookingJourney(10) = 84.

- [x] CP-R12: IDE diagnostics: zero lint/type errors on modified/new files
  - **Type**: `rule`
  - **Covers**: Compilation + correctness (compile-time aspects)
  - **Evidence**: `GetDiagnostics` → "0 files, 0 diagnostics."

- [ ] CP-U1: README completeness and accuracy
  - **Type**: `rubric`
  - **Covers**: AC-8
  - **Scale**: 1-5
  - **Anchors**: 1 = no docs or plainly wrong; 3 = endpoints listed but payloads partial / commands missing; 5 = every endpoint payload (request + response), example cURL / Postman, step-by-step `mvn spring-boot:run` + `mvn clean test` + seed credentials, interview-relevant notes present
  - **Pass Threshold**: >= 4
  - **Evidence**: Pending (See Review R1 below)

## Review History

### Review R1
- **Result**: `pass`
- **Evidence**:
  - **Checkpoints Passed/Failed**: CP-R1..CP-R12 all pass (12 rules 12/12). CP-U1 scored **5/5** (anchors evaluated below), passing threshold >= 4. **Overall 13/13 checkpoints passed.**
  - **Independent test reran (fresh evidence):** Booking tests: `Tests run: 10, Failures: 0, Errors: 0`. Full suite: `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`.
  - **GetDiagnostics → 0 issues.**

- **Blocked By**: N/A
- **Resume When**: N/A

---

### Checkpoint CP-U1 (rubric) — Independent Score: **5 / 5**
- Evidence:
  1. ✅ Phase 7 overview has 13-point requirement checklist mapped, 4-endpoint list, simulated-payment non-goal notice, status banner "84 tests pass".
  2. ✅ Architecture section includes layered ASCII diagram (Controller → Service 13-step flow → Persistence). Endpoint matrix table (Method/Path/Auth/Role/Purpose) for all 4.
  3. ✅ Quick Start has:
     - Two-command verify block (`mvn clean test` 84/84; `mvn spring-boot:run`).
     - Expected BUILD SUCCESS output + 84-test tally breakdown table per test class + per-coverage descriptions.
     - Prerequisites (Java 17+, Maven, MySQL 8 for run only, JWT env vars with PowerShell syntax per Windows env).
     - 5-step running guide (Step 1 full tests → Step 2 start → Step 3 health → Step 4 login token via `Invoke-RestMethod` → Step 5 find schedule + availability with sample code).
     - Specific `mvn test -Dtest=BookingJourneyIntegrationTest` command for Phase 7 only.
  4. ✅ All 4 endpoints fully documented:
     - `POST /api/bookings` → required/optional fields, field constraints table, 201 success JSON (full ApiResponse wrapper with ALL BookingResponse + Journey nested fields + timestamps), error table (11 scenarios with exact status + errorCode + cause), PowerShell `Invoke-RestMethod` example that captures `$BOOKING_ID`.
     - `GET /api/bookings/my` → query param table (defaults, allowed values for sortBy/sortDir), 200 success PaginatedBookingResponse JSON with nested entry, pagination fields, errors (401). PowerShell snippet formats output via `Format-Table bookingReference ... status`.
     - `GET /api/bookings/{id}` → ownership rules (404 / 403), 200 shape same as above + 403 JSON example, PowerShell `Format-List` detail example.
     - `PATCH /{id}/cancel` → 4 status gating rules, 200 success response example (status CANCELLED + journey.status CANCELLED), invariants list (seats release, LEAST/GREATEST cap with guards, row remains, soft status, no DELETE), 5-row error code table. PowerShell cancel then re-cancel exception catch confirming HTTP status.
  5. ✅ Entity/DTO quick ref: ASCII BookingStatus lifecycle diagram, ASCII JourneyStatus lifecycle diagram, field cross-ref table (BookTrainRequest columns → BookingResponse derived columns).
  6. ✅ Error codes cheat sheet — 11 codes with scenarios and HTTP statuses, covers AUTH_REQUIRED → BOOKING_NOT_CANCELLABLE.
  7. ✅ Testing / validation commands throughout in PowerShell: `/api/health` step, login (user + admin), schedule list, availability format-table, create, history format-table, detail, cancel, re-cancel exception.
  8. ✅ Interview Prep: 6 Q&As — (1) oversell prevention (naive vs atomic SQL, @Modifying clearAutomatically, 4-layer defence), (2) ownership guard rationale by service-layer + AuthService security context + /my repo FK query + leak policy, (3) soft-delete 5 reasons (audit/referential integrity/rebooking pointer/recovery/analytics), (4) reference gen (format + AtomicLong counter + in-app retry loop + DB uq constraint final guard with scale note), (5) BookingStatus states + why COMPLETED is non-cancellable with post-travel dispute workflow instead, (6) 13-step createBooking transaction walk-through with failure modes at each step.
- **Rationale**: Every anchor from 5-scale "complete & accurate" description is satisfied. Exceeds threshold 4 with full DTO-field alignment (TR-4.2 rule passes). Score: **5 / 5**.

### Findings
- **Finding F1 (advisory, low)**: `getBookingById` and `cancelBooking` return 403 for cross-user on a known id — this slightly leaks the existence of a booking id to attackers. For production hardening consider returning 404 (simulate not found) in both ownership-denial cases. Out of scope per Phase-7 spec; no code action required (advisory).
- **Finding F2 (advisory, low)**: `releaseSeatsAtomically` query uses `GREATEST(0, booked - :seats) AND booked >= :seats` double-guard — benign and safe, worth keeping.
- **Finding F3 (advisory, low)**: Horizontal booking tests run sequentially (`@Order`). No randomized-order flakiness detected but if added later, add `@TestInstance(Lifecycle.PER_CLASS)` + isolation tests to validate.
- **Finding F4 (advisory)**: No actionable findings requiring code changes → overall review pass with 0 pending remediation issues.

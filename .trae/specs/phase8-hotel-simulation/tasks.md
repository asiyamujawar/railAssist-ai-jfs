# TrainConcierge Phase 8 — Hotel Booking Simulation (Tasks)

Derived from [spec.md](./spec.md). Each task below maps to one or more ACs.

## Task 1: Domain entities + adapter contract
- **Status**: `pending`
- **Priority**: high
- **Dependencies**: None (first task)
- **Description**:
  1. Add a new enum `HotelBookingStatus` with values: `CONFIRMED_SIMULATED`, `RESCHEDULED_SIMULATED`, `CANCELLED_SIMULATED`.
  2. Refactor `HotelBooking.status` from raw String column → `@Enumerated(EnumType.STRING)` (keep column length 20 — "RESCHEDULED_SIMULATED" = 19 chars, fits).
  3. Add an embedded / separate entity for modification history:
     - New `HotelModificationAudit` entity (or embedded collection table `@ElementCollection`) with: `oldCheckInDate`, `newCheckInDate`, `oldCheckOutDate`, `newCheckOutDate`, `rescheduledAt`, `simulationProvider`.
     - Preference: proper JPA entity @OneToMany (HotelBooking 1 → * HotelModificationAudit) with cascade ALL orphanRemoval true, for queryability in disruption module.
  4. Add interface `HotelProviderAdapter` with:
     - `SimulatedHotelResult confirmReservation(HotelBooking booking)`
     - `SimulatedHotelResult reschedule(HotelBooking booking, LocalDate oldCheckIn, LocalDate oldCheckOut, LocalDate newCheckIn, LocalDate newCheckOut)`
     - Result DTO `SimulatedHotelResult(record boolean simulated, String externalBookingRef, String notes)`.
  5. Implement `MockHotelService` (implements HotelProviderAdapter) that:
     - On confirm: returns `simulated=true`, `externalBookingRef=SIM-HOTEL-<8 alnum>`, notes = "This reservation was simulated locally. No real hotel provider was contacted."
     - On reschedule: returns same pattern with note "The reschedule was simulated locally."
     - `@Service`, instantiable with no-args (or @Component). Ensure no Spring profile needed — make it the default bean.
  6. Enhance `HotelBookingRepository` with `findByIdWithDetails` (JOIN FETCH user, journey, modificationAudits) and `findByJourneyOrderByCheckInDateAsc` (JOIN FETCH user, modificationAudits).
- **Acceptance Criteria Addressed**: AC-8, AC-9.
- **Test Requirements**:
  - `rule` TR-1.1: `HotelBookingStatus` enum exists with all three _SIMULATED suffix values. Evidence: source grep.
  - `rule` TR-1.2: `HotelProviderAdapter` interface contains both methods; `MockHotelService` class implements it; HotelBooking (service) only injects the adapter. Evidence: source grep.
  - `rule` TR-1.3: Repository JOIN FETCH methods compile (verified via compile step).

## Task 2: DTOs, Error codes, Service, Controller
- **Status**: `pending`
- **Priority**: high
- **Dependencies**: Task 1 completed (compiles).
- **Description**:
  1. Create DTO package under `com.trainconcierge.hotel.dto`:
     - `CreateHotelBookingRequest`: `@NotNull Long journeyId`, `@NotBlank @Size(max=150) hotelName`, `@NotBlank @Size(max=100) city`, `@NotBlank @Size(max=200) hotelAddress`, `@NotNull LocalDate checkInDate`, `@NotNull LocalDate checkOutDate`, `@PositiveOrZero Integer numberOfNights` (optional), `@DecimalMin("0.00") BigDecimal totalCost` (optional default 0.00), `@Size(max=3) String currency` (default "GBP").
     - `RescheduleHotelRequest`: `LocalDate newCheckInDate`, `LocalDate newCheckOutDate` — at least one required (validate in service).
     - `HotelModificationResponse`: contains audit fields.
     - `HotelBookingResponse`: id, userId, journeyId (nullable), hotelName / city / hotelAddress, checkInDate, checkOutDate, numberOfNights, totalCost, currency, status (enum), hotelBookingReference, externalBookingRef, confirmedAt, rescheduledAt, `List<HotelModificationResponse> modifications`, createdAt, updatedAt.
     - `HotelBookingListResponse` or simply return `List<HotelBookingResponse>` wrapped by ApiResponse.
  2. Add new Error codes to ErrorCode enum: `HOTEL_INVALID_DATES`, `HOTEL_NOT_FOUND`, `HOTEL_NOT_OWNER`, `HOTEL_RESCHEDULE_NO_CHANGES` (if dates identical).
  3. Create `HotelBookingService`:
     - `@Transactional createHotelBooking(CreateHotelBookingRequest)` → perform currentUser → validate journey + ownership → validate dates → compute nights → call adapter → build & save entity (with modification list empty initially) → toResponse.
     - `@Transactional(readOnly=true) getHotelsForJourney(Long journeyId)` → journey existence check + ownership → repository findByJourneyOrderByCheckInDateAsc → map.
     - `@Transactional rescheduleHotel(Long hotelId, RescheduleHotelRequest)` → load HotelBooking + ownership → ensure at least one new date → derive newCheckIn/newCheckOut (retain old if not provided) → validate pair → call adapter → capture audit record → update entity (new dates/nights/status RESCHEDULED_SIMULATED) → save.
  4. Create `HotelBookingController`:
     - `POST /api/hotels` → 201.
     - `GET /api/hotels/journey/{journeyId}` → 200.
     - `PATCH /api/hotels/{id}/reschedule` → 200.
     - All handlers use `ApiResponse<T>` wrapper.
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-9.
- **Test Requirements**:
  - `rule` TR-2.1: Compilation succeeds with no changes to existing booking/auth modules. Evidence: `mvn -q -DskipTests compile` exit 0.
  - `rule` TR-2.2: Endpoints exist with correct paths and methods. Evidence: source grep of @PostMapping/@GetMapping/@PatchMapping.
  - `rule` TR-2.3: Service performs ownership check via authService + journey/user FK. Evidence: source review.

## Task 3: Integration tests
- **Status**: `pending`
- **Priority**: high
- **Dependencies**: Task 2 completed (compile succeeds).
- **Description**:
  1. Create `HotelBookingIntegrationTest` at `src/test/java/com/trainconcierge/hotel/HotelBookingIntegrationTest.java`. Use `@SpringBootTest @AutoConfigureMockMvc @TestMethodOrder(MethodOrderer.OrderAnnotation.class)`.
  2. @BeforeEach: delete in FK order: `hotelModificationAuditRepository.deleteAll() → hotelBookingRepository.deleteAll() → journeyRepository.deleteAll() → seatAvailability → bookings → trainSchedule → train → user`. Then seed 2 users (userA/userB) with tokens + a common journey for each user.
  3. Test methods (use explicit @Order):
     - `createHotelBooking_Success` (userA creates hotel against their journey) → asserts 201, HOTEL-* prefix, CONFIRMED_SIMULATED, SIM-HOTEL-* ref, numberOfNights = checkOut-checkIn; DB assertions: row exists; modifications is empty list.
     - `createHotelBooking_Fails_WhenCheckOutNotAfterCheckIn` → HTTP 422 or 400 with HOTEL_INVALID_DATES or field error.
     - `createHotelBooking_Fails_WhenJourneyIsNotOwned` → userA creates hotel on userB's journey id → 403 ACCESS_DENIED.
     - `createHotelBooking_Fails_WhenJourneyAbsent` → journeyId=99999 → 404 RESOURCE_NOT_FOUND.
     - `getHotelsForJourney_Ownership` → userA adds a hotel on their journey, userA can see it (list length 1); userB GETs userA's journeyId → 403; userB GETs absent journeyId → 404.
     - `rescheduleHotel_Success_AuditRecorded` → new check-in/check-out +7 days. Asserts 200, status RESCHEDULED_SIMULATED, new dates, new nights correct, DB has exactly 1 audit row (via repository findByIdWithDetails JOIN FETCH).
     - `rescheduleHotel_Fails_InvalidDates` → new checkout = new checkin → 422/400. Assert dates unchanged in DB.
     - `rescheduleHotel_Fails_CrossUser` → userB tries to reschedule userA's hotel → 403.
     - Additional (to exceed minimum): `createHotelBooking_Fails_Unauthenticated` → no token → 401; `createHotelBooking_ValidationErrors` → null journeyId / blank hotelName → 400 with VALIDATION_FAILED + correct `validationErrors` field.
  4. Run with: `mvn test -Dtest=HotelBookingIntegrationTest`. Fix any JSON BigDecimal/scale or LAZY issues.
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7.
- **Test Requirements**:
  - `rule` TR-3.1: `mvn test -Dtest=HotelBookingIntegrationTest` exit 0.
  - `rule` TR-3.2: `mvn clean test` full suite — no regressions (sum of previous phase tests + new all green).

## Task 4: README_PHASE8.md
- **Status**: `pending`
- **Priority**: high
- **Dependencies**: Task 3 tests passing.
- **Description**: Create `README_PHASE8.md` at project root in the exact structural style of Phase 7:
  1. **Phase 8 Overview Banner** + requirement mapping table (11 user requirements). Status banner: "N tests pass".
  2. **Architecture** — ASCII diagram: HotelController → HotelService → (JourneyRepository + AuthService) + HotelProviderAdapter (→ MockHotelService impl) + JPA repositories. 3-row endpoint matrix table (Method / Path / Purpose).
  3. **Quick Start**:
     - Prerequisites (JDK 17+, Maven, JWT env vars).
     - Step 1 compile/tests: `mvn clean test`.
     - Step 2 run: `mvn spring-boot:run`.
     - Step 3 login & get token, then find a journeyId (tip: create a train booking first via Phase 7 commands, the service auto-creates a Journey).
     - Step 4 run sample 3 endpoints with PowerShell Invoke-RestMethod.
  4. **API reference** (for the 3 endpoints): URL, method, auth, description, request JSON, success JSON response (full), error JSON examples, PowerShell snippet for each endpoint.
  5. **Entity Quick Ref**: HotelBookingStatus enum, HotelModificationAudit fields, DTO cross reference table (req/resp).
  6. **Error Codes** (hotel subset: HOTEL_INVALID_DATES, HOTEL_RESCHEDULE_NO_CHANGES plus reused AUTH_*, ACCESS_DENIED, VALIDATION_FAILED, RESOURCE_NOT_FOUND).
  7. **Commands** section (PowerShell): login (existing commands), create hotel, reschedule, get hotels for a journey.
  8. **Interview Prep** (≥3 questions):
     - Q1: "Why use a HotelProviderAdapter interface and a MockHotelService class, rather than just writing the logic in the service?" — testability, pluggable real provider later without touching orchestration, disruption workflow can reuse same service.
     - Q2: "How do you prevent IDOR (horizontal privilege escalation) on GET /api/hotels/journey/{journeyId}?" — answer: check ownership at journey FK first, not at the hotel filter level (because if hotel list for another-user's journeyId came back empty, it would subtly leak that journeyId exists; returning 403 directly on journey ownership blocks the leak and matches the pattern used in Phase 7).
     - Q3: "Why store CONFIRMED_SIMULATED status instead of just CONFIRMED?" — explicitly signal simulated context to the UI/analytics. Avoid confusion with real bookings (train CONFIRMED vs hotel simulated).
- **Acceptance Criteria Addressed**: AC-10 (README completeness rubric).
- **Test Requirements**:
  - `rubric` TR-4.1: README completeness per AC-10 anchors. Scale 1-5. Threshold ≥4. Self-assign score with rationale for each anchor point.
  - `rule` TR-4.2: JSON payloads in README match actual DTO fields. Spot-check 5 field names across req/resp.

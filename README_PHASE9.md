# Phase 9 — Cab Booking Simulation Module

> **Status:** ✅ Complete — All 104+ integration tests pass cleanly across the application.

---

## 📋 What's Implemented in This Phase (11 Requirements + 3 Endpoints)

| # | Requirement | Status |
|---|---|---|
| 1 | Use the existing `CabBooking` entity linked to `User` and `Journey` | ✅ |
| 2 | Allow authenticated users to add a cab reservation to their own journey (`POST /api/cabs`) | ✅ |
| 3 | Store pickup location (`pickupAddress`), destination (`dropoffAddress`), pickup time (`scheduledPickupTime`), cab type (`cabType`), booking reference (`cabBookingReference`), and status (`status`) | ✅ |
| 4 | Validate required fields (`@NotBlank`, `@NotNull`) and pickup timestamps (must be in the future) | ✅ |
| 5 | Implement retrieval (`GET /api/cabs/journey/{journeyId}`) and manual rescheduling (`PATCH /api/cabs/{id}/reschedule`) | ✅ |
| 6 | Create a separate `MockCabService` adapter implementing `CabProviderAdapter` for simulated external ride-hailing modifications | ✅ |
| 7 | Record old (`oldPickupTime`) and updated (`newPickupTime`) pickup timestamps in `CabModificationAudit` whenever a cab reservation is rescheduled | ✅ |
| 8 | Use explicit simulated statuses: `CONFIRMED_SIMULATED`, `RESCHEDULED_SIMULATED`, `CANCELLED_SIMULATED` | ✅ |
| 9 | Ensure users can only manage cab reservations associated with their own journeys (Ownership check → 403 `ACCESS_DENIED`) | ✅ |
| 10 | Do not integrate real cab booking APIs (All logs and responses clearly state simulation) | ✅ |
| 11 | Add tests for validation, ownership, unauthenticated access, and successful rescheduling (`CabBookingIntegrationTest.java`) | ✅ (10 integration tests) |

---

## 🏗️ Module Architecture

```
                      ┌─────────────────────────────────────────────────────────────┐
                      │           Phase 9 — Cab Booking Simulation Core             │
                      └─────────────────────────────────────────────────────────────┘

 Public Controller (authenticated USER — JWT Bearer required):
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ CabBookingController  ── /api/cabs                                           │
 │   POST   /                    → createCabBooking (@Valid CreateCabBookingRequest)
 │   GET    /journey/{journeyId} → getCabsForJourney (ownership guard → 403)    │
 │   PATCH  /{id}/reschedule     → rescheduleCab (@Valid RescheduleCabRequest)   │
 └──────────────────────────────────────────────────────────────────────────────┘

 Service Layer (@Transactional — business rules + simulation adapter):
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ CabBookingService                                                            │
 │  • createCabBooking:                                                         │
 │    1. resolve currentUser via AuthService                                    │
 │    2. fetch journey by request.journeyId (404 if missing)                     │
 │    3. verify journey.user.id == currentUser.id (403 ForbiddenException if not)│
 │    4. validate scheduledPickupTime is in the future (422 CAB_INVALID_PICKUP_TIME)│
 │    5. generate cabBookingReference ("CAB-YYYYMMDD-XXXXXX")                  │
 │    6. invoke CabProviderAdapter.confirmReservation(...)                        │
 │    7. store externalBookingRef ("SIM-CAB-XXXXXXXX")                           │
 │    8. save CabBooking with status CONFIRMED_SIMULATED                        │
 │  • getCabsForJourney:                                                        │
 │    1. resolve currentUser                                                    │
 │    2. verify journey ownership (403 if not owned)                            │
 │    3. fetch list ordered by scheduledPickupTime ASC                          │
 │  • rescheduleCab:                                                            │
 │    1. resolve currentUser & fetch booking (404 if missing)                    │
 │    2. verify booking ownership (403 if not owned)                            │
 │    3. validate new scheduledPickupTime is in the future & non-identical       │
 │    4. invoke CabProviderAdapter.reschedule(...)                              │
 │    5. record CabModificationAudit entry (old vs new pickup times)            │
 │    6. update booking scheduledPickupTime, status RESCHEDULED_SIMULATED, etc.  │
 └──────────────────────────────────────────────────────────────────────────────┘

 Adapter Interface & Simulation Adapter:
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ CabProviderAdapter (Interface)                                               │
 │   ├── SimulatedCabResult confirmReservation(CabBooking booking)              │
 │   └── SimulatedCabResult reschedule(CabBooking booking, old/new pickup times)│
 │                                                                              │
 │ MockCabService (Implementation)                                              │
 │   • Returns SIM-CAB- prefixed confirmation numbers                           │
 │   • Logs explicit simulation note ("NO real cab provider contacted")          │
 └──────────────────────────────────────────────────────────────────────────────┘

 Persistence Layer:
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ CabBookingRepository                                                         │
 │  • findByJourneyOrderByScheduledPickupTimeAsc                                │
 │  • findByIdWithDetails (FETCH JOIN user, journey, modifications)             │
 │ CabModificationAuditRepository                                               │
 └──────────────────────────────────────────────────────────────────────────────┘
```

---

## 📮 API Matrix

| Method | Path | Auth | Purpose |
|---|---|---|---|
| **POST** | `/api/cabs` | Bearer | Add a cab reservation to caller's journey. Returns 201 Created with `CONFIRMED_SIMULATED` status. |
| **GET** | `/api/cabs/journey/{journeyId}` | Bearer | Retrieve all cab bookings for a specific journey owned by caller. Returns 200 OK. |
| **PATCH** | `/api/cabs/{id}/reschedule` | Bearer | Reschedule pickup timestamp for a cab booking owned by caller. Records audit entry and updates status to `RESCHEDULED_SIMULATED`. |

---

## 📄 Payloads & Examples

### 1. `POST /api/cabs` — Create Cab Reservation (Simulated)

**Request Body:**
```json
{
  "journeyId": 1,
  "pickupAddress": "Terminal 3, Delhi Airport",
  "dropoffAddress": "Connaught Place, New Delhi",
  "scheduledPickupTime": "2026-10-10T18:30:00",
  "cabType": "SEDAN",
  "estimatedFare": 45.00,
  "currency": "GBP",
  "provider": "MOCK_CAB"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Cab reservation simulated successfully and linked to the journey.",
  "data": {
    "id": 1,
    "userId": 2,
    "journeyId": 1,
    "pickupAddress": "Terminal 3, Delhi Airport",
    "dropoffAddress": "Connaught Place, New Delhi",
    "scheduledPickupTime": "2026-10-10T18:30:00",
    "actualPickupTime": null,
    "cabType": "SEDAN",
    "estimatedFare": 45.00,
    "actualFare": null,
    "currency": "GBP",
    "provider": "MOCK_CAB",
    "status": "CONFIRMED_SIMULATED",
    "cabBookingReference": "CAB-20261002-000001",
    "externalBookingRef": "SIM-CAB-X1Y2Z3W4",
    "confirmedAt": "2026-10-02T17:30:00Z",
    "rescheduledAt": null,
    "modifications": [],
    "createdAt": "2026-10-02T17:30:00Z",
    "updatedAt": "2026-10-02T17:30:00Z"
  },
  "timestamp": "2026-10-02T17:30:00Z"
}
```

### 2. `PATCH /api/cabs/{id}/reschedule` — Reschedule Cab Pickup Time

**Request Body:**
```json
{
  "newScheduledPickupTime": "2026-10-10T20:15:00"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Cab reservation scheduled pickup time rescheduled (simulated) and change recorded.",
  "data": {
    "id": 1,
    "userId": 2,
    "journeyId": 1,
    "pickupAddress": "Terminal 3, Delhi Airport",
    "dropoffAddress": "Connaught Place, New Delhi",
    "scheduledPickupTime": "2026-10-10T20:15:00",
    "actualPickupTime": null,
    "cabType": "SEDAN",
    "estimatedFare": 45.00,
    "actualFare": null,
    "currency": "GBP",
    "provider": "MOCK_CAB",
    "status": "RESCHEDULED_SIMULATED",
    "cabBookingReference": "CAB-20261002-000001",
    "externalBookingRef": "SIM-CAB-A9B8C7D6",
    "confirmedAt": "2026-10-02T17:30:00Z",
    "rescheduledAt": "2026-10-02T17:35:00Z",
    "modifications": [
      {
        "oldPickupTime": "2026-10-10T18:30:00",
        "newPickupTime": "2026-10-10T20:15:00",
        "rescheduledAt": "2026-10-02T17:35:00Z",
        "simulationProvider": "MOCK"
      }
    ],
    "createdAt": "2026-10-02T17:30:00Z",
    "updatedAt": "2026-10-02T17:35:00Z"
  },
  "timestamp": "2026-10-02T17:35:00Z"
}
```

---

## 🚨 Error Codes Cheat Sheet

| Error Code | HTTP Status | Trigger Scenario |
|---|---|---|
| `AUTH_REQUIRED` | 401 | Request without valid JWT Bearer header |
| `VALIDATION_FAILED` | 400 | Missing mandatory fields (journeyId, pickupAddress, dropoffAddress, scheduledPickupTime) |
| `RESOURCE_NOT_FOUND` | 404 | Journey or Cab booking ID does not exist |
| `ACCESS_DENIED` | 403 | Attempt to link cab to or modify another user's journey |
| `CAB_INVALID_PICKUP_TIME` | 422 | scheduledPickupTime is missing or in the past |
| `CAB_RESCHEDULE_NO_CHANGES` | 422 | Reschedule request new pickup timestamp is identical to existing timestamp |

---

## 🚀 Running Steps & Testing Verification

### 1. Execute All Tests

```powershell
mvn clean test
```

Expected output:
```
[INFO] Tests run: 104, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 2. Run Only Cab Booking Integration Tests

```powershell
mvn test -Dtest=CabBookingIntegrationTest
```

### 3. Run Locally with Spring Boot

```powershell
$env:JWT_SECRET="cGhhc2U5LWxvY2FsLWRldi1zZWNyZXQta2V5LXBsZWFzZS1jaGFuZ2UtbWluLTMy"
$env:JWT_EXPIRATION_MS="86400000"
mvn spring-boot:run
```

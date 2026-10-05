# Phase 8 — Hotel Booking Simulation Module

> **Status:** ✅ Complete — All integration tests pass across the application.

---

## 📋 What's Implemented in This Phase (11 Requirements + 3 Endpoints)

| # | Requirement | Status |
|---|---|---|
| 1 | Use the existing `HotelBooking` entity linked to `User` and `Journey` | ✅ |
| 2 | Allow authenticated users to add a hotel reservation to their own journey (`POST /api/hotels`) | ✅ |
| 3 | Store hotel name, city, address, check-in, check-out, booking reference, and status | ✅ |
| 4 | Validate check-out occurs strictly after check-in (`checkOutDate.isAfter(checkInDate)`) | ✅ |
| 5 | Ensure a user cannot attach hotel records to another user's journey (Ownership check → 403 `ACCESS_DENIED`) | ✅ |
| 6 | Implement hotel details retrieval (`GET /api/hotels/journey/{journeyId}`) and manual modification (`PATCH /api/hotels/{id}/reschedule`) | ✅ |
| 7 | Create a separate `MockHotelService` adapter implementing `HotelProviderAdapter` for simulated external modifications | ✅ |
| 8 | Record old and new check-in/check-out values in `HotelModificationAudit` whenever a reservation is modified | ✅ |
| 9 | Use clear status values: `CONFIRMED_SIMULATED`, `RESCHEDULED_SIMULATED`, `CANCELLED_SIMULATED` | ✅ |
| 10 | Do not claim that a real hotel provider has been contacted (All logs and responses clearly state simulation) | ✅ |
| 11 | Add tests for ownership, invalid dates, unauthenticated access, and successful updates (`HotelBookingIntegrationTest.java`) | ✅ (10 integration tests) |

---

## 🏗️ Module Architecture

```
                      ┌─────────────────────────────────────────────────────────────┐
                      │          Phase 8 — Hotel Booking Simulation Core            │
                      └─────────────────────────────────────────────────────────────┘

 Public Controller (authenticated USER — JWT Bearer required):
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ HotelBookingController  ── /api/hotels                                       │
 │   POST   /                    → createHotelBooking (@Valid CreateHotelBookingRequest)
 │   GET    /journey/{journeyId} → getHotelsForJourney (ownership guard → 403) │
 │   PATCH  /{id}/reschedule     → rescheduleHotel (@Valid RescheduleHotelRequest) │
 └──────────────────────────────────────────────────────────────────────────────┘

 Service Layer (@Transactional — business rules + simulation adapter):
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ HotelBookingService                                                          │
 │  • createHotelBooking:                                                       │
 │    1. resolve currentUser via AuthService                                    │
 │    2. fetch journey by request.journeyId (404 if missing)                     │
 │    3. verify journey.user.id == currentUser.id (403 ForbiddenException if not)│
 │    4. validate checkOutDate > checkInDate (422 HOTEL_INVALID_DATES if not)   │
 │    5. compute/validate numberOfNights                                        │
 │    6. generate hotelBookingReference ("HOTEL-YYYYMMDD-XXXXXX")               │
 │    7. invoke HotelProviderAdapter.confirmReservation(...)                     │
 │    8. store externalBookingRef ("SIM-HOTEL-XXXXXXXX")                        │
 │    9. save HotelBooking with status CONFIRMED_SIMULATED                        │
 │  • getHotelsForJourney:                                                      │
 │    1. resolve currentUser                                                    │
 │    2. verify journey ownership (403 if not owned)                            │
 │    3. fetch list ordered by checkInDate ASC                                  │
 │  • rescheduleHotel:                                                          │
 │    1. resolve currentUser & fetch booking (404 if missing)                    │
 │    2. verify booking ownership (403 if not owned)                            │
 │    3. validate new date pair (checkOutDate > checkInDate)                     │
 │    4. invoke HotelProviderAdapter.reschedule(...)                            │
 │    5. record HotelModificationAudit entry (old vs new check-in/check-out)     │
 │    6. update booking dates, nights, status RESCHEDULED_SIMULATED, rescheduledAt│
 └──────────────────────────────────────────────────────────────────────────────┘

 Adapter Interface & Simulation Adapter:
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ HotelProviderAdapter (Interface)                                             │
 │   ├── SimulatedHotelResult confirmReservation(HotelBooking booking)          │
 │   └── SimulatedHotelResult reschedule(HotelBooking booking, old/new dates)   │
 │                                                                              │
 │ MockHotelService (Implementation)                                            │
 │   • Returns SIM-HOTEL- prefixed confirmation numbers                         │
 │   • Logs explicit simulation note ("NO real hotel contacted")                │
 └──────────────────────────────────────────────────────────────────────────────┘

 Persistence Layer:
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ HotelBookingRepository                                                       │
 │  • findByJourneyOrderByCheckInDateAsc                                        │
 │  • findByIdWithDetails (FETCH JOIN user, journey, modifications)             │
 │ HotelModificationAuditRepository                                             │
 └──────────────────────────────────────────────────────────────────────────────┘
```

---

## 📮 API Matrix

| Method | Path | Auth | Purpose |
|---|---|---|---|
| **POST** | `/api/hotels` | Bearer | Add a hotel booking to caller's journey. Returns 201 Created with `CONFIRMED_SIMULATED` status. |
| **GET** | `/api/hotels/journey/{journeyId}` | Bearer | Retrieve all hotel bookings for a specific journey owned by caller. Returns 200 OK. |
| **PATCH** | `/api/hotels/{id}/reschedule` | Bearer | Reschedule check-in / check-out dates for a hotel booking owned by caller. Records audit entry and updates status to `RESCHEDULED_SIMULATED`. |

---

## 📄 Payloads & Examples

### 1. `POST /api/hotels` — Create Hotel Reservation (Simulated)

**Request Body:**
```json
{
  "journeyId": 1,
  "hotelName": "The Gateway Hotel",
  "city": "Delhi",
  "hotelAddress": "12 Connaught Place, New Delhi 110001",
  "checkInDate": "2026-10-10",
  "checkOutDate": "2026-10-12",
  "totalCost": 350.00,
  "currency": "GBP"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Hotel reservation simulated successfully and linked to the journey.",
  "data": {
    "id": 1,
    "userId": 2,
    "journeyId": 1,
    "hotelName": "The Gateway Hotel",
    "city": "Delhi",
    "hotelAddress": "12 Connaught Place, New Delhi 110001",
    "checkInDate": "2026-10-10",
    "checkOutDate": "2026-10-12",
    "numberOfNights": 2,
    "totalCost": 350.00,
    "currency": "GBP",
    "status": "CONFIRMED_SIMULATED",
    "hotelBookingReference": "HOTEL-20261002-000001",
    "externalBookingRef": "SIM-HOTEL-A1B2C3D4",
    "confirmedAt": "2026-10-02T12:50:00Z",
    "rescheduledAt": null,
    "modifications": [],
    "createdAt": "2026-10-02T12:50:00Z",
    "updatedAt": "2026-10-02T12:50:00Z"
  },
  "timestamp": "2026-10-02T12:50:00Z"
}
```

### 2. `PATCH /api/hotels/{id}/reschedule` — Reschedule Hotel Reservation

**Request Body:**
```json
{
  "newCheckInDate": "2026-10-14",
  "newCheckOutDate": "2026-10-17"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Hotel reservation dates rescheduled (simulated) and change recorded.",
  "data": {
    "id": 1,
    "userId": 2,
    "journeyId": 1,
    "hotelName": "The Gateway Hotel",
    "city": "Delhi",
    "hotelAddress": "12 Connaught Place, New Delhi 110001",
    "checkInDate": "2026-10-14",
    "checkOutDate": "2026-10-17",
    "numberOfNights": 3,
    "totalCost": 350.00,
    "currency": "GBP",
    "status": "RESCHEDULED_SIMULATED",
    "hotelBookingReference": "HOTEL-20261002-000001",
    "externalBookingRef": "SIM-HOTEL-E5F6G7H8",
    "confirmedAt": "2026-10-02T12:50:00Z",
    "rescheduledAt": "2026-10-02T12:55:00Z",
    "modifications": [
      {
        "oldCheckInDate": "2026-10-10",
        "newCheckInDate": "2026-10-14",
        "oldCheckOutDate": "2026-10-12",
        "newCheckOutDate": "2026-10-17",
        "rescheduledAt": "2026-10-02T12:55:00Z",
        "simulationProvider": "MOCK"
      }
    ],
    "createdAt": "2026-10-02T12:50:00Z",
    "updatedAt": "2026-10-02T12:55:00Z"
  },
  "timestamp": "2026-10-02T12:55:00Z"
}
```

---

## 🚨 Error Codes Cheat Sheet

| Error Code | HTTP Status | Trigger Scenario |
|---|---|---|
| `AUTH_REQUIRED` | 401 | Request without valid JWT Bearer header |
| `VALIDATION_FAILED` | 400 | Missing mandatory fields (journeyId, hotelName, checkInDate, checkOutDate) |
| `RESOURCE_NOT_FOUND` | 404 | Journey or Hotel booking ID does not exist |
| `ACCESS_DENIED` | 403 | Attempt to link hotel to or modify another user's journey |
| `HOTEL_INVALID_DATES` | 422 | checkOutDate is equal to or before checkInDate |
| `HOTEL_RESCHEDULE_NO_CHANGES` | 400 / 422 | Reschedule request has no new dates or new dates match current dates |

---

## 🧪 Testing Verification

Run full test suite:
```powershell
mvn test
```

Run Phase 8 Hotel Booking integration tests specifically:
```powershell
mvn test -Dtest=HotelBookingIntegrationTest
```

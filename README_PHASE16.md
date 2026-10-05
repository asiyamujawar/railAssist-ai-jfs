# Phase 16 — Travel Coordination Workflow

> **Module status:** Complete  
> **New package:** `com.trainconcierge.coordination`  
> **New files:** 9 source + 1 test  
> **Total project tests:** 190 passing (100% success rate)

---

## Overview

The Travel Coordination Workflow automatically reschedules downstream travel arrangements (hotel check-in dates and cab pickup times) following a successful train rebooking. 

It retrieves the revised arrival time, applies configurable buffer times for station exit and hotel check-in, invokes `MockHotelService` and `MockCabService`, records previous and updated reservation details, and persists a permanent audit record.

---

## Key Features & Design Rules

| Feature | Implementation Details |
|---|---|
| **Dedicated Service** | `TravelCoordinationService` manages end-to-end travel component rescheduling in a single transaction. |
| **Event-Driven Trigger** | `RebookingCompletedEventListener` listens for `RebookingCompletedEvent` and automatically triggers coordination post-commit. |
| **Configurable Buffers** | Uses `coordination.hotel-buffer-minutes` (default 45 min) and `coordination.cab-buffer-minutes` (default 15 min) from `application.properties`. |
| **Stay Duration Preservation** | Preserves existing hotel stay duration (nights) when calculating new check-out dates. |
| **Explicit Status Tracking** | Component status tracked as `SUCCESS`, `NO_RESERVATION`, `FAILED`, or `SKIPPED`. Overall status as `SUCCESS`, `PARTIAL_FAILURE`, `FAILED`, or `NO_ACTION_REQUIRED`. |
| **Partial Failure & Retry** | Isolates failures so a cab failure does not roll back a hotel update, and provides `POST /api/coordination/{id}/retry` for targeted retries. |
| **Duplicate Prevention** | Prevents duplicate rescheduling for the same rebooking event (`INVALID_OPERATION`). |
| **Local Mock Notice** | Always includes notice: *"All hotel and cab reschedules were simulated locally via Mock services. No real external provider was contacted."* |

---

## Architecture & Coordination Workflow

```
[RebookingCompletedEvent]
          │
          ▼
[RebookingCompletedEventListener]
          │
          ▼
[TravelCoordinationService.coordinateTravelForRebooking()]
    ├── 1. Check Duplicate Execution (rejects if already SUCCESS / NO_ACTION_REQUIRED)
    ├── 2. Retrieve Revised Arrival Time (scheduled/actual arrival date + time)
    ├── 3. Reschedule Hotel Stays:
    │      ├── Calculate check-in = revisedArrival + hotelBufferMinutes (45m)
    │      ├── Preserve stay duration (checkOut = checkIn + nights)
    │      └── Invoke MockHotelService via HotelBookingService
    ├── 4. Reschedule Cab Pickups:
    │      ├── Calculate pickup = revisedArrival + cabBufferMinutes (15m)
    │      └── Invoke MockCabService via CabBookingService
    ├── 5. Determine Overall Outcome (SUCCESS / PARTIAL_FAILURE / FAILED / NO_ACTION_REQUIRED)
    └── 6. Save TravelCoordinationRecord Audit Log
```

---

## REST API Specifications

### 1. Trigger Travel Coordination Manually

* **Endpoint:** `POST /api/journeys/{id}/coordination/trigger`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Travel coordination workflow executed successfully.",
  "data": {
    "coordinationRecordId": 88,
    "rebookingHistoryId": 45,
    "journeyId": 10,
    "userId": 2,
    "trainRebookingStatus": "COMPLETED",
    "hotelUpdateStatus": "SUCCESS",
    "cabUpdateStatus": "SUCCESS",
    "overallStatus": "SUCCESS",
    "hotelOldCheckIn": "2026-10-03",
    "hotelNewCheckIn": "2026-10-03",
    "hotelOldCheckOut": "2026-10-05",
    "hotelNewCheckOut": "2026-10-05",
    "cabOldPickupTime": "2026-10-03T12:15:00",
    "cabNewPickupTime": "2026-10-03T18:15:00",
    "failureDetails": null,
    "simulatedNotice": "All hotel and cab reschedules were simulated locally via Mock services. No real external provider was contacted.",
    "retryCount": 0,
    "initiatedAt": "2026-10-02T22:50:00Z",
    "completedAt": "2026-10-02T22:50:01Z"
  }
}
```

---

### 2. Retry Partial or Failed Coordination

* **Endpoint:** `POST /api/coordination/{id}/retry`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Travel coordination retry executed successfully.",
  "data": {
    "coordinationRecordId": 88,
    "overallStatus": "SUCCESS",
    "retryCount": 1
  }
}
```

---

### 3. Get Travel Coordination History

* **Endpoint:** `GET /api/journeys/{id}/coordination`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "coordinationRecordId": 88,
      "rebookingHistoryId": 45,
      "journeyId": 10,
      "overallStatus": "SUCCESS"
    }
  ]
}
```

---

# Running and Testing Steps

### 1. Running Automated Tests

To execute the complete unit and integration test suite (184 passing tests):

```bash
mvn test
```

To run only the Travel Coordination test suite:

```bash
mvn test -Dtest=TravelCoordinationServiceIntegrationTest
```

---

### 2. End-to-End Walkthrough via cURL

#### Step 1: Login to acquire JWT authentication token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "passenger@example.com", "password": "Password123!"}'
```

#### Step 2: Trigger Travel Coordination Manually
```bash
curl -X POST http://localhost:8080/api/journeys/10/coordination/trigger \
  -H "Authorization: Bearer <TOKEN>"
```

#### Step 3: Retry Failed or Partial Coordination (if needed)
```bash
curl -X POST http://localhost:8080/api/coordination/88/retry \
  -H "Authorization: Bearer <TOKEN>"
```

#### Step 4: Retrieve Travel Coordination Audit Logs
```bash
curl -X GET http://localhost:8080/api/journeys/10/coordination \
  -H "Authorization: Bearer <TOKEN>"
```

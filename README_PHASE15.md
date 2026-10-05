# Phase 15 — Simulated Rebooking Service

> **Module status:** Complete  
> **New package:** `com.trainconcierge.rebooking`  
> **New files:** 9 source + 1 test  
> **Total project tests:** 179 passing (100% success rate)

---

## Overview

The Simulated Rebooking Service allows passengers affected by train disruptions to accept an alternative train recommendation and execute an end-to-end simulated rebooking. 

It handles validation, transactional booking creation, atomic seat inventory updates, original booking status preservation (`REBOOKED`), permanent audit trail creation (`RebookingHistory`), disruption status resolution (`RESOLVED`), and event publication for post-rebooking workflows (hotel/cab updates) without coupling domain logic.

---

## Key Features & Design Rules

| Feature | Implementation Details |
|---|---|
| **Dedicated Service** | `RebookingService` manages all rebooking operations in a single transactional boundary (`@Transactional`). |
| **Ownership Validation** | Validates that the disruption event belongs to the authenticated user's journey (or admin). |
| **Recommendation Verification** | Verifies selected recommendation is valid, belongs to the disruption, and suggested train schedule is active/scheduled. |
| **Seat Re-Check & Atomic Inventory** | Rechecks seat availability and performs atomic decrement (`bookSeatsAtomically`) on `SeatAvailability`. |
| **Original Booking Preservation** | Original `Booking` status is set to `REBOOKED` and linked via `replacementBooking` — never deleted. |
| **Simulated Booking Creation** | Creates a new `Booking` entity with `CONFIRMED` status, calculated fare, and generated reference code. |
| **Rebooking History Audit** | Persists a permanent `RebookingHistory` record linking user, disruption, old booking, new booking, and recommendation. |
| **Disruption Resolution** | Marks `DisruptionEvent` status as `RESOLVED`, sets `resolved = true`, and records `resolvedAt`. |
| **Duplicate Prevention** | Rejects rebooking attempts on already resolved or rebooked disruptions (`DISRUPTION_ALREADY_REBOOKED`). |
| **Dual Execution Modes** | Supports user-approved mode (`autoMode = false`) and controlled demo auto-rebooking mode (`autoMode = true`). |
| **Event-Driven Decoupling** | Publishes `RebookingCompletedEvent` via `PostRebookingWorkflowTrigger` for hotel/cab rescheduling instead of modifying them directly inside the transaction. |
| **No IRCTC / Real Payment** | Pure simulation — no real payment gateway or IRCTC integration is involved. |

---

## Architecture & Rebooking Workflow

```
Passenger / System
       │
       │ POST /api/disruptions/{id}/rebook { recommendationId, autoMode }
       ▼
[RebookingController]
       │
       ▼
[RebookingService.rebook()] ──── @Transactional
       │
       ├── 1. Load User & DisruptionEvent
       ├── 2. Verify User ownership of DisruptionEvent / Journey
       ├── 3. Check for Duplicate Rebooking (isResolved / RebookingHistory exists)
       ├── 4. Load & Validate selected Recommendation (active schedule, non-cancelled)
       ├── 5. Recheck Seat Availability on target alternative schedule
       ├── 6. Execute Atomic Seat Deduction (bookSeatsAtomically)
       ├── 7. Create & Persist New Simulated Booking (CONFIRMED status)
       ├── 8. Mark Original Booking status = REBOOKED & set replacementBooking link
       ├── 9. Update DisruptionEvent status = RESOLVED & Journey status = REBOOKED
       ├── 10. Persist RebookingHistory audit record (autonomous = autoMode)
       │
       └── 11. Publish RebookingCompletedEvent (PostRebookingWorkflowTrigger)
                 └──> Downstream Listeners (Hotel/Cab rescheduling, Notifications)
```

---

## REST API Specifications

### 1. Execute Simulated Rebooking

* **Endpoint:** `POST /api/disruptions/{id}/rebook`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Request Body:**
```json
{
  "recommendationId": 12,
  "autoMode": false
}
```
* **Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Passenger rebooking onto alternative train confirmed successfully.",
  "data": {
    "rebookingHistoryId": 45,
    "disruptionEventId": 3,
    "journeyId": 10,
    "originalBookingId": 101,
    "originalBookingReference": "TC-20261001-0001",
    "originalTrainNumber": "TR-100",
    "originalTrainName": "Disrupted Express",
    "originalJourneyDate": "2026-10-03",
    "originalDeparture": "08:00:00",
    "originalArrival": "12:00:00",
    "originalFare": 100.00,
    "originalBookingStatus": "REBOOKED",
    "newBookingId": 102,
    "newBookingReference": "TC-20261002-004A",
    "newTrainNumber": "TR-200",
    "newTrainName": "Alternative Express",
    "newJourneyDate": "2026-10-03",
    "newDeparture": "09:00:00",
    "newArrival": "13:00:00",
    "newFare": 110.00,
    "newBookingStatus": "CONFIRMED",
    "numberOfSeats": 1,
    "seatClass": "SECOND",
    "currency": "GBP",
    "fareDifference": 10.00,
    "rebookingStatus": "COMPLETED",
    "autonomous": false,
    "initiatedAt": "2026-10-02T22:30:00Z",
    "completedAt": "2026-10-02T22:30:00Z"
  },
  "timestamp": "2026-10-02T22:30:00Z"
}
```

---

### 2. Get Journey Rebooking History

* **Endpoint:** `GET /api/journeys/{id}/rebooking-history`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": 45,
      "disruptionEventId": 3,
      "journeyId": 10,
      "originalBookingId": 101,
      "originalBookingReference": "TC-20261001-0001",
      "newBookingId": 102,
      "newBookingReference": "TC-20261002-004A",
      "recommendationId": 12,
      "status": "COMPLETED",
      "autonomous": false,
      "fareDifference": 10.00,
      "failureReason": null,
      "initiatedAt": "2026-10-02T22:30:00Z",
      "completedAt": "2026-10-02T22:30:00Z",
      "createdAt": "2026-10-02T22:30:00Z"
    }
  ]
}
```

---

## Verification & Testing Guide

### 1. Running Automated Tests

Run the full suite including `RebookingServiceIntegrationTest`:

```bash
mvn test
```

### 2. Manual End-to-End Walkthrough via cURL

#### Step 1: Login to acquire JWT token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "passenger@example.com", "password": "Password123!"}'
```

#### Step 2: Get Disruption Recommendations
```bash
curl -X GET http://localhost:8080/api/disruptions/3/recommendations \
  -H "Authorization: Bearer <TOKEN>"
```

#### Step 3: Execute Simulated Rebooking
```bash
curl -X POST http://localhost:8080/api/disruptions/3/rebook \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"recommendationId": 12, "autoMode": false}'
```

#### Step 4: Verify Rebooking History Audit
```bash
curl -X GET http://localhost:8080/api/journeys/10/rebooking-history \
  -H "Authorization: Bearer <TOKEN>"
```

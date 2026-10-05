# Phase 10 — Mock Train Status Simulation

> **SIMULATED SERVICE** — No real railway data provider (NTES, National Rail, Amtrak, etc.) is contacted at any point. All status transitions are driven by explicit admin API calls and stored in the database for auditability.

---

## Overview

Phase 10 implements a controlled, admin-driven train status simulation engine for the TrainConcierge platform. The module is intentionally isolated from the recommendation and monitoring logic and is designed so that a **real railway data adapter can replace it in the future without rewriting the disruption engine**.

| Capability | Detail |
|---|---|
| Status types | ON_TIME, DELAYED, CANCELLED, PLATFORM_CHANGED |
| Status changes | Admin-only (never autonomous or random) |
| History | Append-only; every change is persisted |
| Integration | Accessible to disruption engine via `TrainStatusPort` interface |

---

## Architecture

```
TrainStatusPort (interface)
        │
        └── MockTrainStatusService    ← current implementation
                ├── reads:  TrainScheduleRepository
                │           TrainStatusHistoryRepository
                └── writes: TrainStatusHistory  (append-only)
                            TrainSchedule        (live fields synced)

REST Layer
    TrainSimulationController    /api/simulation/trains/**  (authenticated users)
    AdminTrainSimulationController /api/admin/simulation/trains/**  (ROLE_ADMIN only)
```

**Adapter contract:** When a real railway data provider (e.g. NTES live feed) is available, implement `TrainStatusPort`, register it as a `@Primary` Spring bean, and delete or disable `MockTrainStatusService`. The disruption engine and all tests remain unchanged.

---

## Files Created / Modified

### New files

| File | Purpose |
|---|---|
| `simulation/TrainStatusPort.java` | Port interface — the only dependency the disruption engine takes |
| `simulation/MockTrainStatusService.java` | Simulated implementation of `TrainStatusPort` |
| `simulation/TrainSimulationController.java` | Public read-only endpoints (`/api/simulation/trains/**`) |
| `simulation/AdminTrainSimulationController.java` | Admin write endpoint (`/api/admin/simulation/trains/**`) |
| `simulation/UpdateSimulatedStatusRequest.java` | Request DTO for admin status update |
| `simulation/SimulatedTrainStatusResponse.java` | Response DTO — always includes `source` and `disclaimer` fields |
| `simulation/SimulatedStatusHistoryEntry.java` | DTO for a single history entry |
| `simulation/TrainStatusSimulationIntegrationTest.java` | 18-test integration test class |

### Modified files

| File | Change |
|---|---|
| `train/TrainStatus.java` | Added `PLATFORM_CHANGED` enum value |
| `train/TrainStatusHistory.java` | Added `platform` (String) and `simulated` (boolean) fields |
| `exception/ErrorCode.java` | Added `SIMULATION_INVALID_DELAY`, `SIMULATION_INVALID_STATUS` codes |

---

## API Reference

All responses are wrapped in the standard `ApiResponse<T>` envelope.

### GET `/api/simulation/trains/{scheduleId}/status`

Returns the latest simulated status for a scheduled train run.

**Authentication:** Bearer JWT (any authenticated user)

**Response `data` fields:**

| Field | Type | Description |
|---|---|---|
| `source` | String | Always `"MOCK_TRAIN_STATUS_SERVICE"` |
| `disclaimer` | String | Always states no real provider was contacted |
| `scheduleId` | Long | The queried schedule |
| `trainNumber` | String | e.g. `"12301"` |
| `trainName` | String | e.g. `"Rajdhani Express"` |
| `scheduledDate` | LocalDate | |
| `scheduledDeparture` | LocalTime | |
| `scheduledArrival` | LocalTime | |
| `status` | TrainStatus | Current simulated status |
| `delayMinutes` | Integer | 0 when ON_TIME or PLATFORM_CHANGED |
| `platform` | String | Null if not set |
| `message` | String | Operator note from last update |
| `lastUpdatedAt` | Instant | Timestamp of last status change |
| `hasHistory` | boolean | True when at least one history entry exists |

**Example response:**

```json
{
  "success": true,
  "message": "[SIMULATED] Latest train status retrieved. No real railway provider was contacted.",
  "data": {
    "source": "MOCK_TRAIN_STATUS_SERVICE",
    "disclaimer": "This status is simulated. No real railway data provider has been contacted.",
    "scheduleId": 42,
    "trainNumber": "12301",
    "trainName": "Rajdhani Express",
    "status": "DELAYED",
    "delayMinutes": 45,
    "platform": "5",
    "message": "Engine delay at origin.",
    "lastUpdatedAt": "2026-10-02T10:15:00Z",
    "hasHistory": true
  },
  "timestamp": "2026-10-02T10:20:00Z"
}
```

---

### GET `/api/simulation/trains/{scheduleId}/history`

Returns all status history entries for a schedule, **newest first**.

**Authentication:** Bearer JWT (any authenticated user)

**Example response:**

```json
{
  "success": true,
  "message": "[SIMULATED] Train status history retrieved. No real railway provider was contacted.",
  "data": [
    {
      "id": 3,
      "status": "ON_TIME",
      "delayMinutes": 0,
      "platform": null,
      "message": "Train recovered.",
      "recordedAt": "2026-10-02T11:00:00Z",
      "simulated": true
    },
    {
      "id": 2,
      "status": "PLATFORM_CHANGED",
      "delayMinutes": 0,
      "platform": "7A",
      "message": "Platform reassigned by station authority.",
      "recordedAt": "2026-10-02T10:30:00Z",
      "simulated": true
    },
    {
      "id": 1,
      "status": "DELAYED",
      "delayMinutes": 45,
      "message": "Engine delay at origin.",
      "recordedAt": "2026-10-02T10:15:00Z",
      "simulated": true
    }
  ],
  "timestamp": "2026-10-02T11:05:00Z"
}
```

---

### PUT `/api/admin/simulation/trains/{scheduleId}/status`

Admin-controlled status update. **This is the only way statuses change — no automatic or random updates occur.**

**Authentication:** Bearer JWT with `ROLE_ADMIN`

**Request body:**

```json
{
  "status": "DELAYED",
  "delayMinutes": 45,
  "platform": null,
  "message": "Engine delay at origin."
}
```

**Validation rules:**

| Status | `delayMinutes` | `platform` |
|---|---|---|
| `ON_TIME` | Optional (ignored) | Optional |
| `DELAYED` | **Required, ≥ 1** | Optional |
| `CANCELLED` | Optional (ignored) | Optional |
| `PLATFORM_CHANGED` | Optional (set to 0) | **Required (non-blank)** |

**Error codes:**

| HTTP | ErrorCode | Condition |
|---|---|---|
| 400 | `SIMULATION_INVALID_DELAY` | `DELAYED` without `delayMinutes >= 1` |
| 400 | `SIMULATION_INVALID_STATUS` | `PLATFORM_CHANGED` without `platform` |
| 404 | `RESOURCE_NOT_FOUND` | `scheduleId` does not exist |
| 403 | `ACCESS_DENIED` | Caller is not `ROLE_ADMIN` |

---

## Status Transition Rules

```
(any state)
    │
    ├─ PUT status=DELAYED, delayMinutes=N     → DELAYED  (N ≥ 1)
    ├─ PUT status=ON_TIME                     → ON_TIME  (delay reset to 0)
    ├─ PUT status=CANCELLED                   → CANCELLED (isCancelled=true on schedule)
    └─ PUT status=PLATFORM_CHANGED, platform=X → PLATFORM_CHANGED (delay unchanged)
```

Every transition appends a row to `train_status_history` and updates the live `train_schedules` row. **Rows in `train_status_history` are never updated or deleted.**

---

## Running the Application

```bash
# From project root
mvn spring-boot:run

# Or with a specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## How to Test This Phase

### 1. Automated Integration Tests

Run only the Phase 10 simulation tests:

```bash
mvn test -Dtest=TrainStatusSimulationIntegrationTest
```

Run the full test suite (all phases — 122 tests):

```bash
mvn test
```

Expected output:
```
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0  ← Phase 10
[INFO] Tests run: 122, Failures: 0, Errors: 0, Skipped: 0 ← All phases
[INFO] BUILD SUCCESS
```

---

### 2. Manual Testing with cURL

Start the application first:

```bash
mvn spring-boot:run
```

#### Step 1 — Register an admin user

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Admin",
    "lastName": "User",
    "email": "admin@trainconcierge.com",
    "password": "Admin@2026!",
    "phoneNumber": "+91-90000-00001",
    "role": "ROLE_ADMIN"
  }' | jq .
```

#### Step 2 — Login and capture the token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@trainconcierge.com",
    "password": "Admin@2026!"
  }' | jq -r '.data.token')

echo "Token: $TOKEN"
```

> **Windows (PowerShell):**
> ```powershell
> $response = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
>   -Method POST -ContentType "application/json" `
>   -Body '{"email":"admin@trainconcierge.com","password":"Admin@2026!"}'
> $TOKEN = $response.data.token
> ```

#### Step 3 — Create a Train and Schedule (prerequisites)

Create a train:
```bash
curl -s -X POST http://localhost:8080/api/admin/trains \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "trainNumber": "12301",
    "trainName": "Rajdhani Express",
    "originStation": "New Delhi",
    "destinationStation": "Mumbai Central",
    "totalSeats": 500
  }' | jq .
```

Note the `id` from the response, then create a schedule:
```bash
# Replace TRAIN_ID with the id returned above
curl -s -X POST http://localhost:8080/api/admin/schedules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "trainId": TRAIN_ID,
    "scheduledDate": "2026-12-15",
    "scheduledDeparture": "08:00",
    "scheduledArrival": "20:00",
    "platform": "5",
    "baseFare": 1500.00
  }' | jq .
```

Note the schedule `id` — referred to as `SCHEDULE_ID` below.

---

#### Step 4 — Check Default Status (no history yet)

```bash
curl -s http://localhost:8080/api/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Expected response:**
```json
{
  "success": true,
  "message": "[SIMULATED] Latest train status retrieved. No real railway provider was contacted.",
  "data": {
    "source": "MOCK_TRAIN_STATUS_SERVICE",
    "disclaimer": "This status is simulated. No real railway data provider has been contacted.",
    "scheduleId": 1,
    "trainNumber": "12301",
    "trainName": "Rajdhani Express",
    "status": "SCHEDULED",
    "delayMinutes": 0,
    "platform": "5",
    "hasHistory": false
  }
}
```

---

#### Step 5 — Set Status to DELAYED

```bash
curl -s -X PUT http://localhost:8080/api/admin/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "DELAYED",
    "delayMinutes": 45,
    "message": "Engine failure at origin station."
  }' | jq .
```

**Expected:** `status=DELAYED`, `delayMinutes=45`, `hasHistory=true`

---

#### Step 6 — Set Status to PLATFORM_CHANGED

```bash
curl -s -X PUT http://localhost:8080/api/admin/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "PLATFORM_CHANGED",
    "platform": "7A",
    "message": "Platform reassigned by station authority."
  }' | jq .
```

**Expected:** `status=PLATFORM_CHANGED`, `platform=7A`, `delayMinutes=0`

---

#### Step 7 — Set Status to CANCELLED

```bash
curl -s -X PUT http://localhost:8080/api/admin/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "CANCELLED",
    "message": "Service suspended due to track maintenance."
  }' | jq .
```

**Expected:** `status=CANCELLED`, `delayMinutes=0`

---

#### Step 8 — Restore to ON_TIME

```bash
curl -s -X PUT http://localhost:8080/api/admin/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "ON_TIME",
    "message": "Service resumed."
  }' | jq .
```

**Expected:** `status=ON_TIME`, `delayMinutes=0`

---

#### Step 9 — View Full Status History

```bash
curl -s http://localhost:8080/api/simulation/trains/SCHEDULE_ID/history \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Expected:** 4 entries returned newest-first — `ON_TIME → CANCELLED → PLATFORM_CHANGED → DELAYED`. Each entry has `"simulated": true`.

---

### 3. Validation Error Testing

#### DELAYED without delayMinutes → 400

```bash
curl -s -X PUT http://localhost:8080/api/admin/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "DELAYED"}' | jq .
```

#### DELAYED with delayMinutes = 0 → 400

```bash
curl -s -X PUT http://localhost:8080/api/admin/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "DELAYED", "delayMinutes": 0}' | jq .
```

#### PLATFORM_CHANGED without platform → 400

```bash
curl -s -X PUT http://localhost:8080/api/admin/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "PLATFORM_CHANGED"}' | jq .
```

---

### 4. Security Testing

#### Non-admin user gets 403 on admin endpoint

```bash
# Register a regular user first and get their token
USER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"User@2026!"}' | jq -r '.data.token')

curl -s -X PUT http://localhost:8080/api/admin/simulation/trains/SCHEDULE_ID/status \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "DELAYED", "delayMinutes": 10}' | jq .
# Expected: 403 Forbidden
```

#### No token gets 401

```bash
curl -s http://localhost:8080/api/simulation/trains/SCHEDULE_ID/status | jq .
# Expected: 401 Unauthorized
```

---

### 5. Invalid Schedule ID → 404

```bash
curl -s http://localhost:8080/api/simulation/trains/99999999/status \
  -H "Authorization: Bearer $TOKEN" | jq .
# Expected: 404 Not Found
```

---

## Test Coverage Summary

| # | Test | Expected |
|---|---|---|
| 1 | GET status with no history | 200, hasHistory=false, delayMinutes=0 |
| 2 | Admin sets DELAYED | 200, status=DELAYED, delay persisted in history |
| 3 | Admin sets CANCELLED | 200, schedule.isCancelled=true |
| 4 | Admin sets PLATFORM_CHANGED | 200, platform value in response and history |
| 5 | Admin sets ON_TIME after DELAYED | 200, delay=0, 2 history entries |
| 6 | DELAYED without delayMinutes | 400 |
| 7 | DELAYED with delayMinutes=0 | 400 |
| 8 | PLATFORM_CHANGED without platform | 400 |
| 9 | History newest-first after 3 updates | 200, correct ordering |
| 10 | Non-admin user calls admin PUT | 403 |
| 11 | Unauthenticated GET status | 401 |
| 12 | Unauthenticated admin PUT | 401 |
| 13 | GET status invalid scheduleId | 404 |
| 14 | GET history invalid scheduleId | 404 |
| 15 | Admin PUT invalid scheduleId | 404 |
| 16 | Source/disclaimer always present | 200, specific string values |
| 17 | simulated=true in all history entries | 200, flag verified |
| 18 | Missing status field in body | 400 |

---

## Design Decisions

### No random status changes
Status is **never changed autonomously**. The only mutation path is `PUT /api/admin/simulation/trains/{scheduleId}/status`.

### Append-only history
`train_status_history` rows are never updated or deleted. Each call to `updateStatus()` inserts a new row, making the full status timeline queryable and auditable.

### Port/Adapter pattern
`TrainStatusPort` is the interface consumed by the disruption engine (future phase). `MockTrainStatusService` is the only implementation. Swapping to a real NTES adapter requires:
1. Implement `TrainStatusPort`
2. Annotate it `@Primary`
3. No changes to the disruption engine, controllers, or tests

### Explicit simulated labelling
Every API response includes:
- `"source": "MOCK_TRAIN_STATUS_SERVICE"`
- `"disclaimer": "This status is simulated. No real railway data provider has been contacted."`

Every history row has `simulated = true`.

---

## Next Phase

**Phase 11 — Disruption Engine**

The disruption engine will monitor `TrainStatusPort.getLatestStatus()` and, when a disruption is detected (DELAYED, CANCELLED, PLATFORM_CHANGED), automatically trigger:
- Hotel rescheduling via `HotelProviderAdapter`
- Cab rescheduling via `CabProviderAdapter`
- Passenger notification

Because all three underlying services are already abstracted behind port interfaces, the disruption engine will require zero changes to hotel, cab, or train simulation code.

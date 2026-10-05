# Phase 19 — Administrative & Simulation Control APIs

> **SIMULATED SERVICE** — All train-status changes are stored in the database but no real railway provider is ever contacted.

---

## 1. Overview

Phase 19 extends TrainConcierge with a dedicated admin simulation control layer. It allows authorised administrators to:

| Goal | Endpoint |
|---|---|
| Create / update train master data | Existing `POST /api/admin/trains`, `PUT /api/admin/trains/{id}` |
| Create schedules | Existing `POST /api/admin/schedules` |
| Modify simulated seat availability | Existing `PATCH /api/admin/schedules/{id}/availability` |
| **Trigger a train delay** | `POST /api/admin/simulation/control/trigger-delay` |
| **Trigger a train cancellation** | `POST /api/admin/simulation/control/trigger-cancellation` |
| **Restore normal train status** | `POST /api/admin/simulation/control/restore-normal` |
| **Trigger a monitoring cycle** | `POST /api/admin/simulation/control/monitoring-cycle` |
| **Clear monitoring cache** | `DELETE /api/admin/simulation/control/monitoring-cache` |
| **Trigger disruption evaluation for a journey** | `POST /api/admin/simulation/control/disruption-evaluation` |

---

## 2. Security Model

All endpoints in this phase fall under `/api/admin/**` and are subject to **two independent security checks**:

1. **URL-level rule** in `SecurityConfig.java`:
   ```
   .requestMatchers("/api/admin/**").hasRole("ADMIN")
   ```
2. **Method-level annotation** on the controller class:
   ```java
   @PreAuthorize("hasRole('ADMIN')")
   ```

- **Unauthenticated requests** → `401 Unauthorized`
- **Authenticated non-admin requests** → `403 Forbidden`

> **Design constraint:** There is no public bypass endpoint. All admin actions require a valid JWT belonging to a `ROLE_ADMIN` user.

---

## 3. New Endpoints (Phase 19)

All new endpoints are under:
```
/api/admin/simulation/control
```

### 3.1 Trigger Delay

```http
POST /api/admin/simulation/control/trigger-delay
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "scheduleId": 1,
  "delayMinutes": 90,
  "reason": "Engine issue at origin station."
}
```

**Validation:** `scheduleId` required; `delayMinutes` required, must be >= 1.

**Response:** `200 OK` — updated `SimulatedTrainStatusResponse` with `status: "DELAYED"`.

**Side effects:**
- Appends row to `train_status_history` with `simulated = true`.
- Updates `train_schedules.schedule_status` and `delay_minutes`.
- Does **not** automatically trigger disruption detection.

---

### 3.2 Trigger Cancellation

```http
POST /api/admin/simulation/control/trigger-cancellation
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "scheduleId": 1,
  "reason": "Strike action — services suspended."
}
```

**Response:** `200 OK` — `status: "CANCELLED"`. The `TrainSchedule` row is marked `cancelled = true`.

---

### 3.3 Restore Normal Status

```http
POST /api/admin/simulation/control/restore-normal
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "scheduleId": 1,
  "reason": "All clear — services resumed."
}
```

**Response:** `200 OK` — `status: "ON_TIME"`, `delayMinutes: 0`. Use between Postman demo iterations to reset state.

---

### 3.4 Trigger Monitoring Cycle

```http
POST /api/admin/simulation/control/monitoring-cycle
Authorization: Bearer {adminToken}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "cycleStartedAt": "2026-10-03T09:00:00Z",
    "cycleCompletedAt": "2026-10-03T09:00:00.123Z",
    "journeysEvaluated": 1,
    "schedulesPolled": 1,
    "changesDetected": 1,
    "summary": "Monitoring cycle completed: 1 journey(s) evaluated, ..."
  }
}
```

**Idempotency:** Calling this twice without a status change between calls yields `changesDetected: 0` on the second call.

---

### 3.5 Clear Monitoring Cache

```http
DELETE /api/admin/simulation/control/monitoring-cache
Authorization: Bearer {adminToken}
```

Clears the in-memory `lastKnownStatus` map in `TrainMonitoringScheduler`. After clearing, the next cycle will treat all schedules as "first observation".

Use this at the **start of each Postman demo run** for a clean baseline.

---

### 3.6 Trigger Disruption Evaluation

```http
POST /api/admin/simulation/control/disruption-evaluation
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "journeyId": 5
}
```

**Response (event created):**
```json
{
  "data": {
    "journeyId": 5,
    "scheduleId": 1,
    "skipped": false,
    "disruptionEventId": 12,
    "disruptionType": "DELAY",
    "severity": "HIGH",
    "disruptionStatus": "DETECTED",
    "recommendationTriggered": true,
    "evaluatedAt": "2026-10-03T09:01:00Z",
    "summary": "Disruption evaluation complete — DisruptionEvent id=12 ..."
  }
}
```

**Response (skipped — duplicate guard):**
```json
{
  "data": {
    "journeyId": 5,
    "skipped": true,
    "skipReason": "Journey id=5 already has 1 open disruption event(s) — skipping to prevent duplicate rebooking.",
    "disruptionEventId": 12
  }
}
```

---

## 4. Duplicate Disruption Guard

A key safety constraint: the disruption evaluation endpoint **cannot create duplicate events**. This prevents:
- Double rebooking of hotel / cab
- Duplicate disruption alert notifications
- Infinite recommendation loops

**Guard layers applied in order:**
1. Journey is in terminal state (`COMPLETED`, `CANCELLED`) → skipped
2. No confirmed bookings for the journey → skipped
3. An open (non-resolved, non-failed) disruption event already exists → skipped
4. Train status is `ON_TIME` → no event created (not disruptive)

Both `AdminSimulationControlService` and `DisruptionDetectionService` independently check these conditions.

---

## 5. Audit Logging

Every admin simulation action is logged with the `[ADMIN-SIM]` prefix:

```
INFO [ADMIN-SIM] Triggering DELAY — scheduleId=1 delayMinutes=90 reason=Engine issue
INFO [ADMIN-SIM] DELAY applied — scheduleId=1 status=DELAYED delayMinutes=90
INFO [ADMIN-SIM] Manual monitoring cycle TRIGGERED.
INFO [ADMIN-SIM] Disruption evaluation TRIGGERED for journeyId=5
```

The existing `TrainStatusHistory` table records every simulated status change with `simulated = true`, providing a full audit trail without additional schema changes.

---

## 6. Files Created in Phase 19

### Production code

| File | Purpose |
|---|---|
| `admin/simulation/AdminSimulationControlController.java` | REST controller — 6 endpoints |
| `admin/simulation/AdminSimulationControlService.java` | Core service with duplicate guards |
| `admin/simulation/dto/TriggerDelayRequest.java` | Request DTO |
| `admin/simulation/dto/TriggerCancellationRequest.java` | Request DTO |
| `admin/simulation/dto/RestoreNormalStatusRequest.java` | Request DTO |
| `admin/simulation/dto/TriggerDisruptionEvaluationRequest.java` | Request DTO |
| `admin/simulation/dto/MonitoringCycleTriggerResponse.java` | Response DTO |
| `admin/simulation/dto/DisruptionEvaluationResponse.java` | Response DTO |

### Test code

| File | Coverage |
|---|---|
| `test/.../AdminSimulationControlIntegrationTest.java` | 24 tests: RBAC, happy-path, validation, duplicate guard, edge cases |

---

## 7. Running the Tests

```bash
# Run Phase 19 tests only
mvn test -Dtest=AdminSimulationControlIntegrationTest

# Run all tests (Phase 1-19)
mvn test
```

### Test coverage matrix

| # | Test | What is verified |
|---|---|---|
| 1-4 | `*_Unauthenticated_Returns401` | All 4 key endpoints reject requests without JWT |
| 5-9 | `*_NonAdminUser_Returns403` | All 5 endpoints reject ROLE_USER tokens |
| 10 | `triggerDelay_Admin_Returns200_WithDelayPersisted` | 200, status=DELAYED, delayMinutes correct |
| 11 | `triggerDelay_Admin_MissingScheduleId_Returns400` | Validation: scheduleId required |
| 12 | `triggerDelay_Admin_ZeroDelayMinutes_Returns400` | Validation: delayMinutes >= 1 |
| 13 | `triggerDelay_Admin_InvalidScheduleId_Returns404` | 404 on non-existent schedule |
| 14 | `triggerCancellation_Admin_Returns200_ScheduleMarkedCancelled` | DB row flagged cancelled |
| 15 | `restoreNormal_AfterDelay_Returns200_StatusOnTime` | Status reset to ON_TIME, delay cleared |
| 16 | `triggerMonitoringCycle_Admin_Returns200_WithCycleStats` | Cycle stats present in response |
| 17 | `triggerMonitoringCycle_Twice_NoStatusChange_DetectsZeroChanges` | Idempotency of monitoring cycle |
| 18 | `clearMonitoringCache_Admin_Returns200_WithConfirmation` | Cache cleared confirmation |
| 19 | `disruptionEvaluation_OnTimeSchedule_ReturnsSkippedWithNoEvent` | ON_TIME does not create disruption |
| 20 | `disruptionEvaluation_AfterDelay_CreatesDisruptionEvent` | Disruption event created for DELAY |
| 21 | `disruptionEvaluation_CalledTwice_SecondCallSkippedByDuplicateGuard` | DB has exactly 1 disruption event |
| 22 | `disruptionEvaluation_CancelledJourney_IsSkipped` | Terminal journey skipped |
| 23 | `disruptionEvaluation_UnknownJourneyId_Returns404` | 404 on non-existent journey |
| 24 | `disruptionEvaluation_NoConfirmedBookings_IsSkipped` | No confirmed bookings skipped |

---

## 8. Repeatable Postman Demo Scenario

> Execute these steps in order. Each step builds on the previous. Use Postman environment variables.

**Required Postman variables:** `{{adminToken}}`, `{{userToken}}`, `{{scheduleId}}`, `{{journeyId}}`

---

### Step 1 — Obtain Admin JWT

```
POST /api/auth/login
{ "email": "admin@example.com", "password": "Admin@2026!" }
```
Copy JWT → `{{adminToken}}`

---

### Step 2 — Verify schedule is ON_TIME

```
GET /api/simulation/trains/{{scheduleId}}/status
Authorization: Bearer {{userToken}}
```
Expected: `data.status = "SCHEDULED"` (no history yet)

---

### Step 3 — Clear monitoring cache (clean baseline)

```
DELETE /api/admin/simulation/control/monitoring-cache
Authorization: Bearer {{adminToken}}
```
Expected: `200 OK` with cache cleared confirmation

---

### Step 4 — First monitoring cycle (seeds cache, no disruptions)

```
POST /api/admin/simulation/control/monitoring-cycle
Authorization: Bearer {{adminToken}}
```
Expected: `data.changesDetected = 0` (first observation only)

---

### Step 5 — Trigger a significant delay

```
POST /api/admin/simulation/control/trigger-delay
Authorization: Bearer {{adminToken}}

{
  "scheduleId": {{scheduleId}},
  "delayMinutes": 120,
  "reason": "Signal failure at junction — all services halted."
}
```
Expected: `data.status = "DELAYED"`, `data.delayMinutes = 120`

---

### Step 6A — Option A: Trigger monitoring cycle to detect change

```
POST /api/admin/simulation/control/monitoring-cycle
Authorization: Bearer {{adminToken}}
```
Expected: `data.changesDetected = 1` — disruption detection pipeline triggered

---

### Step 6B — Option B: Force disruption evaluation directly

```
POST /api/admin/simulation/control/disruption-evaluation
Authorization: Bearer {{adminToken}}

{ "journeyId": {{journeyId}} }
```
Expected: `data.skipped = false`, `data.disruptionEventId` set, `data.recommendationTriggered = true`

---

### Step 7 — Verify disruption event (passenger view)

```
GET /api/disruptions
Authorization: Bearer {{userToken}}
```
Expected: List includes a `DELAY` disruption event for the journey

---

### Step 8 — Call disruption evaluation again (duplicate guard check)

```
POST /api/admin/simulation/control/disruption-evaluation
Authorization: Bearer {{adminToken}}

{ "journeyId": {{journeyId}} }
```
Expected: `data.skipped = true`, skipReason mentions "open disruption event(s)"

---

### Step 9 — Restore to ON_TIME (end of demo)

```
POST /api/admin/simulation/control/restore-normal
Authorization: Bearer {{adminToken}}

{ "scheduleId": {{scheduleId}}, "reason": "Signal issue resolved." }
```
Expected: `data.status = "ON_TIME"`, `data.delayMinutes = 0`

---

### Step 10 — Reset for next run

```
DELETE /api/admin/simulation/control/monitoring-cache
Authorization: Bearer {{adminToken}}
```

---

## 9. Design Notes

- **No new DB schema**: Phase 19 reuses `TrainStatusHistory` (`simulated = true`) and the monitoring in-memory cache.
- **Simulation transparency**: Every simulated response includes `[SIMULATED]` in the `message` field.
- **Thread safety**: Monitoring cycle uses `fixedDelay` (no overlap). The admin trigger runs synchronously.
- **No random state changes**: `MockTrainStatusService` never changes status autonomously — all changes are admin-driven.
- **Relation to Phase 10**: Phase 10 introduced the base simulation infrastructure; Phase 19 adds higher-level control endpoints on top of it.

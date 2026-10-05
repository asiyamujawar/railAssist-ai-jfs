# Phase 13 — Disruption Detection Engine

> **Module status:** Complete  
> **New package:** `com.trainconcierge.disruption`  
> **New files:** 6 source + 1 test  
> **New tests:** 11 (Total project tests: 165)  

---

## Overview

The Disruption Detection Engine is the core decision-making module responsible for evaluating train status changes detected by the monitoring scheduler, assessing passenger impact, creating linked `DisruptionEvent` records per affected journey, managing disruption lifecycle states, and triggering recommendation workflows for eligible disruptions.

### Key Features & Design Rules

| Feature | Design Implementation |
|---|---|
| **Dedicated Service** | `DisruptionDetectionService` handles event creation, journey linking, threshold evaluation, and state transitions. |
| **Configurable Rules** | `DisruptionDetectionProperties` manages configurable thresholds (`min-delay-minutes=15`, `medium-delay-minutes=15`, `high-delay-minutes=60`, recommendation-eligible types). |
| **Platform Changes** | Platform change status updates are captured as **informational events** (`DisruptionType.PLATFORM_CHANGE`, `LOW` severity) without marking journeys `DISRUPTED` or triggering recommendations. |
| **Journey Linking** | Each affected `Journey` with a confirmed booking receives a linked `DisruptionEvent`. If no active bookings exist, a schedule-level event is stored. |
| **Duplicate Prevention** | Suppresses duplicate open disruption event creation for the same journey/schedule condition when an event is in `DETECTED` or `PROCESSING` state. |
| **Lifecycle States** | Supports `DisruptionStatus` state transitions: `DETECTED` → `PROCESSING` → `RESOLVED` / `FAILED`. |
| **Decoupled Workflow Trigger** | `RecommendationWorkflowTrigger` publishes `DisruptionDetectedEvent` for eligible disruptions without performing booking, hotel, or cab updates inside detection. |
| **User Access Control** | REST endpoints strictly restrict passenger access to their own journey disruptions (or Admin). |

---

## Architecture

```
[TrainMonitoringScheduler]
         │
         ▼
[DisruptionDetectionService]
   ├── 1. Check Platform Change → Log informational event (no journey disruption)
   ├── 2. Evaluate Configurable Delay Thresholds (<15m ignored, 15-60m MEDIUM, >60m HIGH, CANCELLED CRITICAL)
   ├── 3. Query Confirmed Bookings → Extract affected Journeys
   ├── 4. Duplicate Guard → Skip if open event exists for Journey/Schedule
   ├── 5. Persist DisruptionEvent (Status = DETECTED, linked to Journey & Schedule)
   ├── 6. Mark Journey status = DISRUPTED
   └── 7. Trigger Recommendation Workflow (Eligible types only)
         │
         ▼
[RecommendationWorkflowTrigger] ──► Publishes [DisruptionDetectedEvent]
                                      (Handled by future Recommendation Engine)
```

---

## Configuration Reference

```properties
# application.properties

# ============================================================
# Disruption Detection Engine Rules
# ============================================================
# Delays below this threshold in minutes are ignored as non-disruptive
disruption.detection.min-delay-minutes=${DISRUPTION_MIN_DELAY_MINUTES:15}

# Delay thresholds for severity assignment
disruption.detection.medium-delay-minutes=${DISRUPTION_MEDIUM_DELAY_MINUTES:15}
disruption.detection.high-delay-minutes=${DISRUPTION_HIGH_DELAY_MINUTES:60}
```

---

## Status & Severity Rules Matrix

| TrainStatus | DisruptionType | Delay | Severity | Journey Status | Recommendation Triggered |
|---|---|---|---|---|---|
| `CANCELLED` | `CANCELLATION` | N/A | `CRITICAL` | `DISRUPTED` | ✅ Yes |
| `DIVERTED` | `DIVERSION` | N/A | `CRITICAL` | `DISRUPTED` | ✅ Yes |
| `DELAYED` | `DELAY` | `< 15 min` | `LOW` | `PLANNED` (Unchanged) | ❌ No (Ignored) |
| `DELAYED` | `DELAY` | `15–59 min` | `MEDIUM` | `DISRUPTED` | ✅ Yes |
| `DELAYED` | `DELAY` | `≥ 60 min` | `HIGH` | `DISRUPTED` | ✅ Yes |
| `PLATFORM_CHANGED` | `PLATFORM_CHANGE` | 0 | `LOW` | `PLANNED` (Unchanged) | ❌ No (Informational) |

---

## Disruption Event Lifecycle States

```
 [DETECTED] ──────► [PROCESSING] ──────► [RESOLVED]
      │
      └─────────────► [FAILED]
```

- **`DETECTED`**: Newly created disruption event awaiting processing.
- **`PROCESSING`**: Recommendation engine or passenger is actively processing options.
- **`RESOLVED`**: Rebooking accepted or train service recovered (`resolved=true`, `resolvedAt` set).
- **`FAILED`**: Rebooking attempt failed or manually marked for operator intervention.

---

## REST API Reference

### 1. Retrieve Authenticated User's Disruptions

```http
GET /api/disruptions/my
Authorization: Bearer <user_token>
```

#### Response `200 OK`:
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": [
    {
      "id": 1,
      "scheduleId": 10,
      "journeyId": 4,
      "trainNumber": "TR100",
      "originStation": "London Euston",
      "destinationStation": "Manchester Piccadilly",
      "type": "CANCELLATION",
      "severity": "CRITICAL",
      "status": "DETECTED",
      "detectedAt": "2026-10-02T19:30:00Z",
      "resolvedAt": null,
      "description": "Train TR100 has been CANCELLED.",
      "operatorNotes": null,
      "estimatedDelayMinutes": 0,
      "platform": "4",
      "resolved": false,
      "rebookingTriggered": false,
      "eligibleForRecommendation": true
    }
  ],
  "timestamp": "2026-10-02T19:30:05Z"
}
```

### 2. Retrieve Specific Disruption Event

```http
GET /api/disruptions/{id}
Authorization: Bearer <user_token>
```

#### Access Control Rules:
- Returns `200 OK` if the disruption belongs to the authenticated user's journey or user has `ROLE_ADMIN`.
- Returns `403 FORBIDDEN` if the disruption belongs to another user's journey.
- Returns `404 NOT FOUND` if the disruption event ID does not exist.

---

---

## How to Test This Phase

### 1. Automated Integration Tests

Run Phase 13 tests:

```bash
mvn test -Dtest=DisruptionEngineIntegrationTest
```

Run both Monitoring and Disruption tests:

```powershell
mvn test "-Dtest=TrainMonitoringSchedulerIntegrationTest,DisruptionEngineIntegrationTest"
```

### Test Coverage Matrix

| # | Test Method | What it verifies |
|---|---|---|
| 1 | `trainCancellation_CreatesCriticalDisruptionEvents_AndMarksJourneysDisrupted` | Cancellation creates `CRITICAL` severity, `DETECTED` status event and marks journey `DISRUPTED`. |
| 2 | `minorDelay_UnderMinThreshold_IsIgnored` | Delays `< 15 min` do not generate disruption events or disrupt journeys. |
| 3 | `mediumDelay_CreatesMediumDisruptionEvent` | Delay 25 min creates `MEDIUM` severity event and marks journey `DISRUPTED`. |
| 4 | `highDelay_CreatesHighDisruptionEvent` | Delay 75 min creates `HIGH` severity event. |
| 5 | `platformChange_CreatesInformationalEvent_WithoutMarkingJourneyDisrupted` | Platform change creates `PLATFORM_CHANGE` informational event without disrupting journey. |
| 6 | `duplicatePrevention_SuppressesDuplicateOpenDisruptionEvents` | Skips duplicate event creation when an open disruption exists for the journey/schedule. |
| 7 | `eventLifecycleStateUpdates_TransitionsStatusCorrectly` | Updates status `DETECTED` → `PROCESSING` → `RESOLVED` and sets `resolvedAt`. |
| 8 | `getMyDisruptions_ReturnsUserDisruptions` | `GET /api/disruptions/my` returns authenticated user's disruptions. |
| 9 | `getDisruptionById_OwnedByUser_ReturnsDisruptionDetails` | `GET /api/disruptions/{id}` returns event details for journey owner. |
| 10 | `getDisruptionById_OwnedByAnotherUser_ReturnsForbidden` | Accessing another passenger's disruption returns `403 Forbidden`. |
| 11 | `getDisruptionById_NonExistentId_ReturnsNotFound` | Accessing invalid ID returns `404 Not Found`. |

---

### 2. Manual Walkthrough & Step-by-Step API Testing

Follow these steps using PowerShell / `curl` to test the Disruption Detection Engine end-to-end:

#### Step 1: Start the Spring Boot Backend

```powershell
mvn spring-boot:run
```

#### Step 2: Register & Login a User

```powershell
# 1. Register User
$reg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
  -Method POST -ContentType "application/json" `
  -Body '{"firstName":"Alice","lastName":"Smith","email":"alice.smith@example.com","password":"Alice@2026!"}'

# 2. Login to receive JWT Token
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"email":"alice.smith@example.com","password":"Alice@2026!"}'
$TOKEN = $login.data.accessToken
```

#### Step 3: Create a Journey and Book a Ticket

```powershell
# Create Journey
$journey = Invoke-RestMethod -Uri "http://localhost:8080/api/journeys" `
  -Method POST `
  -Headers @{ "Authorization" = "Bearer $TOKEN" } `
  -ContentType "application/json" `
  -Body '{"originStation":"London","destinationStation":"Manchester","travelDate":"2026-12-25"}'
$JOURNEY_ID = $journey.data.id

# Book a Ticket on Schedule 1
$booking = Invoke-RestMethod -Uri "http://localhost:8080/api/bookings" `
  -Method POST `
  -Headers @{ "Authorization" = "Bearer $TOKEN" } `
  -ContentType "application/json" `
  -Body "{`"scheduleId`":1,`"journeyId`":$JOURNEY_ID,`"seatClass`":`"SECOND`",`"numberOfSeats`":1}"
```

#### Step 4: Admin Simulates a Disruption (Train Cancellation or Delay)

```powershell
# Login as Admin
$adminLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"email":"admin@trainconcierge.com","password":"Admin@2026!"}'
$ADMIN_TOKEN = $adminLogin.data.accessToken

# Update Schedule 1 status to CANCELLED
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/simulation/trains/1/status" `
  -Method PUT `
  -Headers @{ "Authorization" = "Bearer $ADMIN_TOKEN" } `
  -ContentType "application/json" `
  -Body '{"status":"CANCELLED","delayMinutes":0,"message":"Locomotive engine fault"}'
```

#### Step 5: Trigger Monitoring Cycle & Verify Disruption Detection

```powershell
# The background scheduler (or manual trigger) detects the disruption and creates DisruptionEvents.
# Check Alice's disruptions:
$disruptions = Invoke-RestMethod -Uri "http://localhost:8080/api/disruptions/my" `
  -Method GET `
  -Headers @{ "Authorization" = "Bearer $TOKEN" }

$disruptions.data
```

#### Step 6: Fetch Specific Disruption Details by ID

```powershell
$DISRUPTION_ID = $disruptions.data[0].id

$details = Invoke-RestMethod -Uri "http://localhost:8080/api/disruptions/$DISRUPTION_ID" `
  -Method GET `
  -Headers @{ "Authorization" = "Bearer $TOKEN" }

$details.data
```

---

## Phase Progress Summary


| Phase | Module | Status |
|---|---|---|
| 8 | Hotel Booking Simulation | ✅ Complete |
| 9 | Cab Booking Simulation | ✅ Complete |
| 10 | Mock Train Status Service | ✅ Complete |
| 11 | Smart Seat Availability Alert | ✅ Complete |
| 12 | Train Monitoring Scheduler | ✅ Complete |
| **13** | **Disruption Detection Engine** | ✅ **Complete** |

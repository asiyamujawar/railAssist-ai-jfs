# Phase 14 — Alternative Train Recommendation Engine

> **Module status:** Complete  
> **New package:** `com.trainconcierge.recommendation`  
> **New files:** 7 source + 1 test  
> **New tests:** 8 (Total project tests: 181)

---

## Overview

The Alternative Train Recommendation Engine generates ranked alternative train options for passengers affected by disruption events. It uses a fully deterministic, Java-based weighted scoring system — no machine learning or external LLM dependencies are involved. The same input data always produces the same ranked output.

### Key Features & Design Rules

| Feature | Design Implementation |
|---|---|
| **Deterministic Scoring** | `RecommendationScorer` evaluates all alternatives using four configurable criteria weighted to sum to 1.0. |
| **Configurable Weights** | `RecommendationProperties` exposes weights via `application.properties` (arrival 35%, duration 25%, fare 20%, seats 20% by default). |
| **Alternative Filtering** | Excludes the original disrupted schedule, cancelled schedules, and alternatives with insufficient seat availability. |
| **Route Matching** | Only schedules serving the exact same origin → destination route as the disrupted journey are considered. |
| **Ranked Output** | Recommendations are sorted by composite score (highest first) before persistence and API response. |
| **Score Storage** | Scores are persisted as `NUMERIC(4,3)` in the `recommendations` table (0.000–1.000 range) and returned as 0–100 in API responses. |
| **Reason String** | Each recommendation includes a human-readable explanation summarising arrival diff, fare difference, duration, and available seats. |
| **No Booking Changes** | The service strictly generates recommendations — it does NOT perform any booking, hotel, or cab updates. |
| **User Ownership** | All recommendation endpoints verify that the requesting user owns the affected disruption event. |

---

## Architecture

```
[DisruptionDetectedEvent]
          │
          ▼
[RecommendationService.generateRecommendations()]
   ├── 1. Load DisruptionEvent → verify user ownership
   ├── 2. Load original TrainSchedule (origin, destination, fare)
   ├── 3. Search all candidate TrainSchedules on the same route
   ├── 4. Exclude: original schedule / cancelled / departed / no seats
   ├── 5. Score each candidate via RecommendationScorer
   │         ├── Arrival Time Proximity Score (0–100)
   │         ├── Travel Duration Score (0–100)
   │         ├── Fare Difference Score (0–100)
   │         └── Seat Availability Score (0–100)
   │              ↓
   │         Weighted Sum → totalScore100 → normalizedScore (0.000–1.000)
   ├── 6. Sort candidates by totalScore100 descending
   ├── 7. Delete existing PENDING recommendations for (disruption, user)
   ├── 8. Persist ranked Recommendation entities
   └── 9. Mark DisruptionEvent as PROCESSING + rebookingTriggered = true
```

---

## Scoring Algorithm

### Criteria and Formula

| Criterion | Raw Score (0–100) | Formula |
|---|---|---|
| **Arrival Time Proximity** | Later → lower score | `100 - (diffMinutes × 0.5)`, minimum 0 |
| **Travel Duration** | Longer than original → lower | `100 - (extraMinutes × 1.0)`, minimum 0 |
| **Fare Difference** | More expensive → lower | `100 - (fareDiff × 2.0)`, minimum 0 |
| **Seat Availability** | More seats → higher | `min(100, availableSeats × 2.0)` |

### Default Weights

```properties
disruption.recommendation.weight-arrival=0.35
disruption.recommendation.weight-duration=0.25
disruption.recommendation.weight-fare=0.20
disruption.recommendation.weight-seats=0.20
```

> **Constraint:** Weights must sum to exactly **1.0** for normalized output.

### Example Calculation

Given `altSchedule1` (arrives 60 min later, same duration as original, £5 more expensive, 40 available seats):

```
Arrival Score:  100 - (60 × 0.5)  = 70.0
Duration Score: 100.0              (same duration)
Fare Score:     100 - (5 × 2.0)   = 90.0
Seats Score:    min(100, 40 × 2)  = 80.0

Total = (70.0 × 0.35) + (100.0 × 0.25) + (90.0 × 0.20) + (80.0 × 0.20)
      = 24.5 + 25.0 + 18.0 + 16.0
      = 83.50 / 100
```

---

## Configuration Reference

```properties
# ============================================================
# Recommendation Engine (Weighted Scoring) — Phase 14
# ============================================================

# Scoring criterion weights — must sum to 1.0
disruption.recommendation.weight-arrival=0.35
disruption.recommendation.weight-duration=0.25
disruption.recommendation.weight-fare=0.20
disruption.recommendation.weight-seats=0.20

# Minimum available seats for an alternative to be eligible
disruption.recommendation.min-seats-available=1

# Maximum search window for alternatives in hours (24 = same day)
disruption.recommendation.max-search-window-hours=24
```

---

## REST API Reference

### 1. Generate Recommendations (POST)

```http
POST /api/disruptions/{id}/recommendations
Authorization: Bearer <user_token>
```

Generates and persists ranked alternative train recommendations for the disruption identified by `{id}`.

#### Response `200 OK`:
```json
{
  "success": true,
  "message": "Generated 2 ranked recommendation(s) successfully.",
  "data": [
    {
      "id": 1,
      "disruptionEventId": 5,
      "suggestedScheduleId": 12,
      "trainNumber": "TR-ALT1",
      "trainName": "Fast Alternative",
      "originStation": "London",
      "destinationStation": "Edinburgh",
      "scheduledDate": "2026-10-05",
      "departureTime": "09:00:00",
      "arrivalTime": "13:00:00",
      "estimatedFare": 105.00,
      "availableSeats": 40,
      "score": 83.50,
      "reason": "TR-ALT1 departing at 09:00 (arrives 13:00). Arrives 60 min later. Same travel duration. Fare difference: +£5.00. 40 available seat(s). Score: 83.5/100.",
      "status": "PENDING"
    },
    {
      "id": 2,
      "disruptionEventId": 5,
      "suggestedScheduleId": 13,
      "trainNumber": "TR-ALT2",
      "trainName": "Slower Alternative",
      "originStation": "London",
      "destinationStation": "Edinburgh",
      "scheduledDate": "2026-10-05",
      "departureTime": "10:00:00",
      "arrivalTime": "15:00:00",
      "estimatedFare": 90.00,
      "availableSeats": 20,
      "score": 62.00,
      "reason": "TR-ALT2 departing at 10:00 (arrives 15:00). Arrives 180 min later. Travel time 300m (60m longer). Fare is £10.00 cheaper. 20 available seat(s). Score: 62.0/100.",
      "status": "PENDING"
    }
  ],
  "timestamp": "2026-10-02T20:00:00Z"
}
```

#### Error Responses:
| Status | Condition |
|---|---|
| `403 Forbidden` | Disruption event belongs to another user's journey. |
| `404 Not Found` | Disruption event ID does not exist. |
| `400 Bad Request` | No original schedule associated with disruption. |

---

### 2. Get Persisted Recommendations (GET)

```http
GET /api/disruptions/{id}/recommendations
Authorization: Bearer <user_token>
```

Returns previously generated and persisted recommendations for the disruption event, sorted by score descending.

#### Response `200 OK`:
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": [
    {
      "id": 1,
      "trainNumber": "TR-ALT1",
      "score": 83.50,
      "status": "PENDING"
    }
  ],
  "timestamp": "2026-10-02T20:00:05Z"
}
```

#### Error Responses: Same as POST endpoint.

---

## How to Test This Phase

### 1. Automated Integration Tests

Run Phase 14 tests only:

```bash
mvn test -Dtest=RecommendationEngineIntegrationTest
```

Run the full Phase 13 + 14 test suite:

```powershell
mvn test "-Dtest=DisruptionEngineIntegrationTest,RecommendationEngineIntegrationTest"
```

Run all project tests:

```bash
mvn test
```

### Test Coverage Matrix

| # | Test Method | What it verifies |
|---|---|---|
| 1 | `generateRecommendations_RanksAlternativesByScoreDescending` | Recommendations are returned in score-descending order; `TR-ALT1` ranks above `TR-ALT2`. |
| 2 | `generateRecommendations_FiltersOutCancelledAndNoSeatAlternatives` | Cancelled schedules and zero-seat alternatives are excluded from results. |
| 3 | `scoreNormalization_CalculatesExactWeightedScores` | `RecommendationScorer.score()` produces exact sub-scores and total matching manual formula. |
| 4 | `generateRecommendations_NoEligibleAlternatives_ReturnsEmptyList` | Gracefully returns an empty list when no eligible alternatives are available. |
| 5 | `postRecommendations_GeneratesAndReturnsRankedList` | `POST` endpoint returns 200 with 2 ranked recommendations; score > 80; recommendations persisted in DB. |
| 6 | `getRecommendations_ReturnsPersistedRankedList` | `GET` endpoint returns previously generated recommendations in the same ranked order. |
| 7 | `recommendations_OwnedByAnotherUser_ReturnsForbidden` | Both `POST` and `GET` return `403 Forbidden` when requested by a different user. |
| 8 | `recommendations_InvalidDisruptionId_ReturnsNotFound` | Both endpoints return `404 Not Found` for a non-existent disruption ID. |

---

### 2. Manual Walkthrough & Step-by-Step API Testing

Follow these steps using PowerShell to test the Recommendation Engine end-to-end:

#### Step 1: Start the Spring Boot Backend

```powershell
mvn spring-boot:run
```

#### Step 2: Register & Login a Passenger

```powershell
# Register User
$reg = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
  -Method POST -ContentType "application/json" `
  -Body '{"firstName":"Alice","lastName":"Smith","email":"alice.smith@example.com","password":"Alice@2026!"}'

# Login
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"email":"alice.smith@example.com","password":"Alice@2026!"}'
$TOKEN = $login.data.accessToken
```

#### Step 3: Book a Ticket (creates a Journey automatically)

```powershell
# Search for schedules on your route
$schedules = Invoke-RestMethod -Uri "http://localhost:8080/api/schedules/search?originStation=London&destinationStation=Edinburgh&journeyDate=2026-12-25" `
  -Method GET `
  -Headers @{ "Authorization" = "Bearer $TOKEN" }
$SCHEDULE_ID = $schedules.data[0].scheduleId

# Book a ticket
$booking = Invoke-RestMethod -Uri "http://localhost:8080/api/bookings" `
  -Method POST `
  -Headers @{ "Authorization" = "Bearer $TOKEN" } `
  -ContentType "application/json" `
  -Body "{`"scheduleId`":$SCHEDULE_ID,`"seatClass`":`"SECOND`",`"passengerCount`":1}"
$BOOKING_ID = $booking.data.id
```

#### Step 4: Admin Simulates Train Cancellation

```powershell
# Login as Admin
$adminLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"email":"admin@trainconcierge.com","password":"Admin@2026!"}'
$ADMIN_TOKEN = $adminLogin.data.accessToken

# Cancel the schedule via Simulation endpoint
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/simulation/trains/$SCHEDULE_ID/status" `
  -Method PUT `
  -Headers @{ "Authorization" = "Bearer $ADMIN_TOKEN" } `
  -ContentType "application/json" `
  -Body '{"status":"CANCELLED","message":"Locomotive engine fault"}'
```

#### Step 5: Monitoring Cycle Detects Disruption

The background scheduler runs every 60 seconds (configurable). Wait for a cycle, or wait for `monitoring.enabled=true` in dev. Then check Alice's disruptions:

```powershell
$disruptions = Invoke-RestMethod -Uri "http://localhost:8080/api/disruptions/my" `
  -Method GET `
  -Headers @{ "Authorization" = "Bearer $TOKEN" }

$DISRUPTION_ID = $disruptions.data[0].id
Write-Host "Disruption ID: $DISRUPTION_ID, Type: $($disruptions.data[0].type), Severity: $($disruptions.data[0].severity)"
```

#### Step 6: Generate Alternative Train Recommendations

```powershell
$recommendations = Invoke-RestMethod -Uri "http://localhost:8080/api/disruptions/$DISRUPTION_ID/recommendations" `
  -Method POST `
  -Headers @{ "Authorization" = "Bearer $TOKEN" }

$recommendations.data | ForEach-Object {
    Write-Host "Train: $($_.trainNumber) | Departure: $($_.departureTime) | Score: $($_.score)/100 | Seats: $($_.availableSeats)"
    Write-Host "Reason: $($_.reason)"
    Write-Host "---"
}
```

#### Step 7: Fetch Persisted Recommendations (GET)

```powershell
$savedRecs = Invoke-RestMethod -Uri "http://localhost:8080/api/disruptions/$DISRUPTION_ID/recommendations" `
  -Method GET `
  -Headers @{ "Authorization" = "Bearer $TOKEN" }

$savedRecs.data
```

#### Step 8: Verify Access Control (Forbidden for other users)

```powershell
# Register a second user (Bob)
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
  -Method POST -ContentType "application/json" `
  -Body '{"firstName":"Bob","lastName":"Jones","email":"bob.jones@example.com","password":"Bob@2026!"}'

$bobLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"email":"bob.jones@example.com","password":"Bob@2026!"}'
$BOB_TOKEN = $bobLogin.data.accessToken

# Bob tries to access Alice's recommendations (should get 403)
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/disruptions/$DISRUPTION_ID/recommendations" `
      -Method GET `
      -Headers @{ "Authorization" = "Bearer $BOB_TOKEN" }
} catch {
    Write-Host "Expected: 403 Forbidden — $($_.Exception.Message)"
}
```

---

## Files Created

| File | Package | Purpose |
|---|---|---|
| `Recommendation.java` | `recommendation` | JPA entity linking disruption → user → suggested schedule |
| `RecommendationRepository.java` | `recommendation` | DAO for scoring results and ranked queries |
| `RecommendationProperties.java` | `recommendation` | Configurable weights and filtering thresholds |
| `RecommendationScorer.java` | `recommendation` | Deterministic weighted scoring engine |
| `RecommendationService.java` | `recommendation` | Core generation, filtering, ranking, and persistence service |
| `RecommendationResponse.java` | `recommendation` | API response DTO (score shown as 0–100) |
| `RecommendationController.java` | `recommendation` | REST endpoints `POST` and `GET /api/disruptions/{id}/recommendations` |
| `RecommendationEngineIntegrationTest.java` | `test/recommendation` | 8 integration tests covering all scenarios |

---

## Phase Progress Summary

| Phase | Module | Status |
|---|---|---|
| 8 | Hotel Booking Simulation | ✅ Complete |
| 9 | Cab Booking Simulation | ✅ Complete |
| 10 | Mock Train Status Service | ✅ Complete |
| 11 | Smart Seat Availability Alert | ✅ Complete |
| 12 | Train Monitoring Scheduler | ✅ Complete |
| 13 | Disruption Detection Engine | ✅ Complete |
| **14** | **Alternative Train Recommendation Engine** | ✅ **Complete** |

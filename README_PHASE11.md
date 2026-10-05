# Phase 11 — Smart Seat Availability Alert

> **Module status:** Complete  
> **Spring feature used:** `@Scheduled` (fixed-delay polling)  
> **New tables:** `seat_alert_subscriptions` (extended), `seat_alert_history`  
> **New endpoint count:** 3  
> **New test count:** 15  

---

## Overview

The Smart Seat Availability Alert module lets authenticated users subscribe to seat-availability notifications for a specific train schedule and seat class. A configurable Spring Scheduler polls the database on a fixed interval and fires an alert when the available seat count reaches or drops to the user's configured **threshold**.

### Key design decisions

| Concern | Decision |
|---|---|
| Seat data source | Database-backed `seat_availability` table — no external APIs |
| Duplicate alerts | One subscription → one alert. The subscription is auto-deactivated after firing. |
| Notification delivery | Separated: monitor writes a `Notification` row (outbox); actual push/email/SMS delivery is a future concern |
| Scheduler strategy | `fixedDelay` (not `fixedRate`) — next poll only starts after the previous one completes, avoiding overlaps |
| Ownership | Users can only create, view, or deactivate **their own** subscriptions |
| Alert history | Append-only `seat_alert_history` table — never updated/deleted |

---

## Architecture

```
SeatAlertController          (REST – 3 endpoints)
      │
      ▼
SeatAlertSubscriptionService (CRUD + ownership enforcement)
      │
      ▼
SeatAlertMonitorService      (Spring Scheduler – polling loop)
      │          │
      ▼          ▼
SeatAvailability   SeatAlertHistory
Repository         Repository
      │
      ▼
NotificationRepository       (in-app outbox – delivery is separate)
```

---

## New / Modified Files

### Main source

| File | Role |
|---|---|
| [`SeatAlertSubscription.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAlertSubscription.java) | Entity — extended with `threshold`, `lastAlertedAt`, `alertCount` |
| [`SeatAlertSubscriptionRepository.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAlertSubscriptionRepository.java) | Added `findAllActiveUntriggered()` JPQL query for scheduler |
| [`SeatAlertHistory.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAlertHistory.java) | New entity — append-only alert event log |
| [`SeatAlertHistoryRepository.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAlertHistoryRepository.java) | New repository |
| [`SeatAlertSubscriptionService.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAlertSubscriptionService.java) | New service — subscribe, list, deactivate |
| [`SeatAlertMonitorService.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAlertMonitorService.java) | New service — scheduler + threshold evaluation + alert firing |
| [`SeatAlertController.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAlertController.java) | New REST controller — 3 endpoints |
| [`dto/CreateSeatAlertRequest.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/dto/CreateSeatAlertRequest.java) | New request DTO |
| [`dto/SeatAlertSubscriptionResponse.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/dto/SeatAlertSubscriptionResponse.java) | New response DTO with live seat count |
| [`JpaConfig.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/config/JpaConfig.java) | Added `@EnableScheduling` |
| [`ErrorCode.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/exception/ErrorCode.java) | Added `SEAT_ALERT_*` error codes |
| [`application.properties`](file:///d:/MegaProject/TrainConceirge/src/main/resources/application.properties) | Added `seat.alert.poll-interval-ms` |

### Test source

| File | Role |
|---|---|
| [`SeatAlertIntegrationTest.java`](file:///d:/MegaProject/TrainConceirge/src/test/java/com/trainconcierge/seat/SeatAlertIntegrationTest.java) | 15 integration tests |
| [`src/test/resources/application.properties`](file:///d:/MegaProject/TrainConceirge/src/test/resources/application.properties) | Set `seat.alert.poll-interval-ms=5000` for tests |

---

## API Reference

All endpoints require a valid **JWT Bearer token** in the `Authorization` header.

### POST `/api/seat-alerts`
Subscribe to seat availability alerts.

**Request body:**
```json
{
  "scheduleId": 1,
  "seatClass": "SECOND",
  "threshold": 5
}
```

| Field | Required | Default | Notes |
|---|---|---|---|
| `scheduleId` | ✅ | — | Must exist |
| `seatClass` | ✅ | — | `FIRST`, `SECOND`, `SLEEPER`, `BUSINESS` |
| `threshold` | ❌ | `0` | Alert fires when `availableSeats <= threshold`. `0` = alert when any seat opens from fully booked. `5` = alert when 5 or fewer seats remain. Must be ≥ 0. |

**Response:** `201 Created`
```json
{
  "success": true,
  "message": "Seat alert subscription created. You will be notified when the available seat count reaches or drops to 5.",
  "data": {
    "id": 1,
    "scheduleId": 1,
    "trainNumber": "12301",
    "trainName": "Rajdhani Express",
    "scheduledDate": "2026-10-15",
    "seatClass": "SECOND",
    "threshold": 5,
    "active": true,
    "triggered": false,
    "alertCount": 0,
    "currentAvailableSeats": 42,
    "createdAt": "2026-10-02T12:00:00Z"
  }
}
```

**Error responses:**

| HTTP | Condition |
|---|---|
| `400` | Duplicate active subscription for same user/schedule/class |
| `400` | `threshold < 0` |
| `404` | `scheduleId` does not exist |
| `401` | Missing or invalid token |

---

### GET `/api/seat-alerts/my`
Returns all subscriptions (active and triggered) for the authenticated user.

**Response:** `200 OK` — array of subscription objects (same structure as above).

---

### PATCH `/api/seat-alerts/{id}/deactivate`
Manually deactivate an active alert subscription.

**Response:** `200 OK` — subscription with `active: false`.

**Error responses:**

| HTTP | Condition |
|---|---|
| `400` | Subscription already inactive |
| `403` | Subscription belongs to another user |
| `404` | Subscription not found |

---

## Scheduler Configuration

The polling interval is controlled by a property so it can be tuned per environment:

```properties
# application.properties (production default — 1 minute)
seat.alert.poll-interval-ms=${SEAT_ALERT_POLL_MS:60000}
```

```properties
# test application.properties — 5 seconds for fast tests
seat.alert.poll-interval-ms=5000
```

To use a short interval in development:

```bash
# Override at startup (10 second poll for live demos)
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DSEAT_ALERT_POLL_MS=10000"
```

Or set environment variable before starting:

```powershell
$env:SEAT_ALERT_POLL_MS = "10000"
mvn spring-boot:run
```

> [!NOTE]
> `fixedDelay` is used (not `fixedRate`). The next poll only begins after the previous one completes, so a slow DB will not stack up overlapping scheduler invocations.

---

## How to Test This Phase

### 1. Automated Integration Tests

Run Phase 11 tests only:

```bash
mvn test -Dtest=SeatAlertIntegrationTest
```

Run the full test suite (all phases):

```bash
mvn test
```

**Expected output:**
```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0  ← Phase 11
[INFO] BUILD SUCCESS
```

#### Test coverage matrix

| # | Test name | What it verifies |
|---|---|---|
| 1 | `subscribe_Success_Returns201` | Happy path — subscription created with correct fields |
| 2 | `subscribe_Duplicate_Returns400` | Duplicate guard — same user/schedule/class rejected |
| 3 | `getMyAlerts_ReturnsAllSubscriptionsForUser` | Isolation — user A sees 2, user B sees 0 |
| 4 | `deactivate_Success_Returns200` | Manual deactivate sets `active=false` |
| 5 | `deactivate_AlreadyInactive_Returns400` | Cannot deactivate twice |
| 6 | `deactivate_OtherUsersAlert_Returns403` | Ownership enforcement |
| 7 | `unauthenticated_Returns401` | Security — no token = 401 |
| 8 | `subscribe_InvalidScheduleId_Returns404` | Non-existent schedule rejected |
| 9 | `subscribe_NegativeThreshold_Returns400` | Validation — negative threshold rejected |
| 10 | `monitor_FiresAlert_WhenSeatsAtOrBelowThreshold_Default0` | Core threshold logic (0 seats, threshold=0 → fire) |
| 11 | `monitor_DoesNotFireAlert_WhenSeatsAboveThreshold` | No false alerts (10 seats, threshold=5 → no fire) |
| 12 | `monitor_NoDuplicateAlert_WhenAlreadyTriggered` | Duplicate suppression — scheduler query excludes triggered |
| 13 | `monitor_FiresAlert_WhenThresholdGreaterThanZeroAndSeatsAtOrBelow` | Threshold > 0 — (10 seats, threshold=10 → fire on boundary) |
| 14 | `monitor_AlertHistory_ContainsCorrectDetails` | History row has correct seat count, threshold, message |
| 15 | `fullCycle_Subscribe_AndMonitorFires` | End-to-end — subscribe via API → run monitor → check via API |

---

### 2. Manual cURL Walkthrough

> **Prerequisite:** Application running on `http://localhost:8080`. Replace `<TOKEN>` with the JWT from step 1.

#### Step 1 — Register and log in

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Seat",
    "lastName": "Watcher",
    "email": "seat.watcher@example.com",
    "password": "Watch@2026!"
  }'

# Login — copy the token from the response
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "seat.watcher@example.com",
    "password": "Watch@2026!"
  }' | python -m json.tool
```

**PowerShell — capture token automatically:**
```powershell
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"email":"seat.watcher@example.com","password":"Watch@2026!"}'
$TOKEN = $login.data.accessToken
```

---

#### Step 2 — Create a train and schedule (admin or use existing)

> If you already have a `scheduleId` from Phase 10 or earlier, skip to Step 3.

```bash
# Create train (requires ADMIN token — register an admin user first)
curl -s -X POST http://localhost:8080/api/trains \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "trainNumber": "12301",
    "trainName": "Rajdhani Express",
    "originStation": "New Delhi",
    "destinationStation": "Mumbai",
    "totalSeats": 500,
    "seatClassConfigs": [
      {"seatClass": "SECOND", "totalSeats": 300, "fare": 350.00},
      {"seatClass": "FIRST",  "totalSeats": 100, "fare": 900.00}
    ]
  }'

# Create schedule
curl -s -X POST http://localhost:8080/api/schedules \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "trainId": 1,
    "scheduledDate": "2026-12-25",
    "scheduledDeparture": "07:00",
    "scheduledArrival": "19:30",
    "platform": "5",
    "baseFare": 350.00
  }'
```

Note the `scheduleId` from the response (e.g. `1`).

---

#### Step 3 — Check current seat availability

```bash
curl -s http://localhost:8080/api/seat-availability/schedules/1 \
  -H "Authorization: Bearer <TOKEN>" | python -m json.tool
```

Expected: you will see `availableSeats` for each seat class.

---

#### Step 4 — Subscribe with threshold = current available seats

This guarantees the alert fires on the very next scheduler poll.

```bash
# Subscribe — alert fires when SECOND class seats <= 300 (boundary condition)
curl -s -X POST http://localhost:8080/api/seat-alerts \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "scheduleId": 1,
    "seatClass": "SECOND",
    "threshold": 300
  }' | python -m json.tool
```

Note the `id` from the response (e.g. `id: 1`).

---

#### Step 5 — Check your subscriptions

```bash
curl -s http://localhost:8080/api/seat-alerts/my \
  -H "Authorization: Bearer <TOKEN>" | python -m json.tool
```

You should see `active: true`, `triggered: false`, `alertCount: 0`.

---

#### Step 6 — Trigger the scheduler manually (or wait for poll)

The scheduler runs automatically every `seat.alert.poll-interval-ms` milliseconds. To test without waiting, reduce the interval at startup:

```powershell
$env:SEAT_ALERT_POLL_MS = "5000"   # 5-second poll for demo
mvn spring-boot:run
```

Then wait 5–10 seconds after subscribing.

---

#### Step 7 — Verify alert fired

```bash
curl -s http://localhost:8080/api/seat-alerts/my \
  -H "Authorization: Bearer <TOKEN>" | python -m json.tool
```

Expected response fields:
```json
{
  "active": false,
  "triggered": true,
  "alertCount": 1,
  "triggeredAt": "2026-10-02T...",
  "lastAlertedAt": "2026-10-02T..."
}
```

---

#### Step 8 — Test manual deactivation

```bash
# Create a new subscription (different class)
curl -s -X POST http://localhost:8080/api/seat-alerts \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "scheduleId": 1,
    "seatClass": "FIRST",
    "threshold": 50
  }' | python -m json.tool

# Deactivate it manually (use the new subscription's id, e.g. 2)
curl -s -X PATCH http://localhost:8080/api/seat-alerts/2/deactivate \
  -H "Authorization: Bearer <TOKEN>" | python -m json.tool
```

Expected: `active: false`, `triggered: false` (manually stopped, no alert fired).

---

### 3. Validation Error Testing

#### Negative threshold → 400
```bash
curl -s -X POST http://localhost:8080/api/seat-alerts \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"scheduleId": 1, "seatClass": "SECOND", "threshold": -1}'
```

#### Duplicate subscription → 400
```bash
# Call POST twice with the same scheduleId + seatClass
curl -s -X POST http://localhost:8080/api/seat-alerts \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"scheduleId": 1, "seatClass": "FIRST", "threshold": 5}'

curl -s -X POST http://localhost:8080/api/seat-alerts \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"scheduleId": 1, "seatClass": "FIRST", "threshold": 5}'
```

#### Non-existent schedule → 404
```bash
curl -s -X POST http://localhost:8080/api/seat-alerts \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"scheduleId": 99999, "seatClass": "SECOND", "threshold": 0}'
```

---

### 4. Security Testing

#### No token → 401
```bash
curl -s http://localhost:8080/api/seat-alerts/my
```

#### Deactivate another user's alert → 403
```bash
# Login as user B, try to deactivate user A's alert id
curl -s -X PATCH http://localhost:8080/api/seat-alerts/1/deactivate \
  -H "Authorization: Bearer <USER_B_TOKEN>"
```

---

## Threshold Logic Reference

| Available seats | Threshold | Alert fires? |
|---|---|---|
| 0 | 0 | ✅ Yes — seats = threshold |
| 5 | 0 | ❌ No — seats > threshold |
| 0 | 5 | ✅ Yes — seats < threshold |
| 5 | 5 | ✅ Yes — seats = threshold |
| 6 | 5 | ❌ No — seats > threshold |
| 10 | 10 | ✅ Yes — seats = threshold |

**Rule:** alert fires when `availableSeats <= threshold`.

---

## Running the Application

```bash
# Standard start
mvn spring-boot:run

# With short scheduler interval for development
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DSEAT_ALERT_POLL_MS=10000"

# With specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Phase Summary

| Phase | Module | Status |
|---|---|---|
| 8 | Hotel Booking Simulation | ✅ Complete |
| 9 | Cab Booking Simulation | ✅ Complete |
| 10 | Mock Train Status Service | ✅ Complete |
| **11** | **Smart Seat Availability Alert** | ✅ **Complete** |

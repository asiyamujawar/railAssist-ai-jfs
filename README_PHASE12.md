# Phase 12 — Train Monitoring Scheduler

> **Module status:** Complete  
> **Spring feature used:** `@Scheduled` (fixed-delay polling)  
> **New package:** `com.trainconcierge.monitoring`  
> **New files:** 3 source + 1 test  
> **New tests:** 17  

---

## Overview

The Train Monitoring Scheduler is the **central nervous system** of TrainConcierge's disruption pipeline. It polls the train status layer on a configurable interval, compares each schedule's current status against its last known state, and — when a meaningful change is detected — delegates immediately to `DisruptionDetectionService` without performing any recommendation or rebooking work itself.

### Key design decisions

| Concern | Decision |
|---|---|
| Status source | `TrainStatusPort` (currently `MockTrainStatusService`) — the future real adapter will implement the same interface without touching the scheduler |
| Status comparison | In-memory `HashMap<scheduleId, TrainStatus>` cache — resets on restart; first post-restart poll that detects a change re-triggers detection correctly |
| Duplicate detection | `DisruptionDetectionService` checks for open (unresolved) events before creating a new one |
| One concern per class | Scheduler: monitor + delegate only. DetectionService: persist + mark journeys. No recommendation/rebooking here. |
| Resilience | Each schedule evaluation is wrapped in try/catch — one failure does not abort the cycle |
| Disableable | `monitoring.enabled=false` suppresses the entire cycle (useful in dev) |

---

## Architecture

```
[Spring Scheduler — fixedDelay]
         │
         ▼
TrainMonitoringScheduler
  1. journeyRepository.findActiveJourneysForMonitoring(today)
  2. collect unique schedule IDs from CONFIRMED bookings
  3. for each scheduleId:
       a. trainStatusPort.getLatestStatus(scheduleId)
       b. compare with lastKnownStatus cache
       c. if changed → delegate to DisruptionDetectionService
         │
         ▼
DisruptionDetectionService
  - Map TrainStatus → DisruptionType + DisruptionSeverity
  - Check for open disruption events (duplicate guard)
  - Persist DisruptionEvent
  - Mark affected Journeys as DISRUPTED
         │
         ▼
     (Future)
  RecommendationService → RebookingService → NotificationDeliveryService
```

---

## New Files

### Main source

| File | Role |
|---|---|
| [`MonitoredStatusChange.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/monitoring/MonitoredStatusChange.java) | DTO — bridge between scheduler and detection service |
| [`TrainMonitoringScheduler.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/monitoring/TrainMonitoringScheduler.java) | `@Scheduled` polling loop |
| [`DisruptionDetectionService.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/monitoring/DisruptionDetectionService.java) | Creates `DisruptionEvent`, marks journeys `DISRUPTED` |

### Modified files

| File | Change |
|---|---|
| [`JourneyRepository.java`](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/journey/JourneyRepository.java) | Added `findActiveJourneysForMonitoring()` |
| [`application.properties`](file:///d:/MegaProject/TrainConceirge/src/main/resources/application.properties) | Added `monitoring.enabled` and `monitoring.interval-ms` |
| [`test/application.properties`](file:///d:/MegaProject/TrainConceirge/src/test/resources/application.properties) | `monitoring.enabled=false` — tests call the scheduler manually |

### Test source

| File | Role |
|---|---|
| [`TrainMonitoringSchedulerIntegrationTest.java`](file:///d:/MegaProject/TrainConceirge/src/test/java/com/trainconcierge/monitoring/TrainMonitoringSchedulerIntegrationTest.java) | 17 integration tests |

---

## Configuration Reference

```properties
# application.properties

# Enable / disable the monitoring cycle. 
# Set MONITORING_ENABLED=false in dev to silence periodic logs.
monitoring.enabled=${MONITORING_ENABLED:true}

# Fixed delay between monitoring cycles (milliseconds).
# Production default: 30 seconds.
monitoring.interval-ms=${MONITORING_INTERVAL_MS:30000}
```

### Environment variable overrides

```powershell
# Disable monitoring entirely
$env:MONITORING_ENABLED = "false"

# Poll every 5 seconds for local demos
$env:MONITORING_INTERVAL_MS = "5000"

mvn spring-boot:run
```

---

## Status Change → Disruption Mapping

| TrainStatus | DisruptionType | Severity logic |
|---|---|---|
| `DELAYED` | `DELAY` | `< 15 min` → LOW, `15–60 min` → MEDIUM, `> 60 min` → HIGH |
| `CANCELLED` | `CANCELLATION` | Always CRITICAL |
| `DIVERTED` | `DIVERSION` | Always CRITICAL |
| `PLATFORM_CHANGED` | — (no event) | Informational log only |
| `ON_TIME` | — | No action |

---

## How to Test This Phase

### 1. Automated Integration Tests

Run Phase 12 tests only:

```bash
mvn test -Dtest=TrainMonitoringSchedulerIntegrationTest
```

Run the full test suite (all phases):

```bash
mvn test
```

**Expected output:**
```
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0  ← Phase 12
[INFO] Tests run: 154, Failures: 0, Errors: 0, Skipped: 0 ← All phases
[INFO] BUILD SUCCESS
```

#### Test coverage matrix

| # | Test | What it verifies |
|---|---|---|
| 1 | `activeJourneyFiltering_OnlyPlannedAndInProgressLoaded` | COMPLETED / CANCELLED journeys excluded |
| 2 | `activeJourneyFiltering_PastTravelDateExcluded` | Past travel dates excluded |
| 3 | `activeJourneyFiltering_InProgressJourneyIncluded` | IN_PROGRESS included |
| 4 | `unchangedStatus_NoCycleAction` | First observation → no disruption |
| 5 | `unchangedStatus_AfterFirstSeed_NoDuplicateFiring` | Same status twice → no event |
| 6 | `statusChange_Delayed_CreatesDisruptionEvent_AndMarksJourneyDisrupted` | DELAYED → DisruptionEvent + MEDIUM severity + Journey.DISRUPTED |
| 7 | `statusChange_Cancelled_CriticalSeverity` | CANCELLED → CRITICAL severity |
| 8 | `statusChange_PlatformChanged_NoDisruptionEventCreated` | PLATFORM_CHANGED → informational only |
| 9 | `delayedSeverity_Low_WhenDelayUnder15Minutes` | 10 min delay → LOW |
| 10 | `delayedSeverity_High_WhenDelayOver60Minutes` | 90 min delay → HIGH |
| 11 | `duplicatePrevention_SecondChangeDoesNotCreateSecondEvent_WhenFirstIsOpen` | Open event blocks new creation |
| 12 | `errorHandling_InvalidScheduleId_DoesNotThrow` | Non-existent schedule handled gracefully |
| 13 | `cycleDoesNotAbort_WhenOneScheduleFails` | Exception in one schedule doesn't stop cycle |
| 14 | `fullCycle_ReturnsCorrectStats_AfterRun` | Cycle stats (journeys/schedules/changes) populated |
| 15 | `fullCycle_DetectsChange_WhenStatusChangedBetweenCycles` | End-to-end two-cycle detection |
| 16 | `schedulerDisabled_CycleSkipped` | `monitoring.enabled=false` → cycle body not executed |
| 17 | `cancelledBooking_NotIncludedInScheduleSet` | CANCELLED bookings excluded from schedule collection |

---

### 2. Manual Walkthrough — Triggering and Observing a Cycle

> **Prerequisites:** Application running, MySQL connected, at least one journey with a confirmed booking.

#### Step 1 — Start with monitoring at a short interval

```powershell
# 10-second poll for easy observation
$env:MONITORING_INTERVAL_MS = "10000"
mvn spring-boot:run
```

You will see log output every 10 seconds:
```
[TrainMonitor] ── Monitoring cycle START ──────────────────────────
[TrainMonitor] 0 active journeys to monitor. Cycle complete.
[TrainMonitor] ── Monitoring cycle END — journeys=0 schedules=0 changes=0 elapsed=12ms ──
```

#### Step 2 — Register a user and create a journey with a booking

```bash
# 1. Register user
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane.doe@test.com","password":"Jane@2026!"}'

# 2. Login — capture token
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"email":"jane.doe@test.com","password":"Jane@2026!"}'
$TOKEN = $login.data.accessToken

# 3. Get available schedules
curl -s http://localhost:8080/api/schedules -H "Authorization: Bearer $TOKEN"

# 4. Create a journey
curl -s -X POST http://localhost:8080/api/journeys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"originStation":"London","destinationStation":"Edinburgh","travelDate":"2026-12-25"}'

# 5. Book a seat (replace scheduleId with real value)
curl -s -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"scheduleId":1,"journeyId":1,"seatClass":"SECOND","numberOfSeats":1}'
```

#### Step 3 — Watch the next monitoring cycle

After creating the booking, the next cycle log should show:
```
[TrainMonitor] 1 active journey/journeys loaded for monitoring.
[TrainMonitor] Evaluating 1 unique schedule(s).
[TrainMonitor] First observation for schedule 1 — status=ON_TIME. Cached.
[TrainMonitor] Monitoring cycle END — journeys=1 schedules=1 changes=0
```

#### Step 4 — Trigger a simulated status change

Register an admin user and use the admin simulation API:

```bash
# Login as admin
$admin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"email":"admin@test.com","password":"Admin@2026!"}'
$ADMIN_TOKEN = $admin.data.accessToken

# Set schedule to DELAYED (replace scheduleId with real value)
curl -s -X PUT "http://localhost:8080/api/admin/simulation/trains/1/status" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "DELAYED",
    "delayMinutes": 45,
    "message": "Signal failure at junction"
  }'
```

#### Step 5 — Watch the disruption be detected

Within 10 seconds the next cycle fires and logs:
```
[TrainMonitor] Status change on schedule 1 (12301): ON_TIME → DELAYED
[DisruptionDetection] DisruptionEvent created: id=1 scheduleId=1 type=DELAY severity=MEDIUM
[DisruptionDetection] Marked 1 journey/journeys as DISRUPTED for schedule 1.
[TrainMonitor] Monitoring cycle END — journeys=1 schedules=1 changes=1
```

#### Step 6 — Verify via the disruption endpoint

```bash
# Check open disruptions (future admin endpoint)
# Alternatively verify via DB or test Spring context
```

---

### 3. Disabling Monitoring in Local Dev

```properties
# In your local application.properties override:
monitoring.enabled=false
```

Or via environment variable:

```powershell
$env:MONITORING_ENABLED = "false"
mvn spring-boot:run
```

The log will print once per interval:
```
[TrainMonitor] Monitoring is disabled — skipping cycle.
```

---

### 4. Validation / Edge Case Testing

#### Non-existent schedule ID

If a schedule is deleted while being monitored, the exception is caught per schedule:
```
[TrainMonitor] Error evaluating schedule id=999: ... — continuing cycle.
```

The cycle continues for all other schedules.

#### Status changes back to ON_TIME

When a train that was DELAYED recovers:
```
[TrainMonitor] Status change on schedule 1 (12301): DELAYED → ON_TIME
```
The in-memory cache updates. No new disruption event is created (ON_TIME is not disruptive). The journey remains DISRUPTED — resolution is handled by the future disruption resolution workflow.

---

## Running the Application

```bash
# Standard start
mvn spring-boot:run

# With 5-second monitoring interval for development
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DMONITORING_INTERVAL_MS=5000"

# Monitoring disabled
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DMONITORING_ENABLED=false"
```

---

## Phase Summary

| Phase | Module | Status |
|---|---|---|
| 8 | Hotel Booking Simulation | ✅ Complete |
| 9 | Cab Booking Simulation | ✅ Complete |
| 10 | Mock Train Status Service | ✅ Complete |
| 11 | Smart Seat Availability Alert | ✅ Complete |
| **12** | **Train Monitoring Scheduler** | ✅ **Complete** |

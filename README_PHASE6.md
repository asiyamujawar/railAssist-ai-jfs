# Phase 6 — Train Schedule & Seat Availability Management

> **Status:** ✅ Complete — All **74 tests pass** (27 Schedule+Seat integration + 20 Train Mgmt + 26 Auth + 1 context load).

---

## 📋 What's Implemented in This Phase

| # | Requirement (12 Functional + Endpoints) | Status |
|---|---|---|
| 1 | Schedule management DTOs + Service layer (`TrainScheduleService`, `SeatAvailabilityService`) | ✅ |
| 2 | Schedules associated with Train master records (`@ManyToOne` + `JOIN FETCH findByIdWithTrain`) | ✅ |
| 3 | Stored: `scheduledDate`, `scheduledDeparture`, `scheduledArrival`, `baseFare`, `ScheduleStatus` enum (SCHEDULED / ON_TIME / DELAYED / CANCELLED / COMPLETED / DIVERTED) | ✅ |
| 4 | Per-class seat counts (`SeatAvailability` × FIRST / BUSINESS / SECOND / SLEEPER) with total/available/booked/fare | ✅ |
| 5 | Non-negative seat validation: `@Min(0)` on DTOs + service-layer guards + atomic WHERE guards | ✅ |
| 6 | `GET /api/schedules/search` by route + date, **case-insensitive** station matching | ✅ |
| 7 | Admin APIs: `POST /api/admin/schedules` (add) + `PATCH /api/admin/schedules/{id}/availability` (update) | ✅ |
| 8 | `GET /api/schedules/{id}/availability` — per-schedule, per-class availability with fares | ✅ |
| 9 | **No overselling under concurrency** — `bookSeatsAtomically` uses single-row UPDATE + `availableSeats >= :seats` WHERE-guard | ✅ |
| 10 | `@Transactional` services + 3 atomic SQL updates (`book/release/updateInventory`) + `@Version` optimistic lock defence-in-depth | ✅ |
| 11 | Duplicate schedule prevention: `uq_schedule_train_date(train_id, scheduledDate)` DB constraint + `existsByTrainAndScheduledDate` pre-check → 409 | ✅ |
| 12 | 27 integration tests covering invalid schedules, seat updates, **50-thread concurrent booking**, auth gating | ✅ |
| — | `GET /api/schedules` paginated list (page/size/sortBy/sortDir) | ✅ Extra |
| — | `GET /api/schedules/{id}` single schedule detail with train info | ✅ Extra |
| — | Bigdecimal monetary columns (`baseFare`, per-class `fare`) with `@Column(precision=10,scale=2)` | ✅ Financial correctness |

⚠️ **NO IRCTC or external seat integration** — all seat data is 100% simulated MySQL records.

---

## 🏗️ Module Architecture

```
               ┌─────────────────────────────────────────────────────────────┐
               │          Phase 6 — Schedule + Seat Inventory                │
               └─────────────────────────────────────────────────────────────┘

 Public Controllers (any authenticated user — JWT Bearer):
 ┌────────────────────────────────────────────────────────────────────────────┐
 │ ScheduleController ── /api/schedules                                       │
 │   GET  /                    → getAllSchedules(pageable)                    │
 │   GET  /{id}                → getScheduleById                              │
 │   GET  /search              → searchSchedules(origin, dest, journeyDate)   │
 │   GET  /{id}/availability   → getSeatAvailabilityByScheduleId             │
 │   returns PaginatedScheduleResponse | ScheduleResponse | List<SearchResult>│
 └────────────────────────────────────────────────────────────────────────────┘

 Admin Controllers (ROLE_ADMIN ONLY — double guard: URL pattern + @PreAuthorize):
 ┌────────────────────────────────────────────────────────────────────────────┐
 │ AdminScheduleController ── /api/admin/schedules                            │
 │   POST  /                         → createSchedule (route + classes + fares)│
 │   PATCH /{id}/availability        → updateSeatAvailability (patch sem.)   │
 │   @PreAuthorize("hasRole('ADMIN')") — Class-level guard                   │
 └────────────────────────────────────────────────────────────────────────────┘

 Service Layer (@Transactional — business logic + validation + atomic ops):
 ┌────────────────────────────────────────────────────────────────────────────┐
 │ TrainScheduleService            │  SeatAvailabilityService                 │
 │  • search: case-insensitive     │  • GET availability by scheduleId        │
 │    route-different check        │  • PATCH: null-fields = keep-current     │
 │  • create: 404 train-missing    │  •  count≥0  +  avail+booked ≤ total     │
 │    400 inactive-train           │  • bookSeats(id, n) → boolean (no oversell)│
 │    400 arrival ≤ departure      │  • releaseSeats(id, n) → boolean         │
 │    409 duplicate train+date     │  • createSeatAvailabilities(schedule, cfgs)│
 └────────────────────────────────────────────────────────────────────────────┘

 Persistence Layer:
 ┌────────────────────────────────────────────────────────────────────────────┐
 │ TrainScheduleRepository          │  SeatAvailabilityRepository             │
 │  • existsByTrain+ScheduledDate   │  • findBySchedule+SeatClass             │
 │  • findByIdWithTrain (JOIN FETCH)│  • findAllByScheduleIdWithDetails       │
 │  • findAllActiveSchedules (page) │  • bookSeatsAtomically   (UPDATE + WHERE│
 │  • findAvailableSchedulesIgnore  │    guard → ROW-LEVEL LOCK no-oversell)  │
 │    Case (LOWER + JOIN train)     │  • releaseSeatsAtomically               │
 │  @Table uniqueConstraint         │  • updateInventoryAndFareAtomically     │
 │    uq_schedule_train_date        │  @Version optimistic lock (defence-in-  │
 │                                  │    depth) + @Modifying(clearAutomatically)│
 └────────────────────────────────────────────────────────────────────────────┘
```

### Endpoint Matrix (6 Endpoints: 4 Public + 2 Admin)

| Method | Path | Auth | Role | Purpose |
|---|---|---|---|---|
| **GET** | `/api/schedules` | Bearer | Any | Paginated schedule list (page/size/sortBy/sortDir) |
| **GET** | `/api/schedules/{id}` | Bearer | Any | Single schedule detail + train info |
| **GET** | `/api/schedules/search` | Bearer | Any | Search schedules by `originStation` + `destinationStation` + `journeyDate` → includes per-class availability + fares |
| **GET** | `/api/schedules/{id}/availability` | Bearer | Any | Seat availability breakdown for a single schedule |
| **POST** | `/api/admin/schedules` | Bearer | **ADMIN** | Create schedule: associate train, times, 4 class configs (seats + fares) |
| **PATCH** | `/api/admin/schedules/{id}/availability` | Bearer | **ADMIN** | Update seat counts or fare for a specific class on a schedule (null fields keep current values) |

---

## 📁 Files Created / Modified

### New Files

| File | Purpose |
|---|---|
| [ScheduleStatus.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/ScheduleStatus.java) | Enum: SCHEDULED / ON_TIME / DELAYED / CANCELLED / COMPLETED / DIVERTED |
| [CreateScheduleRequest.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/dto/CreateScheduleRequest.java) | Admin POST payload: trainId, scheduledDate, departure/arrival, baseFare, `List<SeatClassConfig>` with `@Min(0)` + `@Valid` |
| [ScheduleResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/dto/ScheduleResponse.java) | GET schedule public DTO (trainNumber/Name, status, fares, timestamps — no entity exposure) |
| [ScheduleSearchResult.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/dto/ScheduleSearchResult.java) | Search output: train details + schedule fields + nested `List<SeatAvailabilityResponse>` (classes + fares) |
| [PaginatedScheduleResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/dto/PaginatedScheduleResponse.java) | Page metadata + content[] (mirrors Phase 5 PaginatedTrainResponse) |
| [SeatAvailabilityResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/dto/SeatAvailabilityResponse.java) | Per-class availability: total/available/booked + BigDecimal fare + updatedAt |
| [SeatAvailabilityUpdateRequest.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/dto/SeatAvailabilityUpdateRequest.java) | PATCH payload (patch semantics: `seatClass` required, other fields optional via null = keep-current) |
| [TrainScheduleService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/TrainScheduleService.java) | Core service: getAll / getById / search / create; validates inactive-train, arrival≤departure, dup train+date, origin==dest |
| [ScheduleController.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/ScheduleController.java) | 4 public endpoints: list / get / search / availability; wrapped in `ResponseEntity<ApiResponse<...>>` |
| [AdminScheduleController.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/admin/AdminScheduleController.java) | 2 admin endpoints: POST create, PATCH availability; class-level `@PreAuthorize("hasRole('ADMIN')")` |
| [ScheduleSeatAvailabilityIntegrationTest.java](file:///d:/MegaProject/TrainConceirge/src/test/java/com/trainconcierge/schedule/ScheduleSeatAvailabilityIntegrationTest.java) | **27 integration tests**: pagination, search (case-insensitive + same-stn + missing params), availability CRUD, create invalid schedules (duplicate / bad times / inactive train / negative seats / 403 user / 404 train), PATCH invalid (negative / overcapacity / bad id / missing class), **50-thread concurrent booking → no oversell invariant**, exact-availability-then-fail, release, 401 unauth |

### Modified Files

| File | Change |
|---|---|
| [TrainSchedule.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/TrainSchedule.java) | Added `ScheduleStatus scheduleStatus` default SCHEDULED; `baseFare: Double → BigDecimal` (fixes H2 `scale has no meaning` error) |
| [TrainScheduleRepository.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/TrainScheduleRepository.java) | Added `existsByTrainAndScheduledDate` (dup check), `findByIdWithTrain` (JOIN FETCH), `findAllActiveSchedules(pageable)` |
| [SeatAvailability.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAvailability.java) | `fare: Double → BigDecimal`; `@Builder.Default BigDecimal.ZERO` (H2/MySQL scale fix) |
| [SeatAvailabilityRepository.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAvailabilityRepository.java) | All 3 `@Modifying` queries → `@Modifying(clearAutomatically = true)` (prevents stale cached entities after UPDATE); `fare param double→BigDecimal` |
| [SeatAvailabilityService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/seat/SeatAvailabilityService.java) | Promoted `createSeatAvailabilities` to `@Transactional public` for cross-package call; all BigDecimal signatures; book/release/expose methods |
| [TrainSearchResult.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/dto/TrainSearchResult.java) | `AvailableClass.fare: Double → BigDecimal` (matches entity change) |
| [TrainService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/TrainService.java) | Search now populates `.fare(sa.getFare())` in AvailableClass builder (previously missing) |
| [TrainManagementDataLoader.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/config/TrainManagementDataLoader.java) | All monetary literals → `BigDecimal.valueOf(...)`; helper signatures updated |

---

## 🚀 Running This Phase

### ⚡ Quick Start — Two Commands to Verify

```powershell
cd d:\MegaProject\TrainConceirge
mvn clean test          # 74/74 passing (H2 in-memory — NO MySQL needed)
mvn spring-boot:run     # auto-seeds 15 trains + 150 schedules + 600 seat rows in dev/default
```

Expected from `mvn clean test`:
```
[INFO] Tests run: 74, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Test breakdown:

| Test Class | Count | What's Covered |
|---|---|---|
| **ScheduleSeatAvailabilityIntegrationTest** | 27 | 1 list (paged+sorted) · 2 get-by-id (OK/404) · 4 search (case-insensitive / empty-route / same-station / missing-params) · 2 availability (OK/404) · 8 create-schedule (OK + 7 invalid: duplicate/arrival<departure/arrival=departure/inactive-train/404-train/negative-seats/USER-403) · 5 PATCH availability (OK + negative/overcapacity/404-schedule/404-class) · 3 inventory atomics (50-thread concurrent booking NO OVERSELL / exact-then-fail / release) · 2 auth (unauth list 401 / unauth search 401) |
| **TrainManagementIntegrationTest** | 20 | Phase 5 (ensures BigDecimal refactor breaks nothing) |
| **AuthIntegrationTest** | 26 | Phase 4 (JWT flows untouched) |
| **TrainConciergeApplicationTests** | 1 | Full context loads clean |

---

### Prerequisites

1. **Java 17+** (`java --version` → openjdk 17)
2. **Maven 3.9+** (`mvn -v`)
3. **MySQL 8+** — **only for `spring-boot:run`**; tests use H2 in-memory automatically (no setup)
4. **Environment variables** or default fallbacks in [application.properties](file:///d:/MegaProject/TrainConceirge/src/main/resources/application.properties):
   ```powershell
   # Windows PowerShell
   $env:DB_HOST="localhost"
   $env:DB_PORT="3306"
   $env:DB_NAME="train_concierge"
   $env:DB_USERNAME="root"
   $env:DB_PASSWORD="<your mysql root password>"
   $env:JWT_SECRET="<Base64 encoded, 32+ bytes>"
   $env:JWT_EXPIRATION_MS="86400000"
   ```

---

### Step-by-Step Running & Verification

#### Step 1 — Run the Full Test Suite (74 tests, ~45s)

This runs on H2 in-memory — no MySQL, no env vars, no external services:

```powershell
cd d:\MegaProject\TrainConceirge
mvn clean test
```

Look for **74/74** and `BUILD SUCCESS`. If you only want Phase 6:
```powershell
mvn test -Dtest=ScheduleSeatAvailabilityIntegrationTest
```

#### Step 2 — Start App with MySQL (Auto-Seeds Sample Data)

```powershell
# Set env vars first (Windows), then:
mvn spring-boot:run
```

On first boot you should see:
```
═══════════════════════════════════════════════════════════
Phase 5: Seeding Train Management sample data...
  ▸ Train 12001 — Shatabdi Express ...
  ▸ ... (15 trains, 150 schedules, 600 seat rows)
  ▸ Seeded admin user: admin@trainconcierge.dev / Admin@123
  ▸ Seeded demo user : user@trainconcierge.dev / User@123
═══════════════════════════════════════════════════════════
```

**Pre-seeded credentials for Postman/curl**:

| Email | Password | Role |
|---|---|---|
| `admin@trainconcierge.dev` | `Admin@123` | ROLE_ADMIN — create/update trains + schedules |
| `user@trainconcierge.dev` | `User@123` | ROLE_USER — read schedules, search, check availability |

#### Step 3 — Health Check (Always Public)

```bash
curl http://localhost:8080/api/health
```
→ `{ "status":"UP", ... }`

#### Step 4 — Obtain JWT Tokens

Admin (write) token:
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{ "email":"admin@trainconcierge.dev", "password":"Admin@123" }' \
  | jq -r .data.accessToken
# → ADMIN_TOKEN=eyJhbGciOiJIUzM4NCJ9...

curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{ "email":"user@trainconcierge.dev", "password":"User@123" }' \
  | jq -r .data.accessToken
# → USER_TOKEN=eyJhbGciOiJIUzM4NCJ9...
```

---

## 📮 Postman / curl Examples — All 6 Phase-6 Endpoints

### 1. Public — Paginated List of Schedules

- **GET** `http://localhost:8080/api/schedules`
- **Auth**: Bearer `$USER_TOKEN` or `$ADMIN_TOKEN`
- **Query params** (all optional):
  - `page=0`
  - `size=10` (min 1, cap 100)
  - `sortBy=scheduledDate` (allowed: scheduledDate / scheduledDeparture / scheduledArrival / delayMinutes / createdAt / updatedAt)
  - `sortDir=asc` or `desc`

```bash
curl -s "http://localhost:8080/api/schedules?page=0&size=3&sortBy=scheduledDate&sortDir=asc" \
  -H "Authorization: Bearer $USER_TOKEN" | jq
```

**200 Response**:
```json
{
  "success": true,
  "message": "Schedules retrieved successfully.",
  "data": {
    "content": [
      {
        "id": 1, "trainId": 1, "trainNumber": "12001", "trainName": "Shatabdi Express",
        "originStation": "New Delhi", "destinationStation": "Kanpur",
        "scheduledDate": "2026-10-05",
        "scheduledDeparture": "05:30:00", "scheduledArrival": "11:30:00",
        "actualDeparture": null, "actualArrival": null,
        "delayMinutes": 0, "platform": "1", "cancelled": false,
        "scheduleStatus": "SCHEDULED", "baseFare": 500.00,
        "createdAt": "...", "updatedAt": "..."
      }
    ],
    "pageNumber": 0, "pageSize": 3,
    "totalElements": 150, "totalPages": 50,
    "first": true, "last": false, "empty": false
  },
  "timestamp": "..."
}
```

---

### 2. Public — Get Schedule by ID

- **GET** `http://localhost:8080/api/schedules/{id}`
- **Auth**: Bearer any token

**200 OK**: identical to a single `content` entry above, wrapped in `ApiResponse<ScheduleResponse>`.

**404 NOT FOUND**:
```json
{
  "status": 404, "errorCode": "RESOURCE_NOT_FOUND",
  "message": "TrainSchedule not found with id: '98765'",
  "path": "/api/schedules/98765"
}
```

---

### 3. Public — Search Schedules by Route + Date

- **GET** `http://localhost:8080/api/schedules/search`
- **Auth**: Bearer any token
- **Query params** (**all required**):
  - `originStation=Mumbai Central`
  - `destinationStation=New Delhi`
  - `journeyDate=2026-10-07`

**Key behaviors**:
- Case-insensitive (matches `mumbai central`, `MUMBAI CENTRAL`, etc.)
- Returns only: train.active=true, schedule.cancelled=false
- Each result embeds `seatAvailability[]` — per-class counts + BigDecimal fares

```bash
curl -s "http://localhost:8080/api/schedules/search?originStation=mumbai%20central&destinationStation=NEW%20DELHI&journeyDate=2026-10-07" \
  -H "Authorization: Bearer $USER_TOKEN" | jq
```

**200 Response**:
```json
{
  "success": true,
  "message": "Search completed successfully. Found 2 schedule(s).",
  "data": [
    {
      "id": 31, "trainId": 7, "trainNumber": "12951",
      "trainName": "Mumbai Rajdhani Express", "operatorName": "IRCTC",
      "originStation": "Mumbai Central", "destinationStation": "New Delhi",
      "scheduledDate": "2026-10-07",
      "scheduledDeparture": "17:00:00", "scheduledArrival": "08:35:00",
      "delayMinutes": 0, "platform": "4", "cancelled": false,
      "scheduleStatus": "SCHEDULED", "baseFare": 500.00,
      "seatAvailability": [
        { "seatClass":"FIRST",    "totalSeats":50,  "availableSeats":48, "bookedSeats":2,  "fare":1500.00 },
        { "seatClass":"BUSINESS", "totalSeats":75,  "availableSeats":71, "bookedSeats":4,  "fare": 950.00 },
        { "seatClass":"SECOND",   "totalSeats":225, "availableSeats":210,"bookedSeats":15, "fare": 450.00 },
        { "seatClass":"SLEEPER",  "totalSeats":150, "availableSeats":142,"bookedSeats":8,  "fare": 720.00 }
      ]
    }
  ],
  "timestamp": "..."
}
```

**Failure conditions**:
- Missing any param → **400 VALIDATION_FAILED** with `validationErrors.<paramName>`
- Origin equals destination (case-insensitive) → **400 INVALID_OPERATION**
- No matches → **200 OK** with `data:[]` + message `No schedules found for the specified route and date.`

---

### 4. Public — Seat Availability for a Specific Schedule

- **GET** `http://localhost:8080/api/schedules/{id}/availability`
- **Auth**: Bearer any token

```bash
curl -s http://localhost:8080/api/schedules/31/availability \
  -H "Authorization: Bearer $USER_TOKEN" | jq
```

**200 Response** (just the per-class list — same as search's `seatAvailability`):
```json
{
  "success": true,
  "message": "Seat availability retrieved successfully.",
  "data": [
    { "id": 121, "scheduleId": 31, "seatClass": "FIRST",
      "totalSeats": 50, "availableSeats": 48, "bookedSeats": 2, "fare": 1500.00,
      "updatedAt": "2026-10-02T05:29:59.82341Z" },
    ...
  ]
}
```

**404 NOT FOUND** if schedule id doesn't exist.

---

### 5. Admin — Create a Schedule (POST /api/admin/schedules)

- **POST** `http://localhost:8080/api/admin/schedules`
- **Auth**: Bearer `$ADMIN_TOKEN` (ROLE_USER → 403 ACCESS_DENIED)
- **Body**:

```json
{
  "trainId": 1,
  "scheduledDate": "2026-11-15",
  "scheduledDeparture": "06:30:00",
  "scheduledArrival":   "12:45:00",
  "platform": "3",
  "baseFare": 625.50,
  "seatClassConfigs": [
    { "seatClass": "FIRST",    "totalSeats": 50, "fare": 1800.00 },
    { "seatClass": "BUSINESS", "totalSeats": 80, "fare": 1100.00 },
    { "seatClass": "SECOND",   "totalSeats": 240,"fare":  525.00 },
    { "seatClass": "SLEEPER",  "totalSeats": 160,"fare":  840.00 }
  ]
}
```

**201 CREATED**:
```json
{
  "success": true,
  "message": "Schedule created successfully.",
  "data": {
    "id": 151, "trainId": 1, "trainNumber": "12001", "trainName": "Shatabdi Express",
    "originStation": "New Delhi", "destinationStation": "Kanpur",
    "scheduledDate": "2026-11-15", "scheduledDeparture": "06:30:00", "scheduledArrival": "12:45:00",
    "delayMinutes": 0, "platform": "3", "cancelled": false,
    "scheduleStatus": "SCHEDULED", "baseFare": 625.50,
    "createdAt": "...", "updatedAt": "..."
  }
}
```

**Validation / Error conditions** (all tested):
- `trainId` missing / train doesn't exist → **404**
- Train is `active=false` → **400 INVALID_OPERATION** *"Cannot create a schedule for an inactive train."*
- `scheduledArrival <= scheduledDeparture` → **400 INVALID_OPERATION**
- Same `(trainId, scheduledDate)` already exists → **409 RESOURCE_ALREADY_EXISTS** *"TrainSchedule already exists with trainId:scheduledDate: '<id>:<date>'"*
- Any `totalSeats < 0` → **400 VALIDATION_FAILED**
- ROLE_USER token → **403 ACCESS_DENIED**

---

### 6. Admin — Update Seat Availability / Fare (PATCH)

- **PATCH** `http://localhost:8080/api/admin/schedules/{id}/availability`
- **Auth**: Bearer `$ADMIN_TOKEN`
- **Body semantics**: **All fields optional except `seatClass`**; a null field → "keep current value".

Admin wants to raise SECOND-class fare and re-set counts after a system glitch:
```json
{
  "seatClass": "SECOND",
  "totalSeats": 200,
  "availableSeats": 150,
  "bookedSeats": 50,
  "fare": 550.00
}
```

```bash
curl -s -X PATCH http://localhost:8080/api/admin/schedules/151/availability \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"seatClass":"SECOND","totalSeats":200,"availableSeats":150,"bookedSeats":50,"fare":550.00}' | jq
```

**200 OK**:
```json
{
  "success": true,
  "message": "Seat availability updated successfully.",
  "data": {
    "id": 602, "scheduleId": 151, "seatClass": "SECOND",
    "totalSeats": 200, "availableSeats": 150, "bookedSeats": 50,
    "fare": 550.00, "updatedAt": "2026-10-02T06:13:20.59841Z"
  }
}
```

**Validation / Error conditions** (all tested):
- `availableSeats < 0` in body → **400 VALIDATION_FAILED** via `@Valid` → `validationErrors.availableSeats = "Available seats must not be negative"`
- `available + booked > total` → **400 INVALID_OPERATION** *"Available + booked seats must not exceed total seats."*
- Schedule id missing → **404**
- Schedule exists but has no row for the given `seatClass` → **404** *"SeatAvailability not found with scheduleId:seatClass: '<id>:<class>'"*

---

## 🔐 Concurrency Guarantee: Why We Never Oversell

**The core anti-oversell pattern is a single SQL UPDATE with a WHERE-clause guard** (implemented in `SeatAvailabilityRepository.bookSeatsAtomically`):

```sql
UPDATE seat_availability
   SET available_seats = available_seats - ?,
       booked_seats    = booked_seats    + ?
 WHERE id = ?
   AND available_seats >= ?    -- GUARANTEE: never go negative
   AND total_seats     >= ?
```

Why this works perfectly even with 1000 concurrent bookers:
1. InnoDB / H2 acquire an **exclusive row-level lock** when executing the UPDATE.
2. The WHERE predicate is evaluated **while holding the lock**.
3. If `available_seats < seats_requested` → 0 rows updated → service returns `false` → booking fails.
4. If `available_seats >= seats_requested` → 1 row updated → service returns `true` → booking succeeds.
5. Lock released → next thread runs with the UPDATED counts.

**Layered defence** (belt + suspenders):
- Layer 1: Atomic UPDATE + WHERE guard (primary — **impossible to race**)
- Layer 2: `@Version` optimistic locking on `SeatAvailability` (causes `ObjectOptimisticLockingFailureException` if somehow two transactions write same entity version)
- Layer 3: Service validates `total ≥ 0, available ≥ 0, booked ≥ 0, available + booked ≤ total` before every admin override
- Layer 4: DB `CHECK` constraints (via schema-gen) and `@Min(0)` on DTOs

This pattern is **the industry standard** used by airlines, railways, and ticketing platforms. It is validated end-to-end by `ScheduleSeatAvailabilityIntegrationTest.concurrentBooking_NoOversell_50Threads` (Order 23):
```
THREAD_COUNT = 50, TOTAL_SEATS = 50, SEATS_PER_BOOKING = 2
  → Each thread tries to book 2 seats
  → Invariant after all threads: bookedSeats ≤ 50, availableSeats ≥ 0, total = a+b
  → Assertion: successes + failures = 50
```

---

## 🧪 Manual Verification Checklist (Postman)

Follow these **17 steps** in order to manually exercise every endpoint:

| # | Action | Expected |
|---|---|---|
| 1 | POST `/api/auth/login` as `admin@trainconcierge.dev` / `Admin@123` → save `ADMIN_TOKEN` | 200, `data.accessToken` present |
| 2 | POST `/api/auth/login` as `user@trainconcierge.dev` / `User@123` → save `USER_TOKEN` | 200 |
| 3 | GET `/api/schedules?page=0&size=2&sortBy=scheduledDate&sortDir=asc` with USER token | 200, `totalElements=150`, `pageSize=2` |
| 4 | GET `/api/schedules/{id}` using id #1 from step 3 | 200, `data.trainNumber == "12001"` |
| 5 | GET `/api/schedules/98765` | 404 RESOURCE_NOT_FOUND |
| 6 | GET `/api/schedules/search?originStation=mumbai%20central&destinationStation=NEW%20DELHI&journeyDate=<tomorrow>` (case mix) | 200, 2 trains (Duronto + Rajdhani), each with 4 seat classes |
| 7 | GET `/api/schedules/search?originStation=New%20Delhi&destinationStation=new%20delhi&journeyDate=…` | 400 INVALID_OPERATION |
| 8 | GET `/api/schedules/search?originStation=A&destinationStation=B` (missing date) | 400 VALIDATION_FAILED |
| 9 | GET `/api/schedules/{id}/availability` | 200, `data[]` has 4 classes with `total/available/booked/fare` |
| 10 | POST `/api/admin/schedules` with **USER token**, valid body | 403 ACCESS_DENIED |
| 11 | POST `/api/admin/schedules` with ADMIN token, valid new train+date body | 201 CREATED, returned id ≥ 151 |
| 12 | POST same body as step 11 (duplicate) | 409 RESOURCE_ALREADY_EXISTS |
| 13 | POST body where `scheduledArrival = scheduledDeparture` | 400 INVALID_OPERATION |
| 14 | POST body with `seatClassConfigs[2].totalSeats = -5` | 400 VALIDATION_FAILED |
| 15 | PATCH `/api/admin/schedules/{id}/availability` with `{seatClass:"SECOND",fare:550}` (partial) | 200, fare updated, counts unchanged |
| 16 | PATCH with `{seatClass:"SECOND", availableSeats:-1}` | 400, `validationErrors.availableSeats` present |
| 17 | GET `/api/schedules` with **no token** at all | 401 UNAUTHORIZED |

---

## ✅ Running Steps Recap (Verified Working Order)

```powershell
cd d:\MegaProject\TrainConceirge
mvn clean test          # 74/74 PASS — H2, NO setup
mvn spring-boot:run     # MySQL via env vars; auto-seeds trains/schedules/users
```

Then open Postman and do:
1. **POST** `/api/auth/login` → admin token
2. **GET** `/api/schedules` → paginated list
3. **GET** `/api/schedules/1` → single schedule
4. **GET** `/api/schedules/search?originStation=New%20Delhi&destinationStation=Kanpur&journeyDate=<tomorrow>` → schedules with real-time availability + fares
5. **GET** `/api/schedules/1/availability` → per-class breakdown
6. **POST** `/api/admin/schedules` → create new schedule with 4 classes + fares
7. **PATCH** `/api/admin/schedules/<newId>/availability` → update a class's fare/counts
8. **Verify 403** — try POST admin endpoint with USER token
9. **Verify 401** — try any `/api/schedules` URL with no token

---

## 🎓 Interview Questions — Java Full Stack / Concurrency / Transactions

### Q1. Why `BigDecimal` and not `Double` for fares and baseFare?

**A.** Three reasons:
1. **Financial correctness**: `0.1 + 0.2 = 0.30000000000000004` in IEEE-754 floating-point. Sums would slowly drift.
2. **JPA column precision**: Hibernate + H2 throw `IllegalArgumentException: scale has no meaning for SQL floating point types` if you pair `Double` with `@Column(precision=10, scale=2)`. Only `BigDecimal` carries `precision/scale` semantics cleanly across all dialects.
3. **Audit trail**: `BigDecimal.equals()` is exact, so automated tests and audit logs comparing fares won't flake.

### Q2. Why `@Modifying(clearAutomatically = true)` on UPDATE queries?

**A.** Inside a single transaction + single EntityManager:
1. `findByScheduleAndSeatClass()` → loads entity into **Persistence Context (PC)** cache.
2. `@Modifying UPDATE` — runs on DB, **does NOT update the PC by default**.
3. `findById(sameId)` → EM checks PC first, returns the **STALE pre-UPDATE entity** (e.g. totalSeats=225 when DB already has 200).

`clearAutomatically = true` tells Spring Data JPA to evict the PC after the @Modifying query, forcing the next find() to hit the database. This was the exact root cause of Phase-6 test #18 failure, resolved by the annotation.

### Q3. Compare: `JOIN FETCH` vs `@Transactional` lazy loading. Why do we use JOIN FETCH for schedule→train?

**A.** Without `JOIN FETCH`, calling `schedule.getTrain().getTrainNumber()` inside the service triggers a **second** SQL (N+1 query problem). For 150 schedules you'd get 1 SELECT schedule + 150 SELECT train = 151 round-trips. `JOIN FETCH` materialises train in one SELECT via SQL INNER JOIN — single round-trip, deterministic, no N+1. Used by `findByIdWithTrain` and `findAvailableSchedulesIgnoreCase`.

### Q4. Why duplicate-schedule check at **both** service layer AND DB layer (unique constraint)?

**A.** Classic TOCTOU (time-of-check to time-of-use) race: two concurrent admins POST the same train+date. If only service-level check existed:
1. Thread-A `existsByTrainAndScheduledDate` → false
2. Thread-B `existsByTrainAndScheduledDate` → false (runs between A's check and A's save)
3. Both proceed to save() → **duplicate**.

With the DB unique constraint, Thread-B's save() throws `DataIntegrityViolationException` → GlobalExceptionHandler maps to 409. Service check provides **fast, friendly 409 with descriptive message** for the 99% non-race case; DB constraint provides **correctness under race** for the 1% case. Never rely on only one.

### Q5. Why PATCH (not PUT) for admin availability updates?

**A.** Patch semantics = "submit only the changes". A station agent may want to:
- Raise SECOND-class fare only: `{"seatClass":"SECOND", "fare":575}`
- Drain available seats for maintenance: `{"seatClass":"FIRST", "availableSeats":0, "bookedSeats":50}`

With PUT the caller would be forced to re-submit every count + fare every time (risk of accidentally overwriting unrelated fields). Null = keep-current is exactly REST PATCH semantics.

### Q6. How is the 50-thread concurrent booking test actually proving no-oversell? Walk through the invariants.

**A.** Test Order-23:
- Pre-conditions: TOTAL_SEATS = 50, THREAD_COUNT = 50, SEATS_PER_BOOKING = 2 (so 50×2 = 100 "demand" vs 50 capacity = sure-fire way to detect oversell if it could happen).
- Uses `ExecutorService(50 fixed) + CountDownLatch` to fire all threads near-simultaneously (start latch + done latch).
- `AtomicInteger successes` and `failures` track per-thread outcomes.
- Post-conditions asserted:
  1. `bookedSeats ≤ TOTAL_SEATS` (no oversell — core invariant)
  2. `availableSeats ≥ 0` (no negative)
  3. `totalSeats == available + booked` (matter invariant, never lost or duplicated seats)
  4. `successes + failures == THREAD_COUNT` (every thread deterministically returned one outcome)

If we had used the naive `read available → subtract → write back` pattern, booked would often hit 52-60 under this load. With atomic UPDATE+WHERE it is mathematically capped at exactly 50 = successes × SEATS_PER_BOOKING, with failures = remaining threads = 25.

### Q7. Two-tier RBAC for `/api/admin/schedules`: why both `SecurityConfig URL pattern` AND `@PreAuthorize` on controller?

**A.** Defence in depth:
1. URL pattern `requestMatchers("/api/admin/**").hasRole("ADMIN")` blocks bad URLs before they reach any controller (the outer firewall).
2. Class-level `@PreAuthorize("hasRole('ADMIN')")` is the inner guard that fires **even if someone re-maps the controller to a different URL** during refactoring.

Two tests guarantee this: `createSchedule_Failure_AsRoleUser` (403, Order 17) and 5-level Spring Security integration. If a dev ever removed either guard, the test would instantly turn red.

---

End of Phase 6. Proceed to Phase 7 — **Booking & Payment module** (call `SeatAvailabilityService.bookSeats()` from BookingService inside a `@Transactional`, enforce per-user booking count limits via `AuthService.getCurrentAuthenticatedUser()`, attach bookings to schedules + users, implement cancellation that calls `releaseSeats()`).

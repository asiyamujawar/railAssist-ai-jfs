# Phase 5 — Train Management Module

> **Status:** ✅ Complete — All **47 tests pass** (20 Train Mgmt integration + 16 Auth + 10 Exception handler + 1 context load).

---

## 📋 What's Implemented in This Phase

| # | Requirement | Status |
|---|---|---|
| 1 | Train DTOs: `CreateTrainRequest`, `UpdateTrainRequest`, `TrainResponse`, `TrainSearchResult`, `PaginatedTrainResponse` | ✅ |
| 2 | Train create, retrieval by id + list, update, and soft deactivation (active=false) | ✅ |
| 3 | Train search by source, destination, and journey date (via `TrainSchedule` join) | ✅ |
| 4 | Pagination + Sorting on GET /api/trains (page/size/sortBy/sortDir) | ✅ |
| 5 | Case-insensitive station search (`LOWER()` in JPQL) | ✅ |
| 6 | Source/destination different validation (service + create/update levels) | ✅ |
| 7 | Unique train number enforcement (`existsByTrainNumber` + id-excluding variant) | ✅ |
| 8 | Never expose entities — only DTOs (`toResponse()` mappers) | ✅ |
| 9 | Clear 404 `RESOURCE_NOT_FOUND` for missing trains | ✅ |
| 10 | Admin-only writes (`@PreAuthorize("hasRole('ADMIN')")` on `/api/admin/**` controller + global URL rule) | ✅ |
| 11 | 20 integration tests for search, pagination, invalid routes, missing records, admin gating | ✅ |
| Extra | Development-only sample data seed (15 Indian Railways trains + schedules + seat availability) | ✅ |
| Extra | Demo admin/user accounts seeded in dev | ✅ |

---

## 🏗️ Module Architecture

```
                    ┌──────────────────────────────────────────────────┐
                    │               Train Management                   │
                    └──────────────────────────────────────────────────┘

 Public Controllers (any authenticated user):
 ┌──────────────────────────────────────────────────────────────────────┐
 │ TrainController ── /api/trains                                       │
 │   GET  /                    → getAllTrains(pageable, includeInactive)│
 │   GET  /{id}                → getTrainById                           │
 │   GET  /search              → searchTrains(origin, dest, date)       │
 │   returns PaginatedTrainResponse / TrainResponse / List<SearchResult>│
 └──────────────────────────────────────────────────────────────────────┘

 Admin Controllers (ROLE_ADMIN only):
 ┌──────────────────────────────────────────────────────────────────────┐
 │ AdminTrainController ── /api/admin/trains                            │
 │   POST  /                   → createTrain                            │
 │   PUT   /{id}               → updateTrain                            │
 │   PATCH /{id}/deactivate    → deactivateTrain (soft delete)         │
 │   @PreAuthorize("hasRole('ADMIN')") — Class-level guard             │
 └──────────────────────────────────────────────────────────────────────┘

 Service Layer (business logic, validation, ownership gating):
 ┌──────────────────────────────────────────────────────────────────────┐
 │ TrainService — (Transactional, maps Entity ↔ DTO)                    │
 │   • create/update:  route-different check, unique-number check       │
 │   • search:        route-different check + past-date check           │
 │   • get:           404 if missing, case-insensitive on queries       │
 │   • deactivate:    idempotent soft-delete (active=false)             │
 └──────────────────────────────────────────────────────────────────────┘

 Persistence Layer:
 ┌──────────────────────────────────────────────────────────────────────┐
 │ TrainRepository              │  TrainScheduleRepository              │
 │  • findAll pageable          │  • findAvailableSchedulesIgnoreCase   │
 │  • findAllByActiveTrue       │  (JOIN FETCH train WHERE t.active)    │
 │  • findActiveByRouteIgnoreCase                                         │
 │  • existsByTrainNumber + excludeId                                      │
 └──────────────────────────────────────────────────────────────────────┘
```

### Endpoint Matrix

| Method | Path | Auth | Role | Purpose |
|---|---|---|---|---|
| **GET** | `/api/trains` | Bearer | Any | Paginated train master list |
| **GET** | `/api/trains/{id}` | Bearer | Any | Single train by id |
| **GET** | `/api/trains/search` | Bearer | Any | Search by origin/destination/date with real-time classes |
| **POST** | `/api/admin/trains` | Bearer | **ADMIN** | Create new train master record |
| **PUT** | `/api/admin/trains/{id}` | Bearer | **ADMIN** | Update train (partial fields OK) |
| **PATCH** | `/api/admin/trains/{id}/deactivate` | Bearer | **ADMIN** | Soft-deactivate (active=false) |

⚠️ **Endpoints NOT implemented by design** (not requested):
- No `DELETE /api/admin/trains/{id}` — use PATCH deactivate (soft delete) for referential integrity.
- No refresh-token flows — Phase 4 deliberately uses single access-token design (per phase requirements).

---

## 📁 Files Created / Modified

### New Files

| File | Purpose |
|---|---|
| [dto/CreateTrainRequest.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/dto/CreateTrainRequest.java) | Bean-validated admin create DTO |
| [dto/UpdateTrainRequest.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/dto/UpdateTrainRequest.java) | Partial-update admin DTO (all fields optional) |
| [dto/TrainResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/dto/TrainResponse.java) | Single-train public DTO (no entity exposure) |
| [dto/PaginatedTrainResponse.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/dto/PaginatedTrainResponse.java) | Paginated list metadata + content |
| [dto/TrainSearchResult.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/dto/TrainSearchResult.java) | Schedule-aware search output (departure, classes, fares, delays) |
| [TrainService.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/TrainService.java) | Core business logic |
| [TrainController.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/TrainController.java) | Public `/api/trains/*` endpoints |
| [AdminTrainController.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/admin/AdminTrainController.java) | Admin write endpoints at `/api/admin/trains/*` |
| [TrainManagementDataLoader.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/config/TrainManagementDataLoader.java) | Dev sample data: 15 trains + 150 schedules + 600 seat-availability rows (only profile=dev/default) |
| [TrainManagementIntegrationTest.java](file:///d:/MegaProject/TrainConceirge/src/test/java/com/trainconcierge/train/TrainManagementIntegrationTest.java) | 20 integration tests |

### Modified Files

| File | Change |
|---|---|
| [TrainRepository.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/train/TrainRepository.java) | Added pageable findAll / findAllByActiveTrue / findActiveByRouteIgnoreCase / searchByStationContainingIgnoreCase / existsByTrainNumberAndIdNot |
| [TrainScheduleRepository.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/schedule/TrainScheduleRepository.java) | Added `findAvailableSchedulesIgnoreCase` with JOIN FETCH + `LOWER()` |
| [GlobalExceptionHandler.java](file:///d:/MegaProject/TrainConceirge/src/main/java/com/trainconcierge/exception/GlobalExceptionHandler.java) | Added handler for `MissingServletRequestParameterException` (400 with VALIDATION_FAILED) |
| [application.properties (test)](file:///d:/MegaProject/TrainConceirge/src/test/resources/application.properties) | Added `spring.profiles.active=test` so `@Profile("dev","default")` DataLoader does not seed tests |

---

## 🚀 Running This Phase

### ⚡ Quick Start — Three Commands to Verify & Run

```bash
cd d:\MegaProject\TrainConceirge
mvn clean test          # 47/47 passing (47 tests total)
mvn spring-boot:run     # seeds sample trains automatically in dev/default profile
```

Expected from `mvn clean test`:
```
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

### Prerequisites

1. **Java 17+**
2. **Maven 3.9+**
3. **MySQL 8+** (for `spring-boot:run`; H2 in-memory is used by tests automatically)
4. `.env` configured with at least:
   ```bash
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=train_concierge
   DB_USERNAME=root
   DB_PASSWORD=<your mysql password>
   DDL_AUTO=update
   JWT_SECRET=<base64 32+ bytes>
   JWT_EXPIRATION_MS=86400000
   ```

### Step-by-Step Running & Verification

#### Step 1 — Build and run the full test suite (47 tests, H2 in-memory)

```bash
cd d:\MegaProject\TrainConceirge
mvn clean test
```

Test class breakdown:

| Test Class | Count | What's covered |
|---|---|---|
| **TrainManagementIntegrationTest** | 20 | List (paged + sorted) | Get by id (OK + 404) | Search (case-insensitive + empty route + same station + past date + missing params) | Create (admin OK + duplicate 409 + forbidden as user + validation errors) | Update (OK + 404 + bad station change) | Deactivate (OK + forbidden as USER + 404) | List unauth returns 401 |
| **AuthIntegrationTest** | 16 | Phase 4 auth flows (ensures Phase 5 changes don't break auth) |
| **GlobalExceptionHandlerTest** | 10 | Phase 3 exception handler |
| **TrainConciergeApplicationTests** | 1 | Full context loads cleanly |

#### Step 2 — Start the application with MySQL (dev profile auto-seeds data)

```bash
mvn spring-boot:run
```

On first boot you should see:
```
═══════════════════════════════════════════════════════════
Phase 5: Seeding Train Management sample data...
  ▸ Train 12001 — Shatabdi Express (New Delhi → Kanpur)
  ▸ Train 12002 — Shatabdi Express Return (Kanpur → New Delhi)
  ▸ Train 12301 — Howrah Rajdhani (Howrah → New Delhi)
  ... (15 trains total, 150 schedules, 600 seat availability rows)
  ▸ Seeded admin user: admin@trainconcierge.dev / Admin@123
  ▸ Seeded demo user : user@trainconcierge.dev / User@123
═══════════════════════════════════════════════════════════
```

**Pre-seeded credentials for Postman**:
| Email | Password | Role |
|---|---|---|
| `admin@trainconcierge.dev` | `Admin@123` | ROLE_ADMIN (can POST/PUT/PATCH admin endpoints) |
| `user@trainconcierge.dev` | `User@123` | ROLE_USER (can read trains, search, but cannot create/update) |

#### Step 3 — Smoke Test Public Health (always public)

```bash
curl http://localhost:8080/api/health
```

#### Step 4 — Get a token via login

Login as ADMIN (write operations) or USER (read only) — use Postman or curl:

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{ "email":"admin@trainconcierge.dev", "password":"Admin@123" }' \
  | jq .data.accessToken
```

Copy the token for use as `Authorization: Bearer <token>`.

#### Step 5 — Verify all 6 Train Management Endpoints Work

Use the **Postman examples** below.

---

## 📮 Postman Examples (6 Endpoints)

### 1. Public — Paginated List of Trains

- **GET** `http://localhost:8080/api/trains`
- **Auth**: Bearer (any token; ADMIN or USER)
- **Query params** (all optional, defaults shown):
  - `page=0`
  - `size=10` (capped at 100 server-side; min 1)
  - `sortBy=trainNumber` (allowed: name/number/operator/origin/destination/seats/created/updated)
  - `sortDir=asc` (or desc)
  - `includeInactive=false` (ADMINs often set true for audit)

**Sample**: `GET /api/trains?page=0&size=5&sortBy=totalSeats&sortDir=desc&includeInactive=false`

**Response (200)**:
```json
{
  "success": true,
  "message": "Trains retrieved successfully.",
  "data": {
    "content": [
      {
        "id": 7, "trainNumber": "12625",
        "trainName": "Kerala Express",
        "operatorName": "IRCTC",
        "originStation": "Thiruvananthapuram Central",
        "destinationStation": "New Delhi",
        "totalSeats": 1000, "active": true,
        "createdAt": "...", "updatedAt": "..."
      }
    ],
    "pageNumber": 0, "pageSize": 5,
    "totalElements": 15, "totalPages": 3,
    "first": true, "last": false, "empty": false
  },
  "timestamp": "..."
}
```

**Failures**: 401 if no token.

---

### 2. Public — Get Train by ID

- **GET** `http://localhost:8080/api/trains/{id}`
- **Auth**: Bearer (any)
- **Path**: `{id}` — numeric id (from list)

**200 OK**: same as a single `content` object in a paginated list, wrapped in `ApiResponse<TrainResponse>`.

**404 Not Found**:
```json
{
  "status": 404,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Train not found with id: '99999'",
  "path": "/api/trains/99999"
}
```

---

### 3. Public — Search Trains by Route + Date

- **GET** `http://localhost:8080/api/trains/search`
- **Auth**: Bearer (any)
- **Query params** (**all required**):
  - `originStation=New Delhi`
  - `destinationStation=Agra`
  - `journeyDate=2026-10-05` (ISO format)

**Key behavior**:
- Station names are case-insensitive (`new delhi`, `New Delhi`, `NEW DELHI` — all work).
- Search returns entries from `TrainSchedule` joined with Train where `t.active=true` and `s.cancelled=false`.
- Each result includes per-class availability from `SeatAvailability` (FIRST / BUSINESS / SECOND / SLEEPER).

**Sample call**: `GET /api/trains/search?originStation=New%20Delhi&destinationStation=Kanpur&journeyDate=2026-10-10`

**Response (200)**:
```json
{
  "success": true,
  "message": "Search completed successfully. Found 1 train(s).",
  "data": [
    {
      "trainId": 1,
      "trainNumber": "12001",
      "trainName": "Shatabdi Express",
      "operatorName": "IRCTC",
      "originStation": "New Delhi",
      "destinationStation": "Kanpur",
      "totalSeats": 600,
      "journeyDate": "2026-10-10",
      "scheduledDeparture": "05:30:00",
      "scheduledArrival": "11:30:00",
      "delayMinutes": 0,
      "platform": "1",
      "cancelled": false,
      "availableClasses": [
        { "seatClass": "FIRST",    "availableSeats": 50 },
        { "seatClass": "BUSINESS", "availableSeats": 74 },
        { "seatClass": "SECOND",   "availableSeats": 210 },
        { "seatClass": "SLEEPER",  "availableSeats": 175 }
      ]
    }
  ],
  "timestamp": "..."
}
```

**Failure cases** (covered by tests):
- Missing `destinationStation` param → **400 VALIDATION_FAILED** with `validationErrors.destinationStation`
- Origin = Destination → **400 INVALID_OPERATION**
- Journey date is yesterday or earlier → **400 INVALID_OPERATION**
- Route has no service → **200 OK with `data:[]`** and a helpful message: `No trains found for the specified route and date.`

---

### 4. Admin — Create Train Master Record

- **POST** `http://localhost:8080/api/admin/trains`
- **Auth**: Bearer **ROLE_ADMIN** token (use `admin@trainconcierge.dev` / `Admin@123`)
- **Body**:
```json
{
  "trainNumber": "SP999",
  "trainName": "Special Express 999",
  "operatorName": "IRCTC",
  "originStation": "Pune",
  "destinationStation": "Secunderabad",
  "totalSeats": 550
}
```

**201 Created**:
```json
{
  "success": true,
  "message": "Train created successfully.",
  "data": {
    "id": 16, "trainNumber": "SP999",
    "trainName": "Special Express 999",
    "operatorName": "IRCTC",
    "originStation": "Pune",
    "destinationStation": "Secunderabad",
    "totalSeats": 550, "active": true,
    "createdAt": "...", "updatedAt": "..."
  }
}
```

**409 Conflict** if you POST the same body a second time:
```json
{
  "status": 409,
  "errorCode": "RESOURCE_ALREADY_EXISTS",
  "message": "Train already exists with trainNumber: 'SP999'"
}
```

**403 Forbidden** when using a ROLE_USER token:
```json
{
  "status": 403,
  "errorCode": "ACCESS_DENIED",
  "message": "You do not have permission to perform this action.",
  "path": "/api/admin/trains"
}
```

---

### 5. Admin — Update Train (partial field update)

- **PUT** `http://localhost:8080/api/admin/trains/{id}`
- **Auth**: Bearer ROLE_ADMIN
- **Body** (only set the fields you want to change; any combination permitted):
```json
{
  "trainName": "Shatabdi Express — Premium Service",
  "totalSeats": 650,
  "operatorName": "Indian Railways"
}
```

**200 OK**: returns the merged `TrainResponse`.

**Validation rules** at update time:
- If changing origin OR destination, the resulting pair must still be different (400 INVALID_OPERATION otherwise).
- Active flag can be set via `UpdateTrainRequest.active` (or use `/deactivate` endpoint for semantic clarity).

---

### 6. Admin — Soft Deactivate Train

- **PATCH** `http://localhost:8080/api/admin/trains/{id}/deactivate`
- **Auth**: Bearer ROLE_ADMIN
- **Body**: *(none)*

**Behavior vs DELETE**:
- Soft-delete only: `active` flag is set to `false`.
- Entity still reachable via `GET /api/trains/{id}` (shows active=false) — important for historical bookings that reference the train.
- Entity **disappears** from default list view (includeInactive=false) and from all search results (because the schedule search query only includes `WHERE t.active = true`).
- Idempotent: calling twice returns 200 both times (second call is a no-op with a DEBUG log line).

**200 OK**:
```json
{
  "success": true,
  "message": "Train deactivated successfully.",
  "data": { "active": false, "...": "..." }
}
```

---

## 🧪 Testing the 6 Endpoints — Manual Postman Checklist

| # | Action | Expected Result |
|---|---|---|
| 1 | Login as `admin@trainconcierge.dev` → store token | 200 OK |
| 2 | Login as `user@trainconcierge.dev` → store token | 200 OK |
| 3 | GET `/api/trains` with user token, page=0 size=2 | 2 items, pageNumber=0, totalElements=15 |
| 4 | GET `/api/trains/{id}` with a train id from list | 200 OK with entity fields |
| 5 | GET `/api/trains/99999` | 404 RESOURCE_NOT_FOUND |
| 6 | GET `/api/trains/search?originStation=mumbai%20central&destinationStation=NEW%20DELHI&journeyDate=YYYY-MM-DD` | 200 with 2 trains (Duronto + Rajdhani) |
| 7 | GET `/api/trains/search?originStation=New%20Delhi&destinationStation=new%20delhi&journeyDate=…` | 400 INVALID_OPERATION |
| 8 | GET `/api/trains/search?originStation=A&destinationStation=B` (missing date) | 400 VALIDATION_FAILED |
| 9 | POST `/api/admin/trains` with **USER token**, valid body | 403 ACCESS_DENIED |
| 10 | POST `/api/admin/trains` with **ADMIN token**, valid body | 201 Created |
| 11 | POST same body again (duplicate number) | 409 RESOURCE_ALREADY_EXISTS |
| 12 | POST body where origin==destination | 400 INVALID_OPERATION |
| 13 | PUT `/api/admin/trains/{id}` → change totalSeats to 500 | 200 OK with new totalSeats |
| 14 | PATCH `/api/admin/trains/{id}/deactivate` | 200 OK, `data.active` = false |
| 15 | GET `/api/trains` → `includeInactive=false` → deactivated row hidden | ✔ |
| 16 | GET `/api/trains?includeInactive=true` → deactivated row now reappears | ✔ |

---

## ✅ Running Steps Recap (Verified Working Order)

```bash
cd d:\MegaProject\TrainConceirge
mvn clean test          # 47/47 passing — H2 in-memory
mvn spring-boot:run     # seeds 15 trains + schedules + demo users
```

Then open Postman:
1. **POST** `/api/auth/login` → obtain token for `admin@trainconcierge.dev` / `Admin@123`
2. **GET** `/api/trains` → paginated list
3. **GET** `/api/trains/1` → single train
4. **GET** `/api/trains/search?originStation=New%20Delhi&destinationStation=Kanpur&journeyDate=<tomorrow>` → search
5. **POST** `/api/admin/trains` → create a new train master
6. **PUT** `/api/admin/trains/16` → update it
7. **PATCH** `/api/admin/trains/16/deactivate` → soft-delete

---

## 🎓 Interview Questions (Java Full Stack — Train Mgmt Focus)

### Q1. Why not expose Train / TrainSchedule entities directly from controllers?
A. Two critical reasons:
- **Decoupling**: Database schema (e.g., `passwordHash` on User) and the JSON contract become independent. If you add a new `internalNote` column to Train — it won't leak unless the DTO also adds it.
- **DTO Projection & Aggregation**: A real `TrainSearchResult` needs data from 3 tables (Train + TrainSchedule + SeatAvailability). An entity simply cannot represent that shape cleanly.

### Q2. Why use PATCH for deactivate, not DELETE?
A. Because trains are referenced by foreign keys in `TrainSchedule`, `SeatAvailability`, future `Booking`, etc. Hard-deleting a train would cascade and destroy historical data and BI analytics. Soft-delete (active=false) preserves referential integrity while hiding the train from user-facing list/search.

### Q3. When you need to enforce route-unique + case-insensitive + date check — do these validations go in `@Valid` or service layer?
A. Bean Validation (`@NotBlank`, `@Min`) handles **static per-field rules**. Business rules like **origin != destination**, **past-date check**, **DB uniqueness check** all live in the service layer because they require access to the database or cross-field comparisons.

### Q4. How does `findAvailableSchedulesIgnoreCase` work, and why `JOIN FETCH`?
A. It runs a JPQL `SELECT s FROM TrainSchedule s JOIN FETCH s.train t WHERE LOWER(t.originStation)=LOWER(:src) AND LOWER(t.destinationStation)=LOWER(:dst) AND s.scheduledDate=:date AND t.active AND NOT s.cancelled`. `JOIN FETCH` loads Train eagerly in one SQL statement — avoiding the N+1 problem (for each schedule, a separate query to load train details).

### Q5. How to prove that `@PreAuthorize("hasRole('ADMIN')")` on the AdminTrainController actually works?
A. Our **20 integration tests include 3 explicit gating tests**:
- Order 11: `createTrain_Failure_AsRoleUser` asserts **403 ACCESS_DENIED**
- Order 18: `deactivateTrain_Failure_AsRoleUser` asserts 403
- Order 20: `listTrains_Failure_NoAuth` asserts 401

If a dev accidentally removed the `@PreAuthorize`, these 3 tests would turn red instantly.

---

End of Phase 5. Proceed to Phase 6 (Booking module, where `AuthService.getCurrentAuthenticatedUser()` combines with `TrainService` to implement per-user booking ownership checks).

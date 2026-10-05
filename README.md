# TrainConcierge — AI-Powered Autonomous Train Disruption Concierge & Smart Seat Alert System

> **Simulated backend** — all train-status changes, hotel reservations, and cab bookings are stored in the database but no real external provider is ever contacted.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Environment Setup](#4-environment-setup)
5. [Running the Application](#5-running-the-application)
6. [API Reference — All Endpoints](#6-api-reference--all-endpoints)
7. [Authentication & RBAC](#7-authentication--rbac)
8. [Database Schema Summary](#8-database-schema-summary)
9. [Swagger UI & OpenAPI](#9-swagger-ui--openapi)
10. [Postman Collection](#10-postman-collection)
11. [Running Tests](#11-running-tests)
12. [Demo Scenario — Full Disruption Workflow](#12-demo-scenario--full-disruption-workflow)
13. [Phase Changelog](#13-phase-changelog)

---

## 1. Project Overview

TrainConcierge is a Spring Boot 3.2 backend that automatically detects train disruptions, recommends alternative trains, rebooks passengers, coordinates hotel and cab rescheduling, and notifies passengers — all without human intervention.

Key capabilities:

| Capability | Implementation |
|---|---|
| Real-time monitoring | `TrainMonitoringScheduler` polls simulated train status on a configurable interval |
| Disruption detection | `DisruptionDetectionService` compares live vs. last-known status, creates `DisruptionEvent` |
| Recommendation engine | `RecommendationService` ranks alternatives by departure delta, price, seat availability |
| Autonomous rebooking | `RebookingService` supports both autonomous and passenger-triggered rebooking |
| Travel coordination | `TravelCoordinationService` reschedules linked hotel + cab after a rebooking |
| Seat alert system | `SeatAlertSubscriptionService` notifies users when seat availability crosses a threshold |
| Journey audit timeline | `JourneyTimelineService` provides a unified chronological view of all events for a journey |
| In-app notifications | `NotificationService` delivers structured in-app messages for all key events |
| Admin simulation control | `AdminSimulationControlController` exposes Phase 19 endpoints for triggering disruptions, monitoring cycles, and disruption evaluation |

---

## 2. Architecture

```
src/main/java/com/trainconcierge/
├── auth/           — JWT authentication, Spring Security configuration
├── admin/          — Admin-only train & schedule management
│   └── simulation/ — Phase 19 high-level simulation control
├── booking/        — Train seat booking with optimistic locking
├── cab/            — Simulated cab reservations
├── common/         — ApiResponse wrapper, ErrorResponse, global exception handler
├── config/         — OpenAPI, JPA, Web, DataLoader configs
├── coordination/   — Hotel + cab coordination post-rebooking
├── disruption/     — Disruption event detection, storage, and query
├── exception/      — GlobalExceptionHandler, ResourceNotFoundException, BusinessRuleException
├── hotel/          — Simulated hotel reservations
├── journey/        — Journey entity and lifecycle
├── monitoring/     — Scheduled train status monitoring
├── notification/   — In-app notification delivery
├── rebooking/      — Rebooking execution and history audit
├── recommendation/ — Alternative train ranking algorithm
├── schedule/       — Train schedule management
├── seat/           — Seat availability (optimistic locking with @Version) + smart alerts
├── simulation/     — Simulated train status provider (mock)
├── timeline/       — Unified journey audit timeline
├── train/          — Train master data
└── user/           — User entity
```

---

## 3. Technology Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.4 |
| Security | Spring Security + JJWT 0.12.5 (JWT Bearer) |
| Persistence | Spring Data JPA + Hibernate |
| Database (prod) | MySQL 8 |
| Database (tests) | H2 in-memory |
| API Documentation | Springdoc OpenAPI 2.5.0 + Swagger UI |
| Validation | Jakarta Bean Validation |
| Build tool | Maven |
| Code generation | Lombok |
| Concurrency | Optimistic locking (`@Version` on `SeatAvailability`) |

---

## 4. Environment Setup

### 4.1 Prerequisites

- Java 17+
- Maven 3.9+
- MySQL 8 (or Docker)

### 4.2 Create the database

```sql
CREATE DATABASE trainconcierge_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tc_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON trainconcierge_db.* TO 'tc_user'@'localhost';
FLUSH PRIVILEGES;
```

### 4.3 Configure environment variables

Copy `.env.example` to `.env` and fill in the values:

```bash
cp .env.example .env
```

Required variables:

| Variable | Example | Description |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/trainconcierge_db` | MySQL JDBC URL |
| `DB_USERNAME` | `tc_user` | MySQL user |
| `DB_PASSWORD` | `your_password` | MySQL password |
| `JWT_SECRET` | *(64+ char random string)* | HMAC-SHA256 signing key |
| `JWT_EXPIRATION_MS` | `86400000` | Token TTL in milliseconds (24 h) |
| `MONITORING_INTERVAL_MS` | `60000` | Monitoring poll interval in ms |

### 4.4 Default admin credentials (seeded by DataLoader)

| Field | Value |
|---|---|
| Email | `admin@example.com` |
| Password | `Admin@2026!` |
| Role | `ROLE_ADMIN` |

> **Change these before deploying to any non-local environment.**

---

## 5. Running the Application

There are three ways to run the TrainConcierge backend depending on your preference.

---

### Option A: Docker Compose (Recommended — Zero Manual Database Setup)

This starts both the **Spring Boot Backend** and **MySQL 8 Database** in isolated Docker containers with automated health checks and dependency management.

#### Prerequisites
- [Docker Desktop](https://docs.docker.com/get-docker/) installed and running.

#### Step 1: Copy Environment Template
```powershell
Copy-Item .env.example .env
```
*(On Linux / macOS: `cp .env.example .env`)*

#### Step 2: Build & Start Containers
```powershell
docker compose up -d --build
```

#### Step 3: Check Container Status & Logs
```powershell
# View running container health (both app and db should show 'healthy')
docker compose ps

# Follow live backend application logs
docker compose logs -f app
```
Wait until you see the log entry: `Started TrainConciergeApplication in X.XXX seconds`.

#### Step 4: Stop Containers when done
```powershell
# Stop containers (preserves MySQL database data volume)
docker compose down

# Stop containers AND delete MySQL database data volume
docker compose down -v
```

---

### Option B: Native Maven + Local MySQL Database

Use this option if you already have MySQL 8 installed natively on your host system.

#### Step 1: Create Database & Dedicated User in MySQL
Open MySQL Workbench or MySQL CLI as root and run:
```sql
CREATE DATABASE IF NOT EXISTS trainconcierge_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'tc_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON trainconcierge_db.* TO 'tc_user'@'localhost';
FLUSH PRIVILEGES;
```

#### Step 2: Configure Environment Variables
Copy `.env.example` to `.env` or set environment variables in your terminal:
```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/trainconcierge_db?useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="tc_user"
$env:DB_PASSWORD="your_password"
$env:JWT_SECRET="your_64_character_minimum_random_base64_secret_here"
```

#### Step 3: Build & Launch
```powershell
# 1. Package application skipping tests
mvn clean install -DskipTests

# 2. Start Spring Boot application
mvn spring-boot:run
```

---

### Option C: Native Maven + H2 In-Memory Database (No MySQL Required)

Use this for instant zero-dependency testing without setting up MySQL.

```powershell
# Start application using H2 profile (in-memory database)
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```

---

### Step 5: Verify the Running Backend

Once the application is running (on port `8080` by default):

1. **Custom API Health Check**:
   ```powershell
   curl http://localhost:8080/api/health
   ```
   *Expected Response:* `{"status":"UP","message":"TrainConcierge Backend API is operational", ...}`

2. **Spring Actuator Health**:
   ```powershell
   curl http://localhost:8080/actuator/health
   ```
   *Expected Response:* `{"status":"UP"}`

3. **Interactive Swagger UI**:
   Open browser at: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

4. **Authenticate as Admin**:
   ```powershell
   curl -X POST http://localhost:8080/api/auth/login `
     -H "Content-Type: application/json" `
     -d '{"email":"admin@example.com","password":"Admin@2026!"}'
   ```
   *Expected Response:* Returns a JSON payload with `token` (JWT Bearer token), `email`, and `role: "ROLE_ADMIN"`.

---

## 6. API Reference — All Endpoints

Base URL: `http://localhost:8080`

### Public Endpoints (no JWT required)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | Login, obtain JWT |
| `GET` | `/api/health` | Health check |
| `GET` | `/actuator/health` | Spring Actuator health |
| `GET` | `/v3/api-docs` | OpenAPI JSON |
| `GET` | `/swagger-ui/index.html` | Swagger UI |

### Authenticated User Endpoints (JWT required, any role)

#### Trains
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/auth/me` | Current user profile |
| `GET` | `/api/trains` | List all trains (paginated) |
| `GET` | `/api/trains/{id}` | Get train by ID |
| `GET` | `/api/trains/search` | Search by origin, destination, date |
| `GET` | `/api/schedules` | List all schedules (paginated) |
| `GET` | `/api/schedules/{id}` | Get schedule by ID |
| `GET` | `/api/schedules/search` | Search schedules |
| `GET` | `/api/schedules/{id}/availability` | Seat availability by class |

#### Bookings
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/bookings` | Create train booking |
| `GET` | `/api/bookings/my` | Get my bookings (paginated) |
| `GET` | `/api/bookings/{id}` | Get booking by ID |
| `PATCH` | `/api/bookings/{id}/cancel` | Cancel booking, release seats |

#### Disruptions & Recommendations
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/disruptions/my` | My disruption events |
| `GET` | `/api/disruptions/{id}` | Disruption by ID |
| `POST` | `/api/disruptions/{id}/recommendations` | Generate ranked alternatives |
| `GET` | `/api/disruptions/{id}/recommendations` | Get existing recommendations |
| `POST` | `/api/disruptions/{id}/rebook` | Rebook onto alternative train |

#### Journeys
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/journeys/{id}/rebooking-history` | Rebooking audit history |
| `POST` | `/api/journeys/{id}/coordination/trigger` | Trigger hotel+cab coordination |
| `GET` | `/api/journeys/{id}/coordination` | Coordination history |
| `GET` | `/api/journeys/{id}/timeline` | Unified journey timeline |

#### Hotels & Cabs (Simulated)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/hotels` | Create hotel booking [SIMULATED] |
| `GET` | `/api/hotels/journey/{journeyId}` | Hotels for journey |
| `PATCH` | `/api/hotels/{id}/reschedule` | Reschedule hotel [SIMULATED] |
| `POST` | `/api/cabs` | Create cab booking [SIMULATED] |
| `GET` | `/api/cabs/journey/{journeyId}` | Cabs for journey |
| `PATCH` | `/api/cabs/{id}/reschedule` | Reschedule cab [SIMULATED] |

#### Seat Alerts
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/seat-alerts` | Subscribe to seat alert |
| `GET` | `/api/seat-alerts/my` | My alert subscriptions |
| `PATCH` | `/api/seat-alerts/{id}/deactivate` | Deactivate alert |

#### Notifications
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/notifications/my` | My notifications (paginated) |
| `GET` | `/api/notifications/unread-count` | Unread count |
| `PATCH` | `/api/notifications/{id}/read` | Mark single notification as read |
| `PATCH` | `/api/notifications/read-all` | Mark all as read |

#### Simulation (read-only)
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/simulation/trains/{id}/status` | Latest simulated status [SIMULATED] |
| `GET` | `/api/simulation/trains/{id}/history` | Status history [SIMULATED] |

#### Travel Coordination
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/coordination/{id}/retry` | Retry failed coordination |

### Admin-Only Endpoints (`ROLE_ADMIN` required)

#### Train & Schedule Management
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/admin/trains` | Create train |
| `PUT` | `/api/admin/trains/{id}` | Update train |
| `PATCH` | `/api/admin/trains/{id}/deactivate` | Deactivate train |
| `POST` | `/api/admin/schedules` | Create schedule |
| `PATCH` | `/api/admin/schedules/{id}/availability` | Update seat availability |

#### Simulation Status (Low-level)
| Method | Path | Description |
|---|---|---|
| `PUT` | `/api/admin/simulation/trains/{id}/status` | Direct status update [SIMULATED] |

#### Simulation Control (High-level — Phase 19)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/admin/simulation/control/trigger-delay` | Simulate delay [SIMULATED] |
| `POST` | `/api/admin/simulation/control/trigger-cancellation` | Simulate cancellation [SIMULATED] |
| `POST` | `/api/admin/simulation/control/restore-normal` | Reset to ON_TIME [SIMULATED] |
| `POST` | `/api/admin/simulation/control/monitoring-cycle` | Trigger monitoring cycle |
| `DELETE` | `/api/admin/simulation/control/monitoring-cache` | Clear monitoring cache |
| `POST` | `/api/admin/simulation/control/disruption-evaluation` | Force disruption evaluation |

---

## 7. Authentication & RBAC

### Token lifecycle

1. Register (`POST /api/auth/register`) or login (`POST /api/auth/login`)
2. Copy the `token` from the response
3. Include `Authorization: Bearer <token>` on all protected requests

### Role model

| Role | URL pattern | Additional guard |
|---|---|---|
| `ROLE_USER` | Any authenticated endpoint | — |
| `ROLE_ADMIN` | `/api/admin/**` | `@PreAuthorize("hasRole('ADMIN')")` |

Error responses:
- **401 Unauthorized** — missing or invalid/expired JWT
- **403 Forbidden** — valid JWT but insufficient role

### Ownership checks

All user-facing data endpoints (bookings, disruptions, rebookings, notifications, etc.) enforce that the requesting user owns the resource. Cross-user access returns `403 Forbidden`.

---

## 8. Database Schema Summary

| Table | Key columns | Notes |
|---|---|---|
| `users` | `id`, `email`, `role` | BCrypt-hashed password |
| `trains` | `id`, `train_number`, `active` | Master train data |
| `train_schedules` | `id`, `train_id`, `schedule_status`, `delay_minutes`, `cancelled` | Current live schedule |
| `seat_availability` | `id`, `schedule_id`, `seat_class`, `available_seats`, `version` | Optimistic locking via `@Version` |
| `bookings` | `id`, `journey_id`, `booking_reference`, `status` | Status: CONFIRMED, CANCELLED, REBOOKED |
| `journeys` | `id`, `user_id`, `status` | Status: ACTIVE, COMPLETED, CANCELLED |
| `disruption_events` | `id`, `journey_id`, `type`, `severity`, `status` | Status: DETECTED, IN_PROGRESS, RESOLVED, FAILED |
| `train_alternatives` | `id`, `disruption_event_id`, `score` | Ranked alternatives |
| `rebooking_history` | `id`, `journey_id`, `old_booking_id`, `new_booking_id`, `autonomous` | Full rebooking audit |
| `travel_coordination_records` | `id`, `rebooking_history_id`, `hotel_status`, `cab_status` | Coordination status |
| `hotel_bookings` | `id`, `journey_id`, `check_in_date`, `check_out_date` | Simulated |
| `cab_bookings` | `id`, `journey_id`, `scheduled_pickup_time` | Simulated |
| `seat_alert_subscriptions` | `id`, `user_id`, `schedule_id`, `seat_class`, `threshold`, `active` | Alert config |
| `notifications` | `id`, `user_id`, `type`, `read`, `payload` | In-app notifications |
| `train_status_history` | `id`, `schedule_id`, `status`, `delay_minutes`, `simulated` | Full status audit trail |

---

## 9. Swagger UI & OpenAPI

When the application is running:

- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **OpenAPI YAML**: [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml)

Both the Swagger UI and the `/v3/api-docs*` endpoints are public (no JWT required) so they can be accessed directly from a browser or imported into Postman.

### Authenticating in Swagger UI

1. Call `POST /api/auth/login` from within Swagger UI
2. Copy the `token` from the response
3. Click **Authorize** (top right), paste `Bearer <token>`, click **Authorize**
4. All subsequent requests will include the JWT automatically

---

## 10. Postman Collection

A complete Postman collection is included at the root of the repository:

**File:** [`TrainConcierge_Postman_Collection.json`](TrainConcierge_Postman_Collection.json)

### Import instructions

1. Open Postman → **Import** → drag in the JSON file
2. Create a Postman Environment with these variables:

| Variable | Initial value | Description |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Application base URL |
| `adminToken` | *(filled automatically)* | JWT for admin user |
| `userToken` | *(filled automatically)* | JWT for regular user |
| `scheduleId` | *(fill after creating schedule)* | Target schedule ID |
| `journeyId` | *(filled automatically by booking)* | Target journey ID |
| `disruptionId` | *(filled automatically)* | Target disruption event ID |

### Collection structure (19 folders, 40 requests)

| Folder | Requests |
|---|---|
| 01 — Authentication | Register, Login (User), Login (Admin), Get Profile |
| 02 — Trains | List, Get by ID, Search |
| 03 — Schedules | List, Get by ID, Search, Get Availability |
| 04 — Bookings | Create, My Bookings, Get by ID, Cancel |
| 05 — Disruptions | My Disruptions, Get by ID |
| 06 — Recommendations | Generate, Get Existing |
| 07 — Rebooking | Rebook, Rebooking History |
| 08 — Travel Coordination | Trigger, Retry, History |
| 09 — Hotel Bookings | Create, Get for Journey, Reschedule |
| 10 — Cab Bookings | Create, Get for Journey, Reschedule |
| 11 — Seat Alerts | Subscribe, My Alerts, Deactivate |
| 12 — Notifications | My Notifications, Unread Count, Mark Read, Mark All Read |
| 13 — Journey Timeline | Get Timeline |
| 14 — Simulation Status | Get Latest Status, Get History |
| 15 — Admin Trains | Create, Update, Deactivate |
| 16 — Admin Schedules | Create, Update Availability |
| 17 — Admin Simulation Status | Low-level Status Update |
| 18 — Admin Simulation Control | 8-step full disruption demo workflow |
| 19 — Health | Health, Actuator Health |

---

## 11. Running Tests

```bash
# Run all tests (uses H2, no MySQL required)
mvn test

# Run a specific test class
mvn test -Dtest=AdminSimulationControlIntegrationTest
mvn test -Dtest=FullBackendIntegrationReviewTest

# Run with verbose output
mvn test -pl . -Dsurefire.useFile=false
```

### Test suite summary

| Test class | Tests | What it covers |
|---|---|---|
| `AdminSimulationControlIntegrationTest` | 24 | RBAC, delay/cancel/restore, monitoring cycle idempotency, duplicate disruption guard, edge cases |
| `FullBackendIntegrationReviewTest` | ~12 | E2E disruption workflow, no-alternative scenario, partial hotel/cab failure resilience, concurrent seat booking |

All tests use H2 in-memory with `spring.jpa.hibernate.ddl-auto=create-drop`. MySQL is only needed for the production profile.

---

## 12. Demo Scenario — Full Disruption Workflow

> Use the Postman collection folder **18 — Admin — Simulation Control** for this. Each step builds on the previous.

### Prerequisites

- Application running on port 8080
- Postman environment loaded with `baseUrl`, `adminToken`, `userToken`
- A schedule exists and `scheduleId` is set
- A confirmed booking exists for the user and `journeyId` is set

### Steps

| Step | Request | Expected result |
|---|---|---|
| 1 | `DELETE monitoring-cache` | Cache cleared |
| 2 | `POST monitoring-cycle` | `changesDetected = 0` (first observation) |
| 3 | `POST trigger-delay` | `status = DELAYED`, `delayMinutes = 120` |
| 4 | `POST monitoring-cycle` | `changesDetected = 1` — disruption pipeline fires |
| 5 | `POST disruption-evaluation` | `skipped = false`, `disruptionEventId` set, `recommendationTriggered = true` |
| 6 | `GET /api/disruptions/my` (user) | Disruption event visible |
| 7 | `POST disruption-evaluation` (again) | `skipped = true` — duplicate guard prevented second event |
| 8 | `POST /api/disruptions/{id}/recommendations` (user) | Ranked alternatives returned (or empty if no alternates exist) |
| 9 | `POST /api/disruptions/{id}/rebook` (user) | Rebooking confirmed, journey updated |
| 10 | `POST coordination/trigger` (user) | Hotel + cab rescheduled |
| 11 | `GET /api/journeys/{id}/timeline` (user) | Full chronological audit: booking → disruption → rebook → coordination → notifications |
| 12 | `POST restore-normal` (admin) | Schedule back to ON_TIME |
| 13 | `DELETE monitoring-cache` | Clean baseline for next run |

---

## 13. Phase Changelog

| Phase | Description |
|---|---|
| 1–2 | Project setup, User & Auth module (JWT) |
| 3 | Train master data CRUD |
| 4 | Train schedule management, seat availability |
| 5 | Booking engine with optimistic seat locking |
| 6 | Journey entity, booking lifecycle management |
| 7 | Simulated train status provider (mock) |
| 8 | Train monitoring scheduler |
| 9 | Disruption detection service |
| 10 | Admin simulation status control (low-level) |
| 11 | Recommendation engine (alternative train ranking) |
| 12 | Rebooking service (manual + autonomous) |
| 13 | Hotel booking module [SIMULATED] |
| 14 | Cab booking module [SIMULATED] |
| 15 | Travel coordination (hotel + cab rescheduling post-rebooking) |
| 16 | Smart seat availability alert system |
| 17 | In-app notification system |
| 18 | Journey audit timeline |
| 19 | Admin Simulation Control APIs (Phase 19): trigger-delay, trigger-cancellation, restore-normal, monitoring-cycle, monitoring-cache, disruption-evaluation. Duplicate disruption guard. Full RBAC test coverage (24 tests). |
| 20 | Backend integration review: Springdoc OpenAPI + Swagger UI integration; `@Tag`/`@Operation` annotations on all 20 controllers; Postman collection (40 requests); `FullBackendIntegrationReviewTest` E2E test suite; consolidated README. |
| 21 | Docker Deployment: Multi-stage Dockerfile, docker-compose.yml for app + MySQL 8, environment variable security, health checks, persistent volumes, CORS configuration, and production deployment guide (`README_PHASE21.md`). |

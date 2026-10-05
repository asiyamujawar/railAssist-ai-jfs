# Phase 1 — Project Foundation & Infrastructure Setup

**Project:** AI-Powered Autonomous Train Disruption Concierge & Smart Seat Alert System  
**Phase:** 1 of N  
**Status:** ✅ Complete  
**Completed On:** October 2026

---

## What Phase 1 Covers

Phase 1 establishes the complete backend foundation — the skeleton every future feature will be built on. No business logic is implemented yet. The goal is a clean, runnable, production-aware base.

---

## Objectives Achieved

| # | Objective | Status |
|---|---|---|
| 1 | Maven-based Spring Boot 3.x project created | ✅ |
| 2 | Java 17 configured as target runtime | ✅ |
| 3 | All required dependencies added to `pom.xml` | ✅ |
| 4 | MySQL 8 + Spring Data JPA + Hibernate configured | ✅ |
| 5 | Environment variable-based configuration | ✅ |
| 6 | Application running on port 8080 | ✅ |
| 7 | Global health-check endpoint (`/api/health`) | ✅ |
| 8 | Spring Boot Actuator configured | ✅ |
| 9 | Global exception handler with consistent error format | ✅ |
| 10 | Generic `ApiResponse<T>` wrapper for all endpoints | ✅ |
| 11 | JPA auditing base entity (`createdAt`, `updatedAt`) | ✅ |
| 12 | CORS configuration | ✅ |
| 13 | All domain package stubs created and documented | ✅ |
| 14 | Context load test with H2 in-memory database | ✅ |
| 15 | `.gitignore` covering Java, Maven, IntelliJ, secrets | ✅ |
| 16 | `.env.example` with placeholder values | ✅ |

---

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.4 |
| Build Tool | Maven | 3.8+ |
| Database | MySQL | 8.0.46 |
| ORM | Spring Data JPA / Hibernate | 6.4.x |
| Utilities | Lombok | Latest via BOM |
| Testing | JUnit 5 + H2 in-memory | Latest via BOM |

---

## Dependencies Added

```xml
spring-boot-starter-web          <!-- REST API layer -->
spring-boot-starter-data-jpa     <!-- JPA + Hibernate -->
spring-boot-starter-validation   <!-- Bean validation (@Valid) -->
spring-boot-starter-actuator     <!-- Health, metrics, info endpoints -->
mysql-connector-j                <!-- MySQL JDBC driver -->
lombok                           <!-- Boilerplate reduction -->
spring-boot-starter-test         <!-- JUnit 5 + Mockito -->
```

---

## Files Created

```
train-concierge/
├── pom.xml                                              ← Maven build file
├── .gitignore                                           ← Git exclusions
├── .env.example                                         ← Environment variable template
├── README.md                                            ← General setup guide
├── README_PHASE1.md                                     ← This file
│
└── src/
    ├── main/
    │   ├── java/com/trainconcierge/
    │   │   │
    │   │   ├── TrainConciergeApplication.java           ← @SpringBootApplication entry point
    │   │   │
    │   │   ├── config/
    │   │   │   ├── JpaConfig.java                       ← @EnableJpaAuditing
    │   │   │   ├── WebConfig.java                       ← CORS configuration
    │   │   │   └── HealthController.java                ← GET /api/health
    │   │   │
    │   │   ├── common/
    │   │   │   ├── ApiResponse.java                     ← Generic response wrapper
    │   │   │   └── BaseEntity.java                      ← JPA auditing base (id, createdAt, updatedAt)
    │   │   │
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java          ← @RestControllerAdvice
    │   │   │   ├── ResourceNotFoundException.java       ← HTTP 404
    │   │   │   └── BadRequestException.java             ← HTTP 400
    │   │   │
    │   │   ├── auth/package-info.java                   ← JWT auth (Phase 2)
    │   │   ├── user/package-info.java                   ← User management (Phase 2)
    │   │   ├── train/package-info.java                  ← Train data (Phase 3)
    │   │   ├── schedule/package-info.java               ← Schedules (Phase 3)
    │   │   ├── seat/package-info.java                   ← Seat alerts (Phase 4)
    │   │   ├── booking/package-info.java                ← Booking (Phase 4)
    │   │   ├── journey/package-info.java                ← Journey planning (Phase 5)
    │   │   ├── hotel/package-info.java                  ← Hotel integration (Phase 6)
    │   │   ├── cab/package-info.java                    ← Cab integration (Phase 6)
    │   │   ├── disruption/package-info.java             ← Disruption engine (Phase 7)
    │   │   ├── recommendation/package-info.java         ← AI recommendations (Phase 8)
    │   │   ├── rebooking/package-info.java              ← Autonomous rebooking (Phase 8)
    │   │   ├── notification/package-info.java           ← Notifications (Phase 9)
    │   │   └── simulation/package-info.java             ← Simulator (Phase 10)
    │   │
    │   └── resources/
    │       └── application.properties                   ← All app configuration
    │
    └── test/
        └── java/com/trainconcierge/
            └── TrainConciergeApplicationTests.java      ← Context load test (H2)
```

---

## API Endpoints Delivered

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/health` | Custom health check — returns app name, version, status | No |
| GET | `/actuator/health` | Spring Actuator — checks DB, disk, ping | No |
| GET | `/actuator/info` | App name, version, description | No |
| GET | `/actuator/metrics` | JVM and HTTP metrics | No |

### Sample Response — `/api/health`

```json
{
  "success": true,
  "message": "Service is healthy",
  "data": {
    "application": "train-concierge",
    "version": "0.0.1-SNAPSHOT",
    "status": "UP",
    "timestamp": "2026-10-01T05:00:00Z"
  },
  "timestamp": "2026-10-01T05:00:00Z"
}
```

### Sample Response — `/actuator/health` (when DB is connected)

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

---

## Infrastructure Decisions & Rationale

### Why environment variables for DB config?
Credentials must never be hardcoded or committed to Git. Using `${DB_HOST:localhost}` syntax gives a safe local default while supporting any deployment environment (Docker, AWS, Kubernetes) without code changes.

### Why `ddl-auto=update` only for local dev?
`update` auto-creates/alters tables — convenient locally, dangerous in production where it can silently drop columns. Production should use `validate` (fail fast if schema doesn't match) with Flyway or Liquibase managing all schema changes explicitly.

### Why `BaseEntity` with JPA auditing?
Every domain entity in this system (User, Train, Booking, Disruption etc.) needs `id`, `createdAt`, `updatedAt`. Centralising this in one `@MappedSuperclass` means no duplication across 15+ entities and automatic timestamp management by Spring Data.

### Why `ApiResponse<T>` wrapper?
Consistent response envelope across all endpoints means frontend teams and API consumers always know the structure — `success`, `message`, `data`, `timestamp`. Also makes global error handling uniform.

### Why `GlobalExceptionHandler`?
Without it, Spring returns different error shapes for validation errors, missing resources, and unhandled exceptions. One `@RestControllerAdvice` class normalises all errors into the same `ApiResponse` format.

### Why H2 for tests?
Tests shouldn't depend on an external MySQL instance running. H2 spins up in-memory in milliseconds, runs the full Spring context, and tears down cleanly — making tests portable and CI-friendly.

---

## Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL server hostname |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `train_concierge` | Database name |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `DDL_AUTO` | `update` | Hibernate DDL strategy |
| `SERVER_PORT` | `8080` | Application port |

---

## How to Run (Windows PowerShell)

```powershell
# Step 1 — Add MySQL to PATH (one time only)
$env:PATH += ";C:\Program Files\MySQL\MySQL Server 8.0\bin"

# Step 2 — Create the database (one time only)
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS train_concierge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Step 3 — Set credentials
$env:DB_HOST     = "localhost"
$env:DB_PORT     = "3306"
$env:DB_NAME     = "train_concierge"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_password"

# Step 4 — Build
mvn clean install -DskipTests "-Djavax.net.ssl.trustStoreType=Windows-ROOT"

# Step 5 — Run
mvn spring-boot:run "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

---

## How to Run Phase 1

Follow these steps in order. Every command is for **Windows PowerShell**.

### Prerequisites

- Java 17+ installed (`java -version`)
- Maven 3.8+ installed (`mvn -version`)
- MySQL 8 installed and running

---

### Step 1 — Add MySQL to PATH (one-time only)

```powershell
$env:PATH += ";C:\Program Files\MySQL\MySQL Server 8.0\bin"
```

To make this permanent across sessions:
```powershell
[System.Environment]::SetEnvironmentVariable(
  "PATH",
  $env:PATH + ";C:\Program Files\MySQL\MySQL Server 8.0\bin",
  [System.EnvironmentVariableTarget]::User
)
```

---

### Step 2 — Create the database (one-time only)

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS train_concierge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

---

### Step 3 — Set environment variables

Run these in the **same PowerShell window** you will use to start the app:

```powershell
$env:DB_HOST     = "localhost"
$env:DB_PORT     = "3306"
$env:DB_NAME     = "train_concierge"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_mysql_password_here"
$env:DDL_AUTO    = "update"
```

---

### Step 4 — Build the project

```powershell
mvn clean install -DskipTests "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Expected output:
```
[INFO] BUILD SUCCESS
```

---

### Step 5 — Run the application

```powershell
mvn spring-boot:run "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Watch for these lines in the console — they confirm a healthy start:
```
HikariPool-1 - Start completed.
Started TrainConciergeApplication in X.XXX seconds
```

---

### Step 6 — Run tests only (no MySQL needed)

```powershell
mvn test "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Expected:
```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Verification Checklist

After starting the app, confirm all of these return expected responses:

```powershell
# 1. Custom health — should return success: true
curl http://localhost:8080/api/health

# 2. Actuator health — db.status should be UP
curl http://localhost:8080/actuator/health

# 3. App info
curl http://localhost:8080/actuator/info

# 4. 404 handler — should return ApiResponse error format
curl http://localhost:8080/api/nonexistent
```

---

## Known Limitations in Phase 1

| Limitation | Planned Resolution |
|---|---|
| No authentication or authorization | Phase 2 — JWT with Spring Security |
| CORS allows all origins (`*`) | Phase 2 — restrict to frontend domain |
| No API versioning | Phase 2 — add `/api/v1/` prefix |
| No rate limiting | Phase 5 |
| No request/response logging | Phase 2 |
| No OpenAPI / Swagger docs | Phase 2 |
| H2 used for tests only (no Testcontainers) | Phase 3 — add Testcontainers MySQL |

---

## What's Next — Phase 2

Phase 2 will implement the **Authentication & User Management** module:

- `POST /api/v1/auth/register` — user registration
- `POST /api/v1/auth/login` — JWT token generation
- `POST /api/v1/auth/refresh` — token refresh
- `POST /api/v1/auth/logout` — token invalidation
- `GET  /api/v1/users/me` — current user profile
- `PUT  /api/v1/users/me` — update profile
- Spring Security integration
- Role-based access control (`ROLE_USER`, `ROLE_ADMIN`)
- Password hashing with BCrypt
- JWT secret via environment variable

---

## Roadmap Overview

| Phase | Focus Area | Status |
|---|---|---|
| **1** | **Foundation & Infrastructure** | ✅ **Complete** |
| 2 | Authentication & User Management | 🔲 Planned |
| 3 | Train & Schedule Management | 🔲 Planned |
| 4 | Seat Availability & Booking | 🔲 Planned |
| 5 | Journey Planning | 🔲 Planned |
| 6 | Hotel & Cab Integration | 🔲 Planned |
| 7 | Disruption Detection & Management | 🔲 Planned |
| 8 | AI Recommendations & Autonomous Rebooking | 🔲 Planned |
| 9 | Notifications (Push / Email / SMS) | 🔲 Planned |
| 10 | Simulation & Testing Tools | 🔲 Planned |

---

*Phase 1 completed — backend foundation is stable and ready for feature development.*

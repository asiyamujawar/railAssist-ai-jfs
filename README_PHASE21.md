# Phase 21 — Docker Deployment

> **Phase 21** containerises the TrainConcierge Spring Boot application and its MySQL 8 database using Docker and Docker Compose. No business logic was changed.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Project Structure — New Files](#3-project-structure--new-files)
4. [Quick Start (Local Docker)](#4-quick-start-local-docker)
5. [Step-by-Step Running Guide](#5-step-by-step-running-guide)
6. [All Docker Commands Reference](#6-all-docker-commands-reference)
7. [Environment Variables Reference](#7-environment-variables-reference)
8. [CORS Configuration](#8-cors-configuration)
9. [Health Checks](#9-health-checks)
10. [Persistent MySQL Data](#10-persistent-mysql-data)
11. [Production-Safe Settings](#11-production-safe-settings)
12. [Dockerfile Explained](#12-dockerfile-explained)
13. [docker-compose.yml Explained](#13-docker-composeyml-explained)
14. [Deployment Considerations](#14-deployment-considerations)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. Overview

| Item | Details |
|---|---|
| Application image | Built from `Dockerfile` (multi-stage, JRE-only runtime, non-root user) |
| Database image | `mysql:8.0` (official, pinned) |
| Orchestration | `docker-compose.yml` |
| Secrets | All credentials sourced from `.env` file — never hardcoded |
| Profile | `SPRING_PROFILES_ACTIVE=docker` activates `application-docker.properties` |
| CORS | Configurable via `CORS_ALLOWED_ORIGINS` env var for the React frontend |
| Health checks | Both containers declare health checks; app waits for MySQL to be healthy |
| Data persistence | MySQL data stored in named Docker volume `mysql_data` |

---

## 2. Prerequisites

| Tool | Version | Install |
|---|---|---|
| Docker Desktop | 24+ | https://docs.docker.com/get-docker/ |
| Docker Compose | v2 (bundled with Docker Desktop) | Included with Docker Desktop |
| Java 17+ | 17 | Only needed to run tests locally without Docker |
| Maven 3.9+ | 3.9 | Only needed to run tests locally without Docker |

> **Note:** Docker Desktop must be running before executing any `docker` or `docker compose` commands.

---

## 3. Project Structure — New Files

```
TrainConceirge/
│
├── Dockerfile                                   ← Multi-stage build
├── docker-compose.yml                           ← MySQL + App orchestration
├── .env.example                                 ← Environment template (commit this)
├── .env                                         ← Real secrets (DO NOT commit)
│
├── docker/
│   └── mysql/
│       └── init/
│           └── 01_init.sql                      ← Runs once on first DB start
│
└── src/main/resources/
    ├── application.properties                   ← Base config (all envs)
    └── application-docker.properties            ← Docker-specific overrides
```

---

## 4. Quick Start (Local Docker)

```powershell
# 1. Copy the environment template
Copy-Item .env.example .env

# 2. Edit .env — fill in real secrets (see Section 7)
notepad .env

# 3. Start both containers (MySQL + Spring Boot)
docker compose up -d

# 4. Wait ~90 seconds for the app to start, then open:
#    API:        http://localhost:8080/api/health
#    Swagger UI: http://localhost:8080/swagger-ui/index.html

# 5. Stop containers
docker compose down
```

---

## 5. Step-by-Step Running Guide

### Step 1 — Copy and edit the environment file

```powershell
Copy-Item .env.example .env
```

Open `.env` and set **all required values**:

```env
# Required — MySQL credentials
MYSQL_ROOT_PASSWORD=YourStrongRootPassword123!
DB_NAME=train_concierge
DB_USERNAME=tc_user
DB_PASSWORD=YourStrongAppPassword456!

# Required — JWT secret (generate with: openssl rand -base64 64)
JWT_SECRET=your_64_character_minimum_random_base64_secret_here

# Optional — change if port 8080 is in use
APP_PORT=8080

# Optional — React frontend origin for CORS (comma-separated)
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

> ⚠️ **Never commit `.env` to git.** The `.gitignore` already excludes it.

---

### Step 2 — Build the Docker image

```powershell
# Build image only (no containers started)
docker compose build

# Force a full rebuild (ignores cache)
docker compose build --no-cache
```

Expected output:
```
[+] Building 120.0s (16/16) FINISHED
 => [builder 1/6] FROM maven:3.9.6-eclipse-temurin-17
 => [runtime 1/4] FROM eclipse-temurin:17-jre-alpine
 => [builder 5/6] RUN mvn package -DskipTests -q
 => [runtime 4/4] COPY --from=builder /build/target/train-concierge-*.jar app.jar
 => exporting to image
```

---

### Step 3 — Start all containers

```powershell
# Start MySQL and App in detached mode
docker compose up -d
```

Docker Compose starts containers in this order:
1. **`db`** (MySQL 8) — starts and waits for its own health check to pass
2. **`app`** (Spring Boot) — starts only after `db` is healthy

Watch startup logs:

```powershell
# Follow logs from both containers
docker compose logs -f

# Follow only the app logs
docker compose logs -f app

# Follow only the MySQL logs
docker compose logs -f db
```

---

### Step 4 — Verify the application is running

```powershell
# Check container status (both should be "healthy")
docker compose ps

# Call the health endpoint
curl http://localhost:8080/actuator/health

# Call the custom health endpoint
curl http://localhost:8080/api/health
```

Expected response from `/actuator/health`:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

Open **Swagger UI** in a browser:
```
http://localhost:8080/swagger-ui/index.html
```

---

### Step 5 — Login and test

```powershell
# Register a user
curl -s -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"firstName":"Test","lastName":"User","email":"test@example.com","password":"Test@2026!"}'

# Login as the seeded admin
curl -s -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"admin@example.com","password":"Admin@2026!"}'
```

---

### Step 6 — Stop the application

```powershell
# Stop containers (keep data volume)
docker compose down

# Stop containers AND delete the MySQL data volume
docker compose down -v

# Stop containers AND delete images
docker compose down --rmi all
```

---

## 6. All Docker Commands Reference

### Container lifecycle

```powershell
# Start all services (detached)
docker compose up -d

# Start with live logs (foreground)
docker compose up

# Stop all services
docker compose down

# Restart a specific service
docker compose restart app

# Stop without removing containers
docker compose stop

# Start stopped containers (without rebuilding)
docker compose start
```

### Building images

```powershell
# Build image using cache
docker compose build

# Force full rebuild (no cache)
docker compose build --no-cache

# Build and start in one command
docker compose up -d --build
```

### Logs and inspection

```powershell
# Follow all logs
docker compose logs -f

# Follow app logs only
docker compose logs -f app

# Follow MySQL logs only
docker compose logs -f db

# Last 100 lines from app
docker compose logs --tail=100 app

# View container status and health
docker compose ps

# Inspect app container details
docker inspect trainconcierge_app

# Inspect MySQL container details
docker inspect trainconcierge_db
```

### Executing commands inside containers

```powershell
# Open a shell in the app container
docker exec -it trainconcierge_app /bin/sh

# Open MySQL CLI inside the db container
docker exec -it trainconcierge_db mysql -u tc_user -p train_concierge

# Open MySQL as root
docker exec -it trainconcierge_db mysql -u root -p
```

### Volume management

```powershell
# List all volumes
docker volume ls

# Inspect the MySQL data volume
docker volume inspect trainconceirge_mysql_data

# Delete the MySQL data volume (WARNING: destroys all data)
docker volume rm trainconceirge_mysql_data
```

### Resource monitoring

```powershell
# Real-time CPU/memory usage of all TrainConcierge containers
docker stats trainconcierge_app trainconcierge_db

# One-shot resource snapshot (no live update)
docker stats --no-stream trainconcierge_app trainconcierge_db
```

### Cleanup

```powershell
# Remove stopped containers, unused networks, dangling images
docker system prune

# Remove ALL unused images (not just dangling)
docker system prune -a

# Remove unused volumes
docker volume prune
```

---

## 7. Environment Variables Reference

All variables are set in `.env` (copy from `.env.example`).

### Required variables (no defaults — must be set)

| Variable | Example | Description |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `RootPass123!` | MySQL root password (Compose init only) |
| `DB_PASSWORD` | `AppPass456!` | Application DB user password |
| `JWT_SECRET` | *(64-char base64)* | JWT signing key — generate with `openssl rand -base64 64` |

### Important variables (have safe defaults)

| Variable | Default | Description |
|---|---|---|
| `DB_NAME` | `train_concierge` | MySQL database name |
| `DB_USERNAME` | `tc_user` | MySQL application user |
| `DB_HOST` | `db` | MySQL host (Compose service name) |
| `DB_PORT` | `3306` | MySQL port |
| `APP_PORT` | `8080` | Host port mapped to app container |
| `DDL_AUTO` | `update` | JPA DDL mode — use `validate` in production |
| `JWT_EXPIRATION_MS` | `86400000` | Token TTL in ms (24 hours) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | React frontend origins (comma-separated) |
| `SPRING_PROFILES_ACTIVE` | `docker` | Activates `application-docker.properties` |
| `MONITORING_ENABLED` | `true` | Enable/disable monitoring scheduler |
| `MONITORING_INTERVAL_MS` | `30000` | Monitoring poll interval (30 s) |

### Optional tuning variables

| Variable | Default | Description |
|---|---|---|
| `SEAT_ALERT_POLL_MS` | `60000` | Seat alert polling interval |
| `DISRUPTION_MIN_DELAY_MINUTES` | `15` | Minimum delay to trigger disruption |
| `DISRUPTION_HIGH_DELAY_MINUTES` | `60` | Delay threshold for HIGH severity |
| `RECOMMENDATION_WEIGHT_ARRIVAL` | `0.35` | Score weight for arrival time |
| `RECOMMENDATION_WEIGHT_FARE` | `0.20` | Score weight for fare |
| `COORDINATION_HOTEL_BUFFER_MINUTES` | `45` | Hotel check-in buffer |
| `COORDINATION_CAB_BUFFER_MINUTES` | `15` | Cab pickup buffer |

---

## 8. CORS Configuration

The `CORS_ALLOWED_ORIGINS` environment variable controls which frontend origins can call the API.

### Development (default)

```env
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Allows any local React development server.

### Multiple origins

```env
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

### Production (React on a real domain)

```env
CORS_ALLOWED_ORIGINS=https://your-react-app.com
```

### How it works

1. `CORS_ALLOWED_ORIGINS` is set in `.env`
2. `docker-compose.yml` passes it to the container as `CORS_ALLOWED_ORIGINS`
3. `application-docker.properties` reads it: `app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:3000}`
4. `SecurityConfig.corsConfigurationSource()` reads `${app.cors.allowed-origins}` via `@Value`
5. If the value is `*`, all origins are permitted (local dev only)
6. Otherwise, only the listed origins are allowed

---

## 9. Health Checks

Both containers declare Docker health checks.

### MySQL health check

```yaml
healthcheck:
  test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "${DB_USERNAME}", "--password=${DB_PASSWORD}"]
  interval: 10s
  timeout: 5s
  retries: 10
  start_period: 30s
```

The `app` service uses `depends_on: db: condition: service_healthy` — it will not start until MySQL is confirmed healthy.

### Spring Boot health check

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 5
  start_period: 90s
```

`start_period: 90s` gives the JVM time to start before Docker begins counting failures.

### Check health status

```powershell
# View health status of all containers
docker compose ps

# Expected output:
# NAME                  STATUS
# trainconcierge_db     healthy
# trainconcierge_app    healthy
```

---

## 10. Persistent MySQL Data

MySQL data is stored in the named volume `mysql_data`:

```yaml
volumes:
  mysql_data:
    driver: local
```

**What this means:**
- Data survives `docker compose down` (containers removed, volume intact)
- Data survives `docker compose restart`
- Data survives image rebuilds (`docker compose up -d --build`)
- Data is **destroyed** only by `docker compose down -v` or `docker volume rm`

### Backup the database

```powershell
# Dump all tables to a .sql file
docker exec trainconcierge_db mysqldump `
  -u tc_user -pYourPassword train_concierge > backup_$(Get-Date -Format "yyyyMMdd_HHmmss").sql
```

### Restore from backup

```powershell
# Restore from a dump file
Get-Content backup_20261003.sql | docker exec -i trainconcierge_db `
  mysql -u tc_user -pYourPassword train_concierge
```

---

## 11. Production-Safe Settings

The `application-docker.properties` profile activates these production-safe settings when `SPRING_PROFILES_ACTIVE=docker`:

| Setting | Dev value | Docker/Prod value | Reason |
|---|---|---|---|
| `spring.jpa.show-sql` | `true` | `false` | No SQL noise in container logs |
| `logging.level.org.hibernate.SQL` | `DEBUG` | `WARN` | Cleaner logs |
| `logging.level.com.trainconcierge` | `DEBUG` | `INFO` | Reduce verbosity |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | `health,info` | Restrict actuator surface |
| `server.shutdown` | — | `graceful` | Allows in-flight requests to finish |
| `spring.lifecycle.timeout-per-shutdown-phase` | — | `30s` | Graceful shutdown window |
| `server.tomcat.threads.max` | default | `200` | Explicit thread pool |
| `spring.datasource.hikari.maximum-pool-size` | `10` | `20` | More DB connections under load |

### DDL strategy

```env
# Development / staging — auto-creates and alters tables
DDL_AUTO=update

# Production — only validates; Flyway/Liquibase manages schema
DDL_AUTO=validate
```

> For true production use, replace `DDL_AUTO=validate` with a **Flyway** or **Liquibase** migration setup.

---

## 12. Dockerfile Explained

```dockerfile
# Stage 1: BUILD
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
# Cache dependency layer separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: RUNTIME — stripped-down JRE only
FROM eclipse-temurin:17-jre-alpine AS runtime
RUN apk add --no-cache curl          # needed for HEALTHCHECK
RUN addgroup -S tcgroup && adduser -S tcuser -G tcgroup
USER tcuser                          # non-root security
WORKDIR /app
COPY --from=builder /build/target/train-concierge-*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java",
  "-XX:+UseContainerSupport",        # respect Docker CPU/RAM limits
  "-XX:MaxRAMPercentage=75.0",       # use at most 75% of container RAM for heap
  "-Djava.security.egd=file:/dev/./urandom",  # fast random (JWT)
  "-jar", "app.jar"]
```

**Key design decisions:**

| Decision | Reason |
|---|---|
| Multi-stage build | Final image contains only the JRE and JAR, not Maven or JDK (~250 MB vs ~600 MB) |
| `eclipse-temurin:17-jre-alpine` | Minimal Alpine-based JRE; smaller attack surface |
| Non-root user (`tcuser`) | Container security best practice |
| `dependency:go-offline` before source copy | Docker layer cache: dependency downloads only re-run when `pom.xml` changes |
| `-XX:+UseContainerSupport` | Prevents JVM from reading host RAM instead of container RAM limit |
| `-XX:MaxRAMPercentage=75.0` | Leaves 25% for non-heap (metaspace, thread stacks, OS) |

---

## 13. docker-compose.yml Explained

```yaml
services:
  db:
    image: mysql:8.0               # Pinned version — never use "latest"
    restart: unless-stopped        # Auto-restart on crash
    ports:
      - "127.0.0.1:3306:3306"     # Loopback only — not internet-accessible
    volumes:
      - mysql_data:/var/lib/mysql  # Named volume for persistence
      - ./docker/mysql/init:/docker-entrypoint-initdb.d:ro  # Init scripts
    healthcheck: ...               # mysqladmin ping

  app:
    build: .
    depends_on:
      db:
        condition: service_healthy # Waits for MySQL to be healthy
    environment:
      DB_HOST: db                  # Compose DNS: "db" resolves to MySQL container IP
    ports:
      - "${APP_PORT:-8080}:8080"
    healthcheck: ...               # curl /actuator/health

volumes:
  mysql_data:                      # Named volume persists across restarts

networks:
  trainconcierge_net:              # Isolated bridge network (db not exposed to host network)
```

---

## 14. Deployment Considerations

### Option A — Railway (recommended for quick deployment)

Railway supports Docker and provides managed MySQL:

1. Push code to GitHub
2. Create a Railway project → **Deploy from GitHub**
3. Add a MySQL plugin (Railway provisions DB automatically)
4. Set environment variables in Railway dashboard (same as `.env`)
5. Set `DDL_AUTO=update` initially; switch to `validate` after schema is stable

```bash
# Railway CLI
railway login
railway up
```

### Option B — Render

1. Create a new Web Service → Docker
2. Add a PostgreSQL or MySQL database (or use PlanetScale)
3. Set environment variables in Render dashboard
4. Set **Health Check Path** to `/actuator/health`

### Option C — AWS EC2 / ECS

```bash
# Build and push to ECR
aws ecr get-login-password --region ap-south-1 | \
  docker login --username AWS --password-stdin <account>.dkr.ecr.ap-south-1.amazonaws.com

docker build -t trainconcierge .
docker tag trainconcierge:latest <account>.dkr.ecr.ap-south-1.amazonaws.com/trainconcierge:latest
docker push <account>.dkr.ecr.ap-south-1.amazonaws.com/trainconcierge:latest
```

Use **RDS MySQL** for the database. Set all environment variables in ECS Task Definition.

### Option D — Google Cloud Run

```bash
# Build via Cloud Build
gcloud builds submit --tag gcr.io/PROJECT_ID/trainconcierge

# Deploy
gcloud run deploy trainconcierge \
  --image gcr.io/PROJECT_ID/trainconcierge \
  --platform managed \
  --region asia-south1 \
  --allow-unauthenticated \
  --set-env-vars "DB_HOST=...,JWT_SECRET=..."
```

Use **Cloud SQL (MySQL)** for the database.

### Production checklist

```
[ ] Use DDL_AUTO=validate (not update)
[ ] Set up Flyway or Liquibase for schema migrations
[ ] Use a secrets manager (AWS Secrets Manager, GCP Secret Manager) instead of .env
[ ] Bind MySQL port to 127.0.0.1 only (already done in docker-compose.yml)
[ ] Enable TLS/SSL on MySQL connection (add useSSL=true to JDBC URL)
[ ] Set up automated DB backups (mysqldump cron or managed DB backup)
[ ] Configure log aggregation (ELK, Loki, CloudWatch)
[ ] Set up Prometheus/Grafana (Actuator exposes /actuator/metrics)
[ ] Set CORS_ALLOWED_ORIGINS to exact frontend domain
[ ] Set JWT_EXPIRATION_MS to a shorter value (e.g., 3600000 = 1 hour)
[ ] Change default admin password after first deploy
[ ] Set up container resource limits (--memory, --cpus in docker-compose)
```

---

## 15. Troubleshooting

### App exits immediately — "Communications link failure"

MySQL is not yet ready. The `depends_on: condition: service_healthy` handles this, but if the MySQL container is taking too long to initialise (e.g., running init scripts), increase `start_period`:

```yaml
healthcheck:
  start_period: 60s  # increase from 30s
```

### Port 8080 or 3306 already in use

```powershell
# Change APP_PORT in .env
APP_PORT=9090

# Or find what is using the port
netstat -ano | findstr :8080
```

### Container shows "unhealthy"

```powershell
# Check what the health check reports
docker inspect trainconcierge_app | Select-String -Pattern "Health" -Context 0,10

# Check app logs for startup errors
docker compose logs app
```

### MySQL data appears lost after restart

If you used `docker compose down -v`, the volume was deleted. Use `docker compose down` (without `-v`) to preserve data.

### "No space left on device" in Docker

```powershell
# Prune unused images and build cache
docker system prune -a

# Check Docker disk usage
docker system df
```

### View JVM memory settings

```powershell
docker exec trainconcierge_app java -XX:+PrintFlagsFinal -version 2>&1 | `
  Select-String "MaxHeap"
```

---

*Phase 21 complete — TrainConcierge is fully containerised and ready for deployment.*

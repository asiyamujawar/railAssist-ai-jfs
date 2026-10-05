# TrainConcierge — Frontend API Contract

This document serves as the single source of truth for all REST API endpoints exposed by the TrainConcierge Spring Boot backend (Java 17, Spring Security JWT, MySQL).

---

## 1. Authentication & Security Architecture

- **Token Format:** JSON Web Token (JWT) signed with HMAC-SHA256.
- **Header Format:** `Authorization: Bearer <token>`
- **Token Claims:** `sub` (email), `role` (`ROLE_USER` or `ROLE_ADMIN`), `iat`, `exp` (24-hour expiration by default).
- **Public Endpoints:** Auth (`/api/auth/register`, `/api/auth/login`), Health (`/api/health`, `/actuator/health`), Swagger (`/swagger-ui/**`, `/v3/api-docs/**`).
- **Protected Endpoints:** Require valid JWT Bearer header.
- **Admin Endpoints:** Require `ROLE_ADMIN` role (`/api/admin/**`).

---

## 2. Global Response Wrappers & Exception Formats

### Standard Success Response (`ApiResponse<T>`)
```json
{
  "status": 200,
  "message": "Operation description",
  "data": { ... },
  "timestamp": "2026-10-03T18:00:00"
}
```

### Standard Error Response (`ErrorResponse`)
```json
{
  "status": 400,
  "message": "Error description",
  "timestamp": "2026-10-03T18:00:00",
  "errors": {
    "fieldName": "Validation error message"
  },
  "path": "/api/path"
}
```

---

## 3. Endpoints Matrix

### 3.1 Authentication & User Profile
| Method | Path | Auth | Role | Request Body | Success Data |
|---|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | None | `{ firstName, lastName, email, password, phoneNumber }` | `{ id, firstName, lastName, email, role, token }` |
| `POST` | `/api/auth/login` | Public | None | `{ email, password }` | `{ id, email, role, token }` |
| `GET` | `/api/auth/me` | Bearer | Any | None | User profile object |

### 3.2 Trains & Schedules
| Method | Path | Auth | Role | Query / Body | Success Data |
|---|---|---|---|---|---|
| `GET` | `/api/trains` | Bearer | Any | `?page=0&size=10` | Page of Train objects |
| `GET` | `/api/trains/{id}` | Bearer | Any | Path `id` | Train object |
| `GET` | `/api/trains/search` | Bearer | Any | `?originStation=&destinationStation=&journeyDate=` | List of matching Trains |
| `GET` | `/api/schedules` | Bearer | Any | `?page=0&size=10` | Page of Schedule objects |
| `GET` | `/api/schedules/{id}` | Bearer | Any | Path `id` | Schedule object |
| `GET` | `/api/schedules/search` | Bearer | Any | `?originStation=&destinationStation=&date=` | List of matching Schedules |
| `GET` | `/api/schedules/{id}/availability` | Bearer | Any | Path `id` | List of `SeatAvailability` by class |

### 3.3 Bookings
| Method | Path | Auth | Role | Request Body | Success Data |
|---|---|---|---|---|---|
| `POST` | `/api/bookings` | Bearer | Any | `{ scheduleId, seatClass, passengerName, passengerAge }` | `Booking` object (Status: CONFIRMED) |
| `GET` | `/api/bookings/my` | Bearer | Any | `?page=0&size=10` | Page of user's `Booking` objects |
| `GET` | `/api/bookings/{id}` | Bearer | Any | Path `id` | `Booking` object (ownership checked) |
| `PATCH` | `/api/bookings/{id}/cancel` | Bearer | Any | Path `id` | `Booking` object (Status: CANCELLED) |

### 3.4 Disruptions & Recommendations
| Method | Path | Auth | Role | Request Body | Success Data |
|---|---|---|---|---|---|
| `GET` | `/api/disruptions/my` | Bearer | Any | None | List of `DisruptionEvent` for user's journeys |
| `GET` | `/api/disruptions/{id}` | Bearer | Any | Path `id` | `DisruptionEvent` object |
| `POST` | `/api/disruptions/{id}/recommendations` | Bearer | Any | Path `id` | List of ranked `TrainAlternative` options |
| `GET` | `/api/disruptions/{id}/recommendations` | Bearer | Any | Path `id` | Existing `TrainAlternative` list |
| `POST` | `/api/disruptions/{id}/rebook` | Bearer | Any | `{ alternativeScheduleId, seatClass }` | `RebookingHistory` record + updated journey |

### 3.5 Journeys & Timeline
| Method | Path | Auth | Role | Request / Query | Success Data |
|---|---|---|---|---|---|
| `GET` | `/api/journeys/{id}/rebooking-history` | Bearer | Any | Path `id` | List of `RebookingHistory` records |
| `POST` | `/api/journeys/{id}/coordination/trigger` | Bearer | Any | Path `id` | `TravelCoordinationRecord` object |
| `GET` | `/api/journeys/{id}/coordination` | Bearer | Any | Path `id` | Coordination status object |
| `GET` | `/api/journeys/{id}/timeline` | Bearer | Any | Path `id` | Chronological list of journey timeline events |

### 3.6 Hotel & Cab Reservations (Simulated)
| Method | Path | Auth | Role | Request Body | Success Data |
|---|---|---|---|---|---|
| `POST` | `/api/hotels` | Bearer | Any | `{ journeyId, hotelName, city, address, checkInDate, checkOutDate }` | `HotelBooking` [SIMULATED] |
| `GET` | `/api/hotels/journey/{journeyId}` | Bearer | Any | Path `journeyId` | List of `HotelBooking` objects |
| `PATCH` | `/api/hotels/{id}/reschedule` | Bearer | Any | `{ newCheckInDate, newCheckOutDate }` | Updated `HotelBooking` [SIMULATED] |
| `POST` | `/api/cabs` | Bearer | Any | `{ journeyId, pickupLocation, destination, scheduledPickupTime, cabType }` | `CabBooking` [SIMULATED] |
| `GET` | `/api/cabs/journey/{journeyId}` | Bearer | Any | Path `journeyId` | List of `CabBooking` objects |
| `PATCH` | `/api/cabs/{id}/reschedule` | Bearer | Any | `{ newScheduledPickupTime }` | Updated `CabBooking` [SIMULATED] |

### 3.7 Smart Seat Alerts
| Method | Path | Auth | Role | Request Body | Success Data |
|---|---|---|---|---|---|
| `POST` | `/api/seat-alerts` | Bearer | Any | `{ scheduleId, seatClass, threshold }` | `SeatAlertSubscription` object |
| `GET` | `/api/seat-alerts/my` | Bearer | Any | None | List of active user alerts |
| `PATCH` | `/api/seat-alerts/{id}/deactivate` | Bearer | Any | Path `id` | Deactivated alert subscription object |

### 3.8 Notifications
| Method | Path | Auth | Role | Query | Success Data |
|---|---|---|---|---|---|
| `GET` | `/api/notifications/my` | Bearer | Any | `?page=0&size=10` | Page of `Notification` objects |
| `GET` | `/api/notifications/unread-count` | Bearer | Any | None | `{ unreadCount: number }` |
| `PATCH` | `/api/notifications/{id}/read` | Bearer | Any | Path `id` | Updated `Notification` object |
| `PATCH` | `/api/notifications/read-all` | Bearer | Any | None | Count of marked notifications |

### 3.9 Admin & Simulation Controls (`ROLE_ADMIN`)
| Method | Path | Auth | Role | Body / Query | Success Data |
|---|---|---|---|---|---|
| `POST` | `/api/admin/trains` | Bearer | ADMIN | `{ trainNumber, trainName, originStation, destinationStation, totalSeats, operatorName }` | Created `Train` object |
| `PUT` | `/api/admin/trains/{id}` | Bearer | ADMIN | `{ trainName, originStation, destinationStation, totalSeats, operatorName }` | Updated `Train` object |
| `PATCH` | `/api/admin/trains/{id}/deactivate` | Bearer | ADMIN | Path `id` | Deactivated `Train` object |
| `POST` | `/api/admin/schedules` | Bearer | ADMIN | `{ trainId, scheduledDate, scheduledDeparture, scheduledArrival, baseFare, platform }` | Created `Schedule` object |
| `PATCH` | `/api/admin/schedules/{id}/availability` | Bearer | ADMIN | `{ seatClass, availableSeats }` | Updated `SeatAvailability` object |
| `PUT` | `/api/admin/simulation/trains/{id}/status` | Bearer | ADMIN | `{ status, delayMinutes, platform }` | Updated `TrainSchedule` [SIMULATED] |
| `POST` | `/api/admin/simulation/control/trigger-delay` | Bearer | ADMIN | `?scheduleId=X&delayMinutes=120` | Simulation status update |
| `POST` | `/api/admin/simulation/control/trigger-cancellation` | Bearer | ADMIN | `?scheduleId=X` | Simulation cancellation update |
| `POST` | `/api/admin/simulation/control/restore-normal` | Bearer | ADMIN | `?scheduleId=X` | Schedule restored to ON_TIME |
| `POST` | `/api/admin/simulation/control/monitoring-cycle` | Bearer | ADMIN | None | `{ evaluatedJourneys, polledSchedules, changesDetected }` |
| `DELETE` | `/api/admin/simulation/control/monitoring-cache` | Bearer | ADMIN | None | `{ status: "cleared" }` |
| `POST` | `/api/admin/simulation/control/disruption-evaluation` | Bearer | ADMIN | `?journeyId=X` | `{ evaluated: true, disruptionEventId, recommendationTriggered }` |

---

## 4. Frontend Integration Guidelines

1. **CORS Configuration:** Backend allows `http://localhost:5173` (Vite dev server) via `SecurityConfig`.
2. **Authentication Interceptor:** Attach `Authorization: Bearer <token>` on all requests.
3. **Response Interceptor:** Automatically redirect to `/login` on HTTP 401 Unauthorized.
4. **Simulation Disclosure:** All hotel, cab, status update, and train simulation endpoints MUST display a `[SIMULATED]` visual badge in the UI.

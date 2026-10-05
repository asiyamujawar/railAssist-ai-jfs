# Phase 17 — In-App Notification System

> **Module status:** Complete  
> **New package:** `com.trainconcierge.notification`  
> **New files:** 6 source + 1 test  
> **Total project tests:** 191 passing (100% success rate)

---

## Overview

The Notification System records and manages user notification history across all platform events, including train bookings, disruption alerts, AI rebooking suggestions, hotel stay adjustments, cab pickups, and seat availability alerts.

It follows an **Outbox Pattern**, writing notifications into MySQL first as `IN_APP` messages. External delivery adapters (such as email or SMS) can process un-sent notification rows asynchronously without coupling external communication providers to core business services.

---

## Key Features & Design Rules

| Feature | Implementation Details |
|---|---|
| **Outbox & Inbox Pattern** | Writes notifications to the `notifications` table (`IN_APP` channel by default). Serves as the user's notification history inbox and outbox queue for future delivery adapters. |
| **Comprehensive Event Support** | Supports `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `DISRUPTION_ALERT`, `REBOOKING_SUGGESTION`, `REBOOKING_CONFIRMED`, `HOTEL_BOOKING_CONFIRMED`, `HOTEL_RESCHEDULED`, `HOTEL_BOOKING_CANCELLED`, `CAB_BOOKING_CONFIRMED`, `CAB_RESCHEDULED`, `CAB_BOOKING_CANCELLED`, `SEAT_AVAILABLE`, `JOURNEY_REMINDER`, and `GENERAL`. |
| **Duplicate Suppression** | Prevents duplicate entries when an event is reprocessed by validating existing `(user, type, referenceId)` tuples. |
| **User Ownership Isolation** | Enforces user security so users can only view, read, or mark their own notifications. Cross-user access returns `403 Forbidden`. |
| **Paginated & Sorted Inbox** | `GET /api/notifications/my` supports pagination (`page`, `size`) and orders notifications newest-first (`createdAt DESC`). |
| **Unread Counter** | `GET /api/notifications/unread-count` provides lightweight unread badge counting. |
| **Bulk & Individual Read** | `PATCH /api/notifications/{id}/read` marks a single notification as read with a timestamp, while `PATCH /api/notifications/read-all` updates all unread notifications in bulk. |

---

## REST API Specifications

### 1. Get My Notifications (Paginated)

* **Endpoint:** `GET /api/notifications/my?page=0&size=20`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK):**
```json
{
  "success": true,
  "message": "2 notification(s) retrieved. Unread: 2.",
  "data": {
    "notifications": [
      {
        "id": 14,
        "type": "DISRUPTION_ALERT",
        "channel": "IN_APP",
        "title": "Train Disruption — London Express",
        "body": "Your train London Express on 2026-10-05 has been disrupted. Reason: Severe delay.",
        "referenceId": "DISRUPTION-102",
        "referenceType": "DisruptionEvent",
        "read": false,
        "readAt": null,
        "createdAt": "2026-10-03T00:10:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true,
    "unreadCount": 1
  }
}
```

---

### 2. Get Unread Notification Count

* **Endpoint:** `GET /api/notifications/unread-count`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Unread notification count retrieved.",
  "data": {
    "unreadCount": 1
  }
}
```

---

### 3. Mark Single Notification as Read

* **Endpoint:** `PATCH /api/notifications/{id}/read`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Notification marked as read.",
  "data": {
    "id": 14,
    "type": "DISRUPTION_ALERT",
    "channel": "IN_APP",
    "read": true,
    "readAt": "2026-10-03T00:12:00Z"
  }
}
```

---

### 4. Mark All Notifications as Read

* **Endpoint:** `PATCH /api/notifications/read-all`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK):**
```json
{
  "success": true,
  "message": "1 notification(s) marked as read.",
  "data": {
    "updatedCount": 1
  }
}
```

---

## Running and Testing Steps

### 1. Running Automated Tests

To run the Notification test suite:

```bash
mvn test -Dtest=NotificationIntegrationTest
```

To execute the full project test suite:

```bash
mvn test
```

---

### 2. End-to-End Walkthrough via cURL

#### Step 1: Login to acquire JWT authentication token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "passenger@example.com", "password": "Password123!"}'
```

#### Step 2: Fetch unread count badge
```bash
curl -X GET http://localhost:8080/api/notifications/unread-count \
  -H "Authorization: Bearer <TOKEN>"
```

#### Step 3: Fetch paginated notifications (newest first)
```bash
curl -X GET "http://localhost:8080/api/notifications/my?page=0&size=10" \
  -H "Authorization: Bearer <TOKEN>"
```

#### Step 4: Mark single notification as read
```bash
curl -X PATCH http://localhost:8080/api/notifications/14/read \
  -H "Authorization: Bearer <TOKEN>"
```

#### Step 5: Mark all unread notifications as read
```bash
curl -X PATCH http://localhost:8080/api/notifications/read-all \
  -H "Authorization: Bearer <TOKEN>"
```

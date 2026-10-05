# Phase 18 — Journey Timeline and Audit History Module

> **Module status:** Complete  
> **New package:** `com.trainconcierge.timeline`  
> **New files:** 5 source + 1 test  
> **Total project tests:** 196 passing (100% success rate)

---

## Overview

The Journey Timeline and Audit History module provides a unified, narrative-driven chronological timeline for any given journey. Instead of returning raw database records, the timeline explains **what happened and why**, bringing together 11 distinct event types across the entire train concierge lifecycle—from initial booking and accommodation setup, through live operational changes and disruption alerts, to AI recommendations, automated rebookings, hotel/cab schedule adjustments, and passenger notifications.

---

## Key Features & Design Rules

| Feature | Implementation Details |
|---|---|
| **Unified Timeline Response** | Aggregates all journey audit history across 11 domain event types into a single, cohesive response DTO formatted specifically for React timeline components. |
| **Narrative Explanations** | Translates technical state changes into clear, human-readable explanations detailing **what happened and why** (e.g. why a cab pickup time was adjusted due to a train delay). |
| **Chronological Ordering** | Automatically sorts all aggregated timeline events by event timestamp ascending (`timestamp ASC`), with deterministic tie-breaking. |
| **User Ownership Isolation** | Validates that the authenticated user owns the requested journey. Cross-user access attempts return `403 Forbidden`. |
| **No Unnecessary Audit Duplication** | Avoids redundant table writes by dynamically reconstructing the timeline from domain entities and existing immutable audit logs (`RebookingHistory`, `HotelModificationAudit`, `CabModificationAudit`, `SeatAlertHistory`, `TrainStatusHistory`, `Notification`, etc.). |
| **React Component Ready** | Provides structured metadata, unique React key `id`s, title, summary, narrative explanation, timestamp, entity references, and UI badge color tags (`info`, `success`, `warning`, `danger`). |

---

## Supported Timeline Event Types

The timeline unifies all 11 required event types:

1. **`BOOKING_CREATED`**: Train ticket booking creation and confirmation details.
2. **`HOTEL_ADDED`**: Hotel accommodation reservation linked to the journey.
3. **`CAB_ADDED`**: Last-mile cab transfer scheduled.
4. **`SEAT_ALERT_GENERATED`**: Automated seat availability alert triggered for a monitored schedule.
5. **`TRAIN_STATUS_CHANGED`**: Operator status updates (e.g. delays, platform changes).
6. **`DISRUPTION_DETECTED`**: Service disruption events detected by the monitoring engine.
7. **`RECOMMENDATION_GENERATED`**: AI-generated alternative itinerary suggestions.
8. **`REBOOKING_COMPLETED`**: Autonomous or manual disruption rebooking execution.
9. **`HOTEL_RESCHEDULED`**: Hotel check-in/out date modifications following train disruptions.
10. **`CAB_RESCHEDULED`**: Cab pickup time adjustments aligning with modified train arrival times.
11. **`NOTIFICATION_GENERATED`**: Passenger in-app / external notification delivery history.

---

## REST API Specification

### Get Unified Journey Timeline

* **Endpoint:** `GET /api/journeys/{id}/timeline`
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Path Parameter:** `id` (Long) — Journey ID

#### Success Response (`200 OK`)

```json
{
  "success": true,
  "message": "Journey timeline retrieved successfully.",
  "data": {
    "journeyId": 101,
    "originStation": "London Euston",
    "destinationStation": "Edinburgh Waverley",
    "travelDate": "2026-10-05",
    "status": "PLANNED",
    "totalEvents": 11,
    "events": [
      {
        "id": "evt-booking-1",
        "eventType": "BOOKING_CREATED",
        "title": "Train Booking Created",
        "summary": "Train booking TC-20261001-0001 confirmed",
        "explanation": "Passenger created train booking TC-20261001-0001 for 2 seat(s) in FIRST class on train LNX100 (London Express). Total fare: GBP 150.00.",
        "timestamp": "2026-10-05T10:10:00Z",
        "entityType": "BOOKING",
        "entityId": "TC-20261001-0001",
        "uiBadgeColor": "info",
        "metadata": {
          "bookingReference": "TC-20261001-0001",
          "seatClass": "FIRST",
          "numberOfSeats": 2,
          "totalFare": 150.00,
          "currency": "GBP",
          "status": "CONFIRMED"
        }
      },
      {
        "id": "evt-hotel-5",
        "eventType": "HOTEL_ADDED",
        "title": "Hotel Booking Added",
        "summary": "Hotel stay at The Balmoral confirmed",
        "explanation": "Hotel accommodation reserved at The Balmoral in Edinburgh for 2 night(s) (Check-in: 2026-10-05, Check-out: 2026-10-07). Confirmation: HOTEL-20261001-101.",
        "timestamp": "2026-10-05T10:20:00Z",
        "entityType": "HOTEL_BOOKING",
        "entityId": "HOTEL-20261001-101",
        "uiBadgeColor": "info",
        "metadata": {
          "hotelBookingReference": "HOTEL-20261001-101",
          "hotelName": "The Balmoral",
          "city": "Edinburgh",
          "checkInDate": "2026-10-05",
          "checkOutDate": "2026-10-07",
          "numberOfNights": 2,
          "totalCost": 250.00,
          "currency": "GBP"
        }
      },
      {
        "id": "evt-cab-3",
        "eventType": "CAB_ADDED",
        "title": "Cab Transport Scheduled",
        "summary": "Cab pickup scheduled with MOCK_CAB",
        "explanation": "Last-mile cab transfer booked via MOCK_CAB from 'Edinburgh Waverley Station' to 'The Balmoral Hotel', scheduled for pickup at 2026-10-05T15:00. Ref: CAB-20261001-201.",
        "timestamp": "2026-10-05T10:30:00Z",
        "entityType": "CAB_BOOKING",
        "entityId": "CAB-20261001-201",
        "uiBadgeColor": "info",
        "metadata": {
          "cabBookingReference": "CAB-20261001-201",
          "provider": "MOCK_CAB",
          "pickupAddress": "Edinburgh Waverley Station",
          "dropoffAddress": "The Balmoral Hotel",
          "scheduledPickupTime": "2026-10-05T15:00",
          "estimatedFare": 25.00,
          "currency": "GBP"
        }
      },
      {
        "id": "evt-seat-alert-12",
        "eventType": "SEAT_ALERT_GENERATED",
        "title": "Seat Availability Alert",
        "summary": "Seat alert triggered for FIRST class",
        "explanation": "Automated seat monitor detected 3 seat(s) available in FIRST class on train schedule #12 (Threshold: 5). Details: 3 seats opened up in FIRST class.",
        "timestamp": "2026-10-05T10:40:00Z",
        "entityType": "SEAT_ALERT",
        "entityId": "12",
        "uiBadgeColor": "info",
        "metadata": {
          "subscriptionId": 4,
          "seatClass": "FIRST",
          "availableSeatsAtAlert": 3,
          "threshold": 5,
          "message": "3 seats opened up in FIRST class."
        }
      },
      {
        "id": "evt-train-status-14",
        "eventType": "TRAIN_STATUS_CHANGED",
        "title": "Train Operational Status Update",
        "summary": "Train status changed to DELAYED (45 min delay)",
        "explanation": "Operator status update for train schedule #12: Status set to DELAYED (45 min delay) at station Peterborough. Reason: Signal failure near Peterborough.",
        "timestamp": "2026-10-05T10:50:00Z",
        "entityType": "TRAIN_STATUS",
        "entityId": "14",
        "uiBadgeColor": "warning",
        "metadata": {
          "scheduleId": 12,
          "status": "DELAYED",
          "delayMinutes": 45,
          "simulated": true,
          "recordedAtStation": "Peterborough",
          "message": "Signal failure near Peterborough"
        }
      },
      {
        "id": "evt-disruption-8",
        "eventType": "DISRUPTION_DETECTED",
        "title": "Service Disruption Detected",
        "summary": "DELAY disruption detected (HIGH severity)",
        "explanation": "System detected a HIGH DELAY disruption on train schedule #12. Estimated delay: 45 minute(s). Impact description: 45-minute delay due to signal failure.",
        "timestamp": "2026-10-05T11:00:00Z",
        "entityType": "DISRUPTION_EVENT",
        "entityId": "8",
        "uiBadgeColor": "danger",
        "metadata": {
          "disruptionId": 8,
          "type": "DELAY",
          "severity": "HIGH",
          "status": "DETECTED",
          "estimatedDelayMinutes": 45,
          "description": "45-minute delay due to signal failure"
        }
      },
      {
        "id": "evt-recommendation-19",
        "eventType": "RECOMMENDATION_GENERATED",
        "title": "AI Rebooking Recommendation Generated",
        "summary": "Alternative route suggested with score 0.950",
        "explanation": "In response to disruption #8, AI engine recommended alternative train schedule #15 (Score: 0.950/1.000). Reason: Next direct service departing at 11:00.",
        "timestamp": "2026-10-05T11:10:00Z",
        "entityType": "RECOMMENDATION",
        "entityId": "19",
        "uiBadgeColor": "info",
        "metadata": {
          "recommendationId": 19,
          "disruptionEventId": 8,
          "suggestedScheduleId": 15,
          "score": 0.950,
          "reason": "Next direct service departing at 11:00",
          "status": "ACCEPTED"
        }
      },
      {
        "id": "evt-rebooking-2",
        "eventType": "REBOOKING_COMPLETED",
        "title": "Disruption Rebooking Processed",
        "summary": "Rebooking status: COMPLETED",
        "explanation": "Disruption recovery workflow automatically processed rebooking for original ticket TC-20261001-0001 and issued replacement booking TC-20261001-0002 (Fare diff: 0.00). Rebooking process completed.",
        "timestamp": "2026-10-05T11:20:00Z",
        "entityType": "REBOOKING_HISTORY",
        "entityId": "2",
        "uiBadgeColor": "success",
        "metadata": {
          "rebookingId": 2,
          "originalBookingReference": "TC-20261001-0001",
          "newBookingReference": "TC-20261001-0002",
          "status": "COMPLETED",
          "autonomous": true,
          "fareDifference": 0.00
        }
      },
      {
        "id": "evt-hotel-rescheduled-1",
        "eventType": "HOTEL_RESCHEDULED",
        "title": "Hotel Reservation Rescheduled",
        "summary": "Hotel stay at The Balmoral shifted to 2026-10-06",
        "explanation": "Due to journey adjustments, hotel booking HOTEL-20261001-101 was rescheduled. Check-in shifted from [2026-10-05 to 2026-10-07] to [2026-10-06 to 2026-10-08].",
        "timestamp": "2026-10-05T11:30:00Z",
        "entityType": "HOTEL_MODIFICATION",
        "entityId": "1",
        "uiBadgeColor": "warning",
        "metadata": {
          "hotelBookingReference": "HOTEL-20261001-101",
          "oldCheckInDate": "2026-10-05",
          "newCheckInDate": "2026-10-06",
          "oldCheckOutDate": "2026-10-07",
          "newCheckOutDate": "2026-10-08",
          "provider": "MOCK"
        }
      },
      {
        "id": "evt-cab-rescheduled-1",
        "eventType": "CAB_RESCHEDULED",
        "title": "Cab Pickup Rescheduled",
        "summary": "Cab pickup time adjusted to 2026-10-05T16:00",
        "explanation": "To align with train arrival modifications, cab booking CAB-20261001-201 pickup time was shifted from 2026-10-05T15:00 to 2026-10-05T16:00.",
        "timestamp": "2026-10-05T11:40:00Z",
        "entityType": "CAB_MODIFICATION",
        "entityId": "1",
        "uiBadgeColor": "warning",
        "metadata": {
          "cabBookingReference": "CAB-20261001-201",
          "oldPickupTime": "2026-10-05T15:00",
          "newPickupTime": "2026-10-05T16:00",
          "provider": "MOCK"
        }
      },
      {
        "id": "evt-notification-44",
        "eventType": "NOTIFICATION_GENERATED",
        "title": "Passenger Notification Generated",
        "summary": "Rebooking Confirmed",
        "explanation": "Dispatched REBOOKING_CONFIRMED notification via IN_APP channel: \"Your train booking TC-20261001-0001 has been rebooked to TC-20261001-0002.\".",
        "timestamp": "2026-10-05T11:50:00Z",
        "entityType": "NOTIFICATION",
        "entityId": "44",
        "uiBadgeColor": "info",
        "metadata": {
          "notificationId": 44,
          "type": "REBOOKING_CONFIRMED",
          "channel": "IN_APP",
          "title": "Rebooking Confirmed",
          "read": false,
          "sent": true
        }
      }
    ]
  }
}
```

#### Error Responses

* **`403 Forbidden`** — Accessing another user's journey timeline:
```json
{
  "success": false,
  "errorCode": "ACCESS_DENIED",
  "message": "Access denied: You can only view timeline history for your own journeys."
}
```
* **`404 Not Found`** — Journey ID does not exist:
```json
{
  "success": false,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Journey not found with id: '999999'"
}
```

---

## Running and Testing Steps

### 1. Running Automated Tests

To execute the Journey Timeline test suite:

```bash
mvn test -Dtest=JourneyTimelineIntegrationTest
```

To execute the full project test suite (196 passing tests):

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

#### Step 2: Fetch unified journey timeline
```bash
curl -X GET http://localhost:8080/api/journeys/101/timeline \
  -H "Authorization: Bearer <TOKEN>"
```

#### Step 3: Attempt cross-user access (Expect 403 Forbidden)
```bash
curl -X GET http://localhost:8080/api/journeys/101/timeline \
  -H "Authorization: Bearer <OTHER_USER_TOKEN>"
```

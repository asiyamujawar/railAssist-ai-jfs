package com.trainconcierge.timeline;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.cab.CabBooking;
import com.trainconcierge.cab.CabBookingRepository;
import com.trainconcierge.cab.CabModificationAudit;
import com.trainconcierge.cab.CabModificationAuditRepository;
import com.trainconcierge.disruption.DisruptionEvent;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.hotel.HotelBooking;
import com.trainconcierge.hotel.HotelBookingRepository;
import com.trainconcierge.hotel.HotelModificationAudit;
import com.trainconcierge.hotel.HotelModificationAuditRepository;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.notification.Notification;
import com.trainconcierge.notification.NotificationRepository;
import com.trainconcierge.rebooking.RebookingHistory;
import com.trainconcierge.rebooking.RebookingHistoryRepository;
import com.trainconcierge.recommendation.Recommendation;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.seat.SeatAlertHistory;
import com.trainconcierge.seat.SeatAlertHistoryRepository;
import com.trainconcierge.timeline.dto.JourneyTimelineEventResponse;
import com.trainconcierge.timeline.dto.JourneyTimelineResponse;
import com.trainconcierge.train.TrainStatusHistory;
import com.trainconcierge.train.TrainStatusHistoryRepository;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business service for reconstructing and aggregating a Journey's complete
 * timeline and audit history across all 11 domain event types.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JourneyTimelineService {

    private final JourneyRepository journeyRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final HotelBookingRepository hotelBookingRepository;
    private final HotelModificationAuditRepository hotelModificationAuditRepository;
    private final CabBookingRepository cabBookingRepository;
    private final CabModificationAuditRepository cabModificationAuditRepository;
    private final SeatAlertHistoryRepository seatAlertHistoryRepository;
    private final TrainStatusHistoryRepository trainStatusHistoryRepository;
    private final DisruptionEventRepository disruptionEventRepository;
    private final RecommendationRepository recommendationRepository;
    private final RebookingHistoryRepository rebookingHistoryRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public JourneyTimelineResponse getJourneyTimeline(Long journeyId, String currentUserEmail) {
        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey", "id", journeyId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", currentUserEmail));

        // Enforce user ownership isolation
        if (!journey.getUser().getId().equals(currentUser.getId())) {
            log.warn("User {} attempted to access timeline for journey {} owned by user {}",
                    currentUser.getId(), journeyId, journey.getUser().getId());
            throw new ForbiddenException("Access denied: You can only view timeline history for your own journeys.");
        }

        List<JourneyTimelineEventResponse> events = new ArrayList<>();

        // 1. Bookings created for this journey
        List<Booking> bookings = bookingRepository.findByJourney(journey);
        for (Booking b : bookings) {
            events.add(buildBookingCreatedEvent(b));
        }

        // Collect TrainSchedules involved in this journey
        List<TrainSchedule> schedules = bookings.stream()
                .map(Booking::getSchedule)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 2. Hotel bookings added for this journey
        List<HotelBooking> hotelBookings = hotelBookingRepository.findByJourney(journey);
        for (HotelBooking hb : hotelBookings) {
            events.add(buildHotelAddedEvent(hb));
        }

        // 3. Hotel rescheduled audits
        if (!hotelBookings.isEmpty()) {
            List<HotelModificationAudit> hotelAudits = hotelModificationAuditRepository.findByHotelBookingIn(hotelBookings);
            for (HotelModificationAudit ha : hotelAudits) {
                events.add(buildHotelRescheduledEvent(ha));
            }
        }

        // 4. Cab bookings added for this journey
        List<CabBooking> cabBookings = cabBookingRepository.findByJourney(journey);
        for (CabBooking cb : cabBookings) {
            events.add(buildCabAddedEvent(cb));
        }

        // 5. Cab rescheduled audits
        if (!cabBookings.isEmpty()) {
            List<CabModificationAudit> cabAudits = cabModificationAuditRepository.findByCabBookingIn(cabBookings);
            for (CabModificationAudit ca : cabAudits) {
                events.add(buildCabRescheduledEvent(ca));
            }
        }

        // 6. Seat alert generated
        Set<Long> scheduleIds = schedules.stream().map(TrainSchedule::getId).collect(Collectors.toSet());
        List<SeatAlertHistory> seatAlerts = seatAlertHistoryRepository.findByUserWithDetails(currentUser);
        for (SeatAlertHistory sah : seatAlerts) {
            if (sah.getSubscription() != null && sah.getSubscription().getSchedule() != null
                    && scheduleIds.contains(sah.getSubscription().getSchedule().getId())) {
                events.add(buildSeatAlertEvent(sah));
            }
        }

        // 7. Train status changed
        if (!schedules.isEmpty()) {
            List<TrainStatusHistory> trainStatuses = trainStatusHistoryRepository.findByScheduleIn(schedules);
            for (TrainStatusHistory tsh : trainStatuses) {
                events.add(buildTrainStatusEvent(tsh));
            }
        }

        // 8. Disruption detected
        Map<Long, DisruptionEvent> disruptionMap = new HashMap<>();
        List<DisruptionEvent> journeyDisruptions = disruptionEventRepository.findByJourney(journey);
        journeyDisruptions.forEach(d -> disruptionMap.put(d.getId(), d));

        if (!schedules.isEmpty()) {
            List<DisruptionEvent> scheduleDisruptions = disruptionEventRepository.findByScheduleIn(schedules);
            scheduleDisruptions.forEach(d -> disruptionMap.put(d.getId(), d));
        }

        List<DisruptionEvent> allDisruptions = new ArrayList<>(disruptionMap.values());
        for (DisruptionEvent de : allDisruptions) {
            events.add(buildDisruptionEvent(de));
        }

        // 9. Recommendation generated
        if (!allDisruptions.isEmpty()) {
            List<Recommendation> recommendations = recommendationRepository.findByDisruptionEventInAndUser(allDisruptions, currentUser);
            for (Recommendation r : recommendations) {
                events.add(buildRecommendationEvent(r));
            }
        }

        // 10. Rebooking completed / audit history
        List<RebookingHistory> rebookings = rebookingHistoryRepository.findByJourneyWithDetails(journey);
        for (RebookingHistory rh : rebookings) {
            events.add(buildRebookingCompletedEvent(rh));
        }

        // 11. Notifications generated relevant to this journey
        Set<String> relevantRefs = new HashSet<>();
        relevantRefs.add(journey.getId().toString());
        bookings.forEach(b -> relevantRefs.add(b.getBookingReference()));
        hotelBookings.forEach(hb -> relevantRefs.add(hb.getHotelBookingReference()));
        cabBookings.forEach(cb -> relevantRefs.add(cb.getCabBookingReference()));
        allDisruptions.forEach(d -> relevantRefs.add(d.getId().toString()));
        allDisruptions.forEach(d -> relevantRefs.add("DISRUPTION-" + d.getId()));
        rebookings.forEach(r -> relevantRefs.add(r.getId().toString()));

        List<Notification> userNotifications = notificationRepository.findByUserOrderByCreatedAtDesc(currentUser);
        for (Notification n : userNotifications) {
            if (n.getReferenceId() != null && relevantRefs.contains(n.getReferenceId())) {
                events.add(buildNotificationEvent(n));
            }
        }

        // Sort events chronologically (ascending by timestamp)
        events.sort(Comparator.comparing(JourneyTimelineEventResponse::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JourneyTimelineEventResponse::getEventType)
                .thenComparing(JourneyTimelineEventResponse::getId));

        return JourneyTimelineResponse.builder()
                .journeyId(journey.getId())
                .originStation(journey.getOriginStation())
                .destinationStation(journey.getDestinationStation())
                .travelDate(journey.getTravelDate())
                .status(journey.getStatus())
                .totalEvents(events.size())
                .events(events)
                .build();
    }

    // ── Helper builders for rich narrative explanations ─────────────────────

    private JourneyTimelineEventResponse buildBookingCreatedEvent(Booking b) {
        Instant ts = b.getConfirmedAt() != null ? b.getConfirmedAt() : b.getCreatedAt();
        String trainInfo = (b.getSchedule() != null && b.getSchedule().getTrain() != null)
                ? b.getSchedule().getTrain().getTrainNumber() + " (" + b.getSchedule().getTrain().getTrainName() + ")"
                : "service";

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("bookingReference", b.getBookingReference());
        meta.put("seatClass", b.getSeatClass());
        meta.put("numberOfSeats", b.getNumberOfSeats());
        meta.put("totalFare", b.getTotalFare());
        meta.put("currency", b.getCurrency());
        meta.put("status", b.getStatus());
        if (b.getSeatNumbers() != null) meta.put("seatNumbers", b.getSeatNumbers());

        return JourneyTimelineEventResponse.builder()
                .id("evt-booking-" + b.getId())
                .eventType(TimelineEventType.BOOKING_CREATED)
                .title("Train Booking Created")
                .summary("Train booking " + b.getBookingReference() + " confirmed")
                .explanation(String.format("Passenger created train booking %s for %d seat(s) in %s class on train %s. Total fare: %s %s.",
                        b.getBookingReference(), b.getNumberOfSeats(), b.getSeatClass(), trainInfo, b.getCurrency(), b.getTotalFare()))
                .timestamp(ts)
                .entityType("BOOKING")
                .entityId(b.getBookingReference())
                .uiBadgeColor("info")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildHotelAddedEvent(HotelBooking hb) {
        Instant ts = hb.getConfirmedAt() != null ? hb.getConfirmedAt() : hb.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("hotelBookingReference", hb.getHotelBookingReference());
        meta.put("hotelName", hb.getHotelName());
        meta.put("city", hb.getCity());
        meta.put("checkInDate", hb.getCheckInDate().toString());
        meta.put("checkOutDate", hb.getCheckOutDate().toString());
        meta.put("numberOfNights", hb.getNumberOfNights());
        meta.put("totalCost", hb.getTotalCost());
        meta.put("currency", hb.getCurrency());

        return JourneyTimelineEventResponse.builder()
                .id("evt-hotel-" + hb.getId())
                .eventType(TimelineEventType.HOTEL_ADDED)
                .title("Hotel Booking Added")
                .summary("Hotel stay at " + hb.getHotelName() + " confirmed")
                .explanation(String.format("Hotel accommodation reserved at %s in %s for %d night(s) (Check-in: %s, Check-out: %s). Confirmation: %s.",
                        hb.getHotelName(), hb.getCity(), hb.getNumberOfNights(), hb.getCheckInDate(), hb.getCheckOutDate(), hb.getHotelBookingReference()))
                .timestamp(ts)
                .entityType("HOTEL_BOOKING")
                .entityId(hb.getHotelBookingReference())
                .uiBadgeColor("info")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildHotelRescheduledEvent(HotelModificationAudit ha) {
        Instant ts = ha.getRescheduledAt() != null ? ha.getRescheduledAt() : ha.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("hotelBookingReference", ha.getHotelBooking().getHotelBookingReference());
        meta.put("oldCheckInDate", ha.getOldCheckInDate().toString());
        meta.put("newCheckInDate", ha.getNewCheckInDate().toString());
        meta.put("oldCheckOutDate", ha.getOldCheckOutDate().toString());
        meta.put("newCheckOutDate", ha.getNewCheckOutDate().toString());
        meta.put("provider", ha.getSimulationProvider());

        return JourneyTimelineEventResponse.builder()
                .id("evt-hotel-rescheduled-" + ha.getId())
                .eventType(TimelineEventType.HOTEL_RESCHEDULED)
                .title("Hotel Reservation Rescheduled")
                .summary("Hotel stay at " + ha.getHotelBooking().getHotelName() + " shifted to " + ha.getNewCheckInDate())
                .explanation(String.format("Due to journey adjustments, hotel booking %s was rescheduled. Check-in shifted from [%s to %s] to [%s to %s].",
                        ha.getHotelBooking().getHotelBookingReference(), ha.getOldCheckInDate(), ha.getOldCheckOutDate(), ha.getNewCheckInDate(), ha.getNewCheckOutDate()))
                .timestamp(ts)
                .entityType("HOTEL_MODIFICATION")
                .entityId(String.valueOf(ha.getId()))
                .uiBadgeColor("warning")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildCabAddedEvent(CabBooking cb) {
        Instant ts = cb.getConfirmedAt() != null ? cb.getConfirmedAt() : cb.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cabBookingReference", cb.getCabBookingReference());
        meta.put("provider", cb.getProvider());
        meta.put("pickupAddress", cb.getPickupAddress());
        meta.put("dropoffAddress", cb.getDropoffAddress());
        meta.put("scheduledPickupTime", cb.getScheduledPickupTime().toString());
        meta.put("estimatedFare", cb.getEstimatedFare());
        meta.put("currency", cb.getCurrency());

        return JourneyTimelineEventResponse.builder()
                .id("evt-cab-" + cb.getId())
                .eventType(TimelineEventType.CAB_ADDED)
                .title("Cab Transport Scheduled")
                .summary("Cab pickup scheduled with " + cb.getProvider())
                .explanation(String.format("Last-mile cab transfer booked via %s from '%s' to '%s', scheduled for pickup at %s. Ref: %s.",
                        cb.getProvider(), cb.getPickupAddress(), cb.getDropoffAddress(), cb.getScheduledPickupTime(), cb.getCabBookingReference()))
                .timestamp(ts)
                .entityType("CAB_BOOKING")
                .entityId(cb.getCabBookingReference())
                .uiBadgeColor("info")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildCabRescheduledEvent(CabModificationAudit ca) {
        Instant ts = ca.getRescheduledAt() != null ? ca.getRescheduledAt() : ca.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cabBookingReference", ca.getCabBooking().getCabBookingReference());
        meta.put("oldPickupTime", ca.getOldPickupTime().toString());
        meta.put("newPickupTime", ca.getNewPickupTime().toString());
        meta.put("provider", ca.getSimulationProvider());

        return JourneyTimelineEventResponse.builder()
                .id("evt-cab-rescheduled-" + ca.getId())
                .eventType(TimelineEventType.CAB_RESCHEDULED)
                .title("Cab Pickup Rescheduled")
                .summary("Cab pickup time adjusted to " + ca.getNewPickupTime())
                .explanation(String.format("To align with train arrival modifications, cab booking %s pickup time was shifted from %s to %s.",
                        ca.getCabBooking().getCabBookingReference(), ca.getOldPickupTime(), ca.getNewPickupTime()))
                .timestamp(ts)
                .entityType("CAB_MODIFICATION")
                .entityId(String.valueOf(ca.getId()))
                .uiBadgeColor("warning")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildSeatAlertEvent(SeatAlertHistory sah) {
        Instant ts = sah.getAlertedAt() != null ? sah.getAlertedAt() : sah.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("subscriptionId", sah.getSubscription().getId());
        meta.put("seatClass", sah.getSubscription().getSeatClass());
        meta.put("availableSeatsAtAlert", sah.getAvailableSeatsAtAlert());
        meta.put("threshold", sah.getThreshold());
        meta.put("message", sah.getMessage());

        return JourneyTimelineEventResponse.builder()
                .id("evt-seat-alert-" + sah.getId())
                .eventType(TimelineEventType.SEAT_ALERT_GENERATED)
                .title("Seat Availability Alert")
                .summary("Seat alert triggered for " + sah.getSubscription().getSeatClass() + " class")
                .explanation(String.format("Automated seat monitor detected %d seat(s) available in %s class on train schedule #%d (Threshold: %d). Details: %s.",
                        sah.getAvailableSeatsAtAlert(), sah.getSubscription().getSeatClass(), sah.getSubscription().getSchedule().getId(), sah.getThreshold(), sah.getMessage()))
                .timestamp(ts)
                .entityType("SEAT_ALERT")
                .entityId(String.valueOf(sah.getId()))
                .uiBadgeColor("info")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildTrainStatusEvent(TrainStatusHistory tsh) {
        Instant ts = tsh.getRecordedAt() != null ? tsh.getRecordedAt() : tsh.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("scheduleId", tsh.getSchedule().getId());
        meta.put("status", tsh.getStatus());
        meta.put("delayMinutes", tsh.getDelayMinutes());
        meta.put("simulated", tsh.isSimulated());
        if (tsh.getRecordedAtStation() != null) meta.put("recordedAtStation", tsh.getRecordedAtStation());
        if (tsh.getMessage() != null) meta.put("message", tsh.getMessage());

        String delayText = tsh.getDelayMinutes() > 0 ? " (" + tsh.getDelayMinutes() + " min delay)" : "";
        String stationText = tsh.getRecordedAtStation() != null ? " at station " + tsh.getRecordedAtStation() : "";
        String msgText = tsh.getMessage() != null ? ". Reason: " + tsh.getMessage() : "";

        return JourneyTimelineEventResponse.builder()
                .id("evt-train-status-" + tsh.getId())
                .eventType(TimelineEventType.TRAIN_STATUS_CHANGED)
                .title("Train Operational Status Update")
                .summary("Train status changed to " + tsh.getStatus() + delayText)
                .explanation(String.format("Operator status update for train schedule #%d: Status set to %s%s%s%s.",
                        tsh.getSchedule().getId(), tsh.getStatus(), delayText, stationText, msgText))
                .timestamp(ts)
                .entityType("TRAIN_STATUS")
                .entityId(String.valueOf(tsh.getId()))
                .uiBadgeColor(tsh.getDelayMinutes() > 0 ? "warning" : "info")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildDisruptionEvent(DisruptionEvent de) {
        Instant ts = de.getDetectedAt() != null ? de.getDetectedAt() : de.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("disruptionId", de.getId());
        meta.put("type", de.getType());
        meta.put("severity", de.getSeverity());
        meta.put("status", de.getStatus());
        meta.put("estimatedDelayMinutes", de.getEstimatedDelayMinutes());
        meta.put("description", de.getDescription());
        if (de.getTrainNumber() != null) meta.put("trainNumber", de.getTrainNumber());

        return JourneyTimelineEventResponse.builder()
                .id("evt-disruption-" + de.getId())
                .eventType(TimelineEventType.DISRUPTION_DETECTED)
                .title("Service Disruption Detected")
                .summary(de.getType() + " disruption detected (" + de.getSeverity() + " severity)")
                .explanation(String.format("System detected a %s %s disruption on train schedule #%d. Estimated delay: %d minute(s). Impact description: %s.",
                        de.getSeverity(), de.getType(), de.getSchedule().getId(), de.getEstimatedDelayMinutes(), de.getDescription()))
                .timestamp(ts)
                .entityType("DISRUPTION_EVENT")
                .entityId(String.valueOf(de.getId()))
                .uiBadgeColor("danger")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildRecommendationEvent(Recommendation r) {
        Instant ts = r.getRespondedAt() != null ? r.getRespondedAt() : r.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("recommendationId", r.getId());
        meta.put("disruptionEventId", r.getDisruptionEvent().getId());
        meta.put("suggestedScheduleId", r.getSuggestedSchedule().getId());
        meta.put("score", r.getScore());
        meta.put("reason", r.getReason());
        meta.put("status", r.getStatus());

        return JourneyTimelineEventResponse.builder()
                .id("evt-recommendation-" + r.getId())
                .eventType(TimelineEventType.RECOMMENDATION_GENERATED)
                .title("AI Rebooking Recommendation Generated")
                .summary("Alternative route suggested with score " + r.getScore())
                .explanation(String.format("In response to disruption #%d, AI engine recommended alternative train schedule #%d (Score: %s/1.000). Reason: %s.",
                        r.getDisruptionEvent().getId(), r.getSuggestedSchedule().getId(), r.getScore(), r.getReason()))
                .timestamp(ts)
                .entityType("RECOMMENDATION")
                .entityId(String.valueOf(r.getId()))
                .uiBadgeColor("info")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildRebookingCompletedEvent(RebookingHistory rh) {
        Instant ts = rh.getCompletedAt() != null ? rh.getCompletedAt() : (rh.getInitiatedAt() != null ? rh.getInitiatedAt() : rh.getCreatedAt());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("rebookingId", rh.getId());
        meta.put("originalBookingReference", rh.getOriginalBooking().getBookingReference());
        if (rh.getNewBooking() != null) meta.put("newBookingReference", rh.getNewBooking().getBookingReference());
        meta.put("status", rh.getStatus());
        meta.put("autonomous", rh.isAutonomous());
        meta.put("fareDifference", rh.getFareDifference());
        if (rh.getFailureReason() != null) meta.put("failureReason", rh.getFailureReason());

        String autoText = rh.isAutonomous() ? "automatically" : "manually";
        String newRefText = rh.getNewBooking() != null ? " and issued replacement booking " + rh.getNewBooking().getBookingReference() : "";
        String outcomeText = rh.getFailureReason() != null ? " Failure reason: " + rh.getFailureReason() : " Rebooking process completed.";

        return JourneyTimelineEventResponse.builder()
                .id("evt-rebooking-" + rh.getId())
                .eventType(TimelineEventType.REBOOKING_COMPLETED)
                .title("Disruption Rebooking Processed")
                .summary("Rebooking status: " + rh.getStatus())
                .explanation(String.format("Disruption recovery workflow %s processed rebooking for original ticket %s%s (Fare diff: %s).%s",
                        autoText, rh.getOriginalBooking().getBookingReference(), newRefText, rh.getFareDifference(), outcomeText))
                .timestamp(ts)
                .entityType("REBOOKING_HISTORY")
                .entityId(String.valueOf(rh.getId()))
                .uiBadgeColor(rh.getStatus() == com.trainconcierge.rebooking.RebookingStatus.COMPLETED ? "success" : "warning")
                .metadata(meta)
                .build();
    }

    private JourneyTimelineEventResponse buildNotificationEvent(Notification n) {
        Instant ts = n.getSentAt() != null ? n.getSentAt() : n.getCreatedAt();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("notificationId", n.getId());
        meta.put("type", n.getType());
        meta.put("channel", n.getChannel());
        meta.put("title", n.getTitle());
        meta.put("read", n.isRead());
        meta.put("sent", n.isSent());

        return JourneyTimelineEventResponse.builder()
                .id("evt-notification-" + n.getId())
                .eventType(TimelineEventType.NOTIFICATION_GENERATED)
                .title("Passenger Notification Generated")
                .summary(n.getTitle())
                .explanation(String.format("Dispatched %s notification via %s channel: \"%s\".",
                        n.getType(), n.getChannel(), n.getBody()))
                .timestamp(ts)
                .entityType("NOTIFICATION")
                .entityId(String.valueOf(n.getId()))
                .uiBadgeColor("info")
                .metadata(meta)
                .build();
    }
}

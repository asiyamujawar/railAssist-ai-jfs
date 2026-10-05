package com.trainconcierge.monitoring;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.disruption.*;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.notification.NotificationService;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.train.TrainStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dedicated Disruption Detection Engine for TrainConcierge.
 *
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Map train status changes to {@link DisruptionEvent} records linked to affected Journeys.</li>
 *   <li>Evaluate configurable delay thresholds and cancellation rules.</li>
 *   <li>Detect platform changes as informational events without triggering disruptions.</li>
 *   <li>Prevent duplicate open disruption events for the same journey and disruption condition.</li>
 *   <li>Manage event lifecycle states: DETECTED, PROCESSING, RESOLVED, FAILED.</li>
 *   <li>Trigger the recommendation workflow for eligible disruptions without performing booking/hotel/cab updates.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisruptionDetectionService {

    private final DisruptionEventRepository disruptionEventRepository;
    private final BookingRepository bookingRepository;
    private final JourneyRepository journeyRepository;
    private final TrainScheduleRepository scheduleRepository;
    private final DisruptionDetectionProperties properties;
    private final RecommendationWorkflowTrigger recommendationWorkflowTrigger;
    private final NotificationService notificationService;

    // List of inactive statuses for open disruption checks
    private static final List<DisruptionStatus> INACTIVE_STATUSES = List.of(
            DisruptionStatus.RESOLVED, DisruptionStatus.FAILED
    );

    /**
     * Primary entry point — processes a detected train status change.
     *
     * @param change the status change DTO produced by the scheduler
     * @return the primary created DisruptionEvent, or null if skipped/no event created
     */
    @Transactional
    public DisruptionEvent handle(MonitoredStatusChange change) {
        TrainSchedule schedule = scheduleRepository.findById(change.getScheduleId())
                .orElse(null);
        if (schedule == null) {
            log.warn("[DisruptionDetection] Schedule {} not found — skipping.", change.getScheduleId());
            return null;
        }

        // 1. Handle Platform Changes as Informational Events
        if (change.getNewStatus() == TrainStatus.PLATFORM_CHANGED) {
            return handlePlatformChange(schedule, change);
        }

        // 2. Handle Non-Disruptive Statuses (e.g., ON_TIME)
        if (!change.isDisruptive()) {
            log.debug("[DisruptionDetection] Status {} on schedule {} is non-disruptive — ignoring.",
                    change.getNewStatus(), change.getScheduleId());
            return null;
        }

        // 3. Evaluate Delay Thresholds & Rules
        int delayMinutes = change.getDelayMinutes();
        if (change.getNewStatus() == TrainStatus.DELAYED && delayMinutes < properties.getMinDelayMinutes()) {
            log.info("[DisruptionDetection] Delay of {} min on schedule {} is below configured min threshold ({} min) — logging as non-disruptive.",
                    delayMinutes, change.getScheduleId(), properties.getMinDelayMinutes());
            return null;
        }

        // 4. Determine Affected Journeys via Confirmed Bookings
        List<Booking> bookings = bookingRepository.findByScheduleAndStatus(schedule, BookingStatus.CONFIRMED);
        Set<Journey> affectedJourneys = bookings.stream()
                .map(Booking::getJourney)
                .filter(j -> j != null
                        && j.getStatus() != JourneyStatus.COMPLETED
                        && j.getStatus() != JourneyStatus.CANCELLED)
                .collect(Collectors.toSet());

        DisruptionType type = toDisruptionType(change.getNewStatus());
        DisruptionSeverity severity = calculateSeverity(change.getNewStatus(), delayMinutes);

        List<DisruptionEvent> createdEvents = new ArrayList<>();

        if (affectedJourneys.isEmpty()) {
            // Schedule-level disruption event if no active journeys
            DisruptionEvent event = createDisruptionEventForSchedule(schedule, null, change, type, severity);
            if (event != null) {
                createdEvents.add(event);
            }
        } else {
            // Per-journey disruption events
            for (Journey journey : affectedJourneys) {
                DisruptionEvent event = createDisruptionEventForSchedule(schedule, journey, change, type, severity);
                if (event != null) {
                    createdEvents.add(event);

                    // Update journey status to DISRUPTED for real disruptions
                    if (journey.getStatus() != JourneyStatus.DISRUPTED) {
                        journey.setStatus(JourneyStatus.DISRUPTED);
                        journeyRepository.save(journey);
                        log.info("[DisruptionDetection] Updated journey id={} status to DISRUPTED.", journey.getId());
                    }
                }
            }
        }

        if (createdEvents.isEmpty()) {
            return null;
        }

        DisruptionEvent primaryEvent = createdEvents.get(0);

        // 5. Trigger Recommendation Workflow for Eligible Disruptions
        boolean isEligible = isEligibleForRecommendation(type, severity, delayMinutes);
        if (isEligible) {
            for (DisruptionEvent ev : createdEvents) {
                recommendationWorkflowTrigger.triggerWorkflow(ev);
            }
        }

        return primaryEvent;
    }

    /**
     * Updates the status of a disruption event (DETECTED, PROCESSING, RESOLVED, FAILED).
     */
    @Transactional
    public DisruptionEvent updateEventStatus(Long eventId, DisruptionStatus newStatus) {
        DisruptionEvent event = disruptionEventRepository.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("[DisruptionDetection] Cannot update status — event id={} not found.", eventId);
            return null;
        }

        log.info("[DisruptionDetection] Event id={} status change: {} -> {}", eventId, event.getStatus(), newStatus);
        event.setStatus(newStatus);
        if (newStatus == DisruptionStatus.RESOLVED) {
            event.setResolved(true);
            event.setResolvedAt(Instant.now());
        }
        return disruptionEventRepository.save(event);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ──────────────────────────────────────────────────────────────────────

    private DisruptionEvent handlePlatformChange(TrainSchedule schedule, MonitoredStatusChange change) {
        log.info("[DisruptionDetection] Platform change detected on schedule {} ({}): platform={}. Creating informational event.",
                change.getScheduleId(), change.getTrainNumber(), change.getPlatform());

        List<Booking> bookings = bookingRepository.findByScheduleAndStatus(schedule, BookingStatus.CONFIRMED);
        Set<Journey> journeys = bookings.stream()
                .map(Booking::getJourney)
                .filter(j -> j != null && j.getStatus() != JourneyStatus.COMPLETED && j.getStatus() != JourneyStatus.CANCELLED)
                .collect(Collectors.toSet());

        String description = "Platform change notice for Train " + change.getTrainNumber() + ": Platform " + change.getPlatform() + ".";

        if (journeys.isEmpty()) {
            return createPlatformChangeEvent(schedule, null, change, description);
        }

        DisruptionEvent first = null;
        for (Journey journey : journeys) {
            DisruptionEvent ev = createPlatformChangeEvent(schedule, journey, change, description);
            if (first == null) first = ev;
        }
        return first;
    }

    private DisruptionEvent createPlatformChangeEvent(TrainSchedule schedule, Journey journey, MonitoredStatusChange change, String description) {
        // Prevent duplicate platform change events if open
        if (hasOpenDisruption(schedule, journey)) {
            return null;
        }

        DisruptionEvent event = DisruptionEvent.builder()
                .schedule(schedule)
                .journey(journey)
                .type(DisruptionType.PLATFORM_CHANGE)
                .severity(DisruptionSeverity.LOW)
                .status(DisruptionStatus.DETECTED)
                .detectedAt(change.getDetectedAt() != null ? change.getDetectedAt() : Instant.now())
                .description(description)
                .estimatedDelayMinutes(0)
                .trainNumber(change.getTrainNumber())
                .platform(change.getPlatform())
                .resolved(false)
                .rebookingTriggered(false)
                .build();

        return disruptionEventRepository.save(event);
    }

    private DisruptionEvent createDisruptionEventForSchedule(TrainSchedule schedule, Journey journey,
                                                            MonitoredStatusChange change,
                                                            DisruptionType type,
                                                            DisruptionSeverity severity) {
        // Duplicate guard: Prevent duplicate open disruption events for same condition
        if (hasOpenDisruption(schedule, journey)) {
            log.info("[DisruptionDetection] Schedule {} (journey={}) already has an open disruption event — skipping duplicate creation.",
                    schedule.getId(), journey != null ? journey.getId() : "N/A");
            return null;
        }

        String description = buildDescription(change, severity);

        DisruptionEvent event = DisruptionEvent.builder()
                .schedule(schedule)
                .journey(journey)
                .type(type)
                .severity(severity)
                .status(DisruptionStatus.DETECTED)
                .detectedAt(change.getDetectedAt() != null ? change.getDetectedAt() : Instant.now())
                .description(description)
                .estimatedDelayMinutes(change.getDelayMinutes())
                .trainNumber(change.getTrainNumber())
                .platform(change.getPlatform())
                .resolved(false)
                .rebookingTriggered(false)
                .build();

        DisruptionEvent saved = disruptionEventRepository.save(event);
        log.info("[DisruptionDetection] DisruptionEvent created: id={} scheduleId={} journeyId={} type={} severity={} status={}",
                saved.getId(), schedule.getId(), journey != null ? journey.getId() : null, type, severity, saved.getStatus());

        // Phase 17: DISRUPTION_ALERT notification for the journey owner
        if (journey != null && journey.getUser() != null) {
            try {
                String trainName = schedule.getTrain() != null
                        ? schedule.getTrain().getTrainName() : change.getTrainNumber();
                String schedDate = schedule.getScheduledDate() != null
                        ? schedule.getScheduledDate().toString() : "N/A";
                notificationService.notifyDisruptionAlert(
                        journey.getUser(), saved.getId(),
                        trainName, schedDate, description);
            } catch (Exception ex) {
                log.warn("[Notification] Could not create disruption-alert notification: {}", ex.getMessage());
            }
        }

        return saved;
    }

    private boolean hasOpenDisruption(TrainSchedule schedule, Journey journey) {
        if (journey != null) {
            List<DisruptionEvent> openEvents = disruptionEventRepository
                    .findByScheduleAndJourneyAndStatusNotIn(schedule, journey, INACTIVE_STATUSES);
            return !openEvents.isEmpty();
        } else {
            List<DisruptionEvent> openEvents = disruptionEventRepository
                    .findByScheduleAndStatusNotIn(schedule, INACTIVE_STATUSES);
            return !openEvents.isEmpty();
        }
    }

    public DisruptionSeverity calculateSeverity(TrainStatus status, int delayMinutes) {
        if (status == TrainStatus.CANCELLED || status == TrainStatus.DIVERTED) {
            return DisruptionSeverity.CRITICAL;
        }
        if (delayMinutes >= properties.getHighDelayMinutes()) {
            return DisruptionSeverity.HIGH;
        }
        if (delayMinutes >= properties.getMediumDelayMinutes()) {
            return DisruptionSeverity.MEDIUM;
        }
        return DisruptionSeverity.LOW;
    }

    private boolean isEligibleForRecommendation(DisruptionType type, DisruptionSeverity severity, int delayMinutes) {
        if (type == DisruptionType.PLATFORM_CHANGE) {
            return false;
        }
        if (type == DisruptionType.DELAY && delayMinutes < properties.getMinDelayMinutes()) {
            return false;
        }
        List<String> eligibleTypes = properties.getEligibleForRecommendationTypes();
        return eligibleTypes.contains(type.name());
    }

    private DisruptionType toDisruptionType(TrainStatus status) {
        return switch (status) {
            case DELAYED          -> DisruptionType.DELAY;
            case CANCELLED        -> DisruptionType.CANCELLATION;
            case DIVERTED         -> DisruptionType.DIVERSION;
            case PLATFORM_CHANGED -> DisruptionType.PLATFORM_CHANGE;
            default               -> DisruptionType.UNKNOWN;
        };
    }

    private String buildDescription(MonitoredStatusChange change, DisruptionSeverity severity) {
        return switch (change.getNewStatus()) {
            case DELAYED   -> "Train " + change.getTrainNumber() + " is delayed by "
                    + change.getDelayMinutes() + " minute(s). Severity: " + severity + ".";
            case CANCELLED -> "Train " + change.getTrainNumber() + " has been CANCELLED.";
            case DIVERTED  -> "Train " + change.getTrainNumber() + " has been diverted.";
            default        -> "Train " + change.getTrainNumber()
                    + " status changed to " + change.getNewStatus() + ".";
        };
    }
}

package com.trainconcierge.admin.simulation;

import com.trainconcierge.admin.simulation.dto.*;
import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.disruption.*;
import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.monitoring.DisruptionDetectionService;
import com.trainconcierge.monitoring.MonitoredStatusChange;
import com.trainconcierge.monitoring.TrainMonitoringScheduler;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.simulation.SimulatedTrainStatusResponse;
import com.trainconcierge.simulation.TrainStatusPort;
import com.trainconcierge.simulation.UpdateSimulatedStatusRequest;
import com.trainconcierge.train.TrainStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin Simulation Control Service — the single point of truth for all
 * admin-driven simulation actions in Phase 19.
 *
 * <p><strong>Design constraints enforced here:</strong></p>
 * <ul>
 *   <li>All status changes are persisted via {@link TrainStatusPort} — never raw SQL.</li>
 *   <li>Duplicate disruption guard is delegated to {@link DisruptionDetectionService}
 *       — we never bypass it.</li>
 *   <li>Monitoring cycle triggers are synchronous and return cycle stats.</li>
 *   <li>Every admin action is logged with {@code [ADMIN-SIM]} prefix for auditability.</li>
 *   <li>No endpoint can bypass the existing {@code ROLE_ADMIN} security layer.</li>
 * </ul>
 *
 * <p><strong>SIMULATED SERVICE</strong> — no real railway provider is contacted.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSimulationControlService {

    private final TrainStatusPort trainStatusPort;
    private final TrainMonitoringScheduler monitoringScheduler;
    private final DisruptionDetectionService disruptionDetectionService;
    private final JourneyRepository journeyRepository;
    private final BookingRepository bookingRepository;
    private final DisruptionEventRepository disruptionEventRepository;

    private static final List<DisruptionStatus> INACTIVE_STATUSES =
            List.of(DisruptionStatus.RESOLVED, DisruptionStatus.FAILED);

    // ──────────────────────────────────────────────────────────────────────
    // 1. Trigger Delay
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Simulates a train delay.
     *
     * <p>Persists a {@code TrainStatusHistory} entry and updates the live
     * {@code TrainSchedule}. Does NOT directly create a disruption event —
     * that is handled by the monitoring cycle or a follow-up disruption
     * evaluation call.</p>
     *
     * @param request delay parameters
     * @return updated train status response
     */
    @Transactional
    public SimulatedTrainStatusResponse triggerDelay(TriggerDelayRequest request) {
        log.info("[ADMIN-SIM] Triggering DELAY — scheduleId={} delayMinutes={} reason={}",
                request.getScheduleId(), request.getDelayMinutes(), request.getReason());

        UpdateSimulatedStatusRequest updateRequest = UpdateSimulatedStatusRequest.builder()
                .status(TrainStatus.DELAYED)
                .delayMinutes(request.getDelayMinutes())
                .message(request.getReason() != null ? request.getReason()
                        : "[ADMIN SIMULATION] Manual delay triggered.")
                .build();

        SimulatedTrainStatusResponse response =
                trainStatusPort.updateStatus(request.getScheduleId(), updateRequest);

        log.info("[ADMIN-SIM] DELAY applied — scheduleId={} status={} delayMinutes={}",
                request.getScheduleId(), response.getStatus(), response.getDelayMinutes());

        return response;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. Trigger Cancellation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Simulates a train cancellation.
     *
     * <p>Marks the schedule as CANCELLED in both {@code TrainStatusHistory}
     * and the live {@code TrainSchedule}.</p>
     *
     * @param request cancellation parameters
     * @return updated train status response
     */
    @Transactional
    public SimulatedTrainStatusResponse triggerCancellation(TriggerCancellationRequest request) {
        log.info("[ADMIN-SIM] Triggering CANCELLATION — scheduleId={} reason={}",
                request.getScheduleId(), request.getReason());

        UpdateSimulatedStatusRequest updateRequest = UpdateSimulatedStatusRequest.builder()
                .status(TrainStatus.CANCELLED)
                .message(request.getReason() != null ? request.getReason()
                        : "[ADMIN SIMULATION] Manual cancellation triggered.")
                .build();

        SimulatedTrainStatusResponse response =
                trainStatusPort.updateStatus(request.getScheduleId(), updateRequest);

        log.info("[ADMIN-SIM] CANCELLATION applied — scheduleId={} status={}",
                request.getScheduleId(), response.getStatus());

        return response;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. Restore Normal Status
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Restores a schedule to ON_TIME, clearing any delay.
     *
     * <p>Useful after a simulated delay or cancellation to reset the
     * schedule for re-testing without re-creating data.</p>
     *
     * @param request restore parameters
     * @return updated train status response
     */
    @Transactional
    public SimulatedTrainStatusResponse restoreNormal(RestoreNormalStatusRequest request) {
        log.info("[ADMIN-SIM] Restoring NORMAL status — scheduleId={} reason={}",
                request.getScheduleId(), request.getReason());

        UpdateSimulatedStatusRequest updateRequest = UpdateSimulatedStatusRequest.builder()
                .status(TrainStatus.ON_TIME)
                .delayMinutes(0)
                .message(request.getReason() != null ? request.getReason()
                        : "[ADMIN SIMULATION] Normal status restored.")
                .build();

        SimulatedTrainStatusResponse response =
                trainStatusPort.updateStatus(request.getScheduleId(), updateRequest);

        log.info("[ADMIN-SIM] NORMAL status restored — scheduleId={} status={}",
                request.getScheduleId(), response.getStatus());

        return response;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4. Trigger Monitoring Cycle
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Synchronously runs one full monitoring cycle and returns cycle statistics.
     *
     * <p>The monitoring cache is intentionally NOT cleared before the cycle —
     * clearing it would cause every schedule to be treated as "first observation"
     * which would suppress all disruption detection for that run. Admins who
     * want a fresh baseline can call the explicit cache-clear endpoint first.</p>
     *
     * <p>Idempotent: calling this multiple times without status changes will
     * detect 0 changes (the cache comparison prevents re-triggering).</p>
     *
     * @return cycle statistics
     */
    public MonitoringCycleTriggerResponse triggerMonitoringCycle() {
        log.info("[ADMIN-SIM] Manual monitoring cycle TRIGGERED.");
        Instant start = Instant.now();

        monitoringScheduler.runMonitoringCycle();

        Instant end = Instant.now();

        int journeys  = monitoringScheduler.getLastCycleJourneys();
        int schedules = monitoringScheduler.getLastCycleSchedules();
        int changes   = monitoringScheduler.getLastCycleChangesFound();

        String summary = String.format(
                "Monitoring cycle completed: %d journey(s) evaluated, %d schedule(s) polled, %d change(s) detected.",
                journeys, schedules, changes);

        log.info("[ADMIN-SIM] Manual monitoring cycle COMPLETE — {}", summary);

        return MonitoringCycleTriggerResponse.builder()
                .cycleStartedAt(start)
                .cycleCompletedAt(end)
                .journeysEvaluated(journeys)
                .schedulesPolled(schedules)
                .changesDetected(changes)
                .summary(summary)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // 5. Trigger Disruption Evaluation for a Journey
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Forces disruption detection for the confirmed bookings of a given journey,
     * without waiting for the next automatic monitoring cycle.
     *
     * <p><strong>Duplicate guard:</strong> If the journey already has any
     * open (non-resolved, non-failed) disruption events, the evaluation is
     * skipped — this prevents duplicate rebooking and duplicate alerts.</p>
     *
     * <p>The method passes the current live status of the schedule through the
     * full {@link DisruptionDetectionService#handle(MonitoredStatusChange)} pipeline,
     * so all downstream effects (notifications, recommendation workflow) apply
     * exactly as they would in a real monitoring cycle.</p>
     *
     * @param request containing the journeyId to evaluate
     * @return evaluation result with disruption details or skip reason
     */
    @Transactional
    public DisruptionEvaluationResponse triggerDisruptionEvaluation(
            TriggerDisruptionEvaluationRequest request) {

        log.info("[ADMIN-SIM] Disruption evaluation TRIGGERED for journeyId={}",
                request.getJourneyId());

        Journey journey = journeyRepository.findById(request.getJourneyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journey", "id", request.getJourneyId()));

        // Guard: only evaluate PLANNED / IN_PROGRESS / DISRUPTED journeys
        if (journey.getStatus() == JourneyStatus.COMPLETED
                || journey.getStatus() == JourneyStatus.CANCELLED) {
            String msg = "Journey id=" + request.getJourneyId()
                    + " is in terminal status " + journey.getStatus()
                    + " — disruption evaluation not applicable.";
            log.warn("[ADMIN-SIM] {}", msg);
            return DisruptionEvaluationResponse.builder()
                    .journeyId(request.getJourneyId())
                    .skipped(true)
                    .skipReason(msg)
                    .evaluatedAt(Instant.now())
                    .summary(msg)
                    .build();
        }

        // Find the confirmed booking to get the schedule
        List<Booking> confirmedBookings = bookingRepository.findByJourney(journey)
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .collect(Collectors.toList());

        if (confirmedBookings.isEmpty()) {
            String msg = "Journey id=" + request.getJourneyId()
                    + " has no confirmed bookings — nothing to evaluate.";
            log.warn("[ADMIN-SIM] {}", msg);
            return DisruptionEvaluationResponse.builder()
                    .journeyId(request.getJourneyId())
                    .skipped(true)
                    .skipReason(msg)
                    .evaluatedAt(Instant.now())
                    .summary(msg)
                    .build();
        }

        // Use the first confirmed booking's schedule (primary leg)
        TrainSchedule schedule = confirmedBookings.get(0).getSchedule();
        Long scheduleId = schedule.getId();

        // Duplicate guard: check for existing open disruption events for this journey
        List<DisruptionEvent> openEvents = disruptionEventRepository
                .findByScheduleAndJourneyAndStatusNotIn(schedule, journey, INACTIVE_STATUSES);

        if (!openEvents.isEmpty()) {
            String msg = "Journey id=" + request.getJourneyId()
                    + " already has " + openEvents.size()
                    + " open disruption event(s) — skipping to prevent duplicate rebooking.";
            log.warn("[ADMIN-SIM] {}", msg);
            return DisruptionEvaluationResponse.builder()
                    .journeyId(request.getJourneyId())
                    .scheduleId(scheduleId)
                    .skipped(true)
                    .skipReason(msg)
                    .disruptionEventId(openEvents.get(0).getId())
                    .disruptionType(openEvents.get(0).getType())
                    .severity(openEvents.get(0).getSeverity())
                    .disruptionStatus(openEvents.get(0).getStatus())
                    .evaluatedAt(Instant.now())
                    .summary(msg)
                    .build();
        }

        // Fetch current live status from the port
        SimulatedTrainStatusResponse statusResponse =
                trainStatusPort.getLatestStatus(scheduleId);

        TrainStatus currentStatus = statusResponse.getStatus();

        // Build a MonitoredStatusChange treating current status as a new detection
        // (previousStatus = ON_TIME as baseline so all non-ON_TIME statuses are "changes")
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(scheduleId)
                .trainNumber(statusResponse.getTrainNumber())
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(currentStatus)
                .delayMinutes(statusResponse.getDelayMinutes() != null
                        ? statusResponse.getDelayMinutes() : 0)
                .platform(statusResponse.getPlatform())
                .detectedAt(Instant.now())
                .build();

        // Delegate to the full disruption detection pipeline
        DisruptionEvent event = disruptionDetectionService.handle(change);

        if (event == null) {
            String msg = "No disruptive condition detected for schedule id=" + scheduleId
                    + " (status=" + currentStatus + ") — no disruption event created.";
            log.info("[ADMIN-SIM] {}", msg);
            return DisruptionEvaluationResponse.builder()
                    .journeyId(request.getJourneyId())
                    .scheduleId(scheduleId)
                    .skipped(false)
                    .recommendationTriggered(false)
                    .evaluatedAt(Instant.now())
                    .summary(msg)
                    .build();
        }

        String summary = "Disruption evaluation complete — DisruptionEvent id=" + event.getId()
                + " type=" + event.getType() + " severity=" + event.getSeverity()
                + " created for journey id=" + request.getJourneyId() + ".";
        log.info("[ADMIN-SIM] {}", summary);

        return DisruptionEvaluationResponse.builder()
                .journeyId(request.getJourneyId())
                .scheduleId(scheduleId)
                .skipped(false)
                .disruptionEventId(event.getId())
                .disruptionType(event.getType())
                .severity(event.getSeverity())
                .disruptionStatus(event.getStatus())
                .recommendationTriggered(true)
                .evaluatedAt(Instant.now())
                .summary(summary)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // 6. Clear Monitoring Cache (test/admin utility)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Clears the in-memory status cache of the monitoring scheduler.
     *
     * <p>This resets the "last known status" baseline so the next monitoring
     * cycle will treat all schedules as first-observation (i.e. no changes
     * detected on that first run). Useful when test data is rebuilt between
     * Postman scenarios.</p>
     *
     * @return summary message
     */
    public String clearMonitoringCache() {
        log.info("[ADMIN-SIM] Admin cleared monitoring status cache.");
        monitoringScheduler.clearStatusCache();
        return "Monitoring status cache cleared. The next cycle will treat all schedules as first observation.";
    }
}

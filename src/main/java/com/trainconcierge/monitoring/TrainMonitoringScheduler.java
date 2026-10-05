package com.trainconcierge.monitoring;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.simulation.SimulatedTrainStatusResponse;
import com.trainconcierge.simulation.TrainStatusPort;
import com.trainconcierge.train.TrainStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Spring Scheduler that monitors active journeys for train status changes.
 *
 * <p><strong>Monitoring cycle (per execution):</strong></p>
 * <ol>
 *   <li>Load all {@code PLANNED} / {@code IN_PROGRESS} journeys with a
 *       travel date of today or later.</li>
 *   <li>For each journey, find its confirmed bookings and their schedules.</li>
 *   <li>Query {@link TrainStatusPort} for the current status of each unique
 *       schedule (deduplicated across journeys).</li>
 *   <li>Compare with the status last seen in the in-memory state cache.</li>
 *   <li>If the status changed, delegate to {@link DisruptionDetectionService}.</li>
 *   <li>Update the in-memory cache for next cycle comparison.</li>
 * </ol>
 *
 * <p><strong>Design constraints:</strong></p>
 * <ul>
 *   <li>No recommendation or rebooking logic lives here.</li>
 *   <li>Uses {@code fixedDelay} — next cycle starts only after current one
 *       completes, preventing overlapping runs.</li>
 *   <li>Individual schedule failures are caught and logged; they do not abort
 *       the cycle for other schedules.</li>
 *   <li>Can be disabled with {@code monitoring.enabled=false}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainMonitoringScheduler {

    private final JourneyRepository journeyRepository;
    private final BookingRepository bookingRepository;
    private final TrainStatusPort trainStatusPort;
    private final DisruptionDetectionService disruptionDetectionService;

    /**
     * Enable / disable the entire monitoring cycle.
     * Set {@code monitoring.enabled=false} in application.properties to suppress
     * all polling (useful during local development when no train data exists).
     */
    @Value("${monitoring.enabled:true}")
    private boolean monitoringEnabled;

    /**
     * In-memory map: scheduleId → last known TrainStatus.
     *
     * <p>This is intentionally a simple in-memory cache rather than a DB column
     * because:
     * <ul>
     *   <li>Status history is already persisted via {@code TrainStatusHistory}.</li>
     *   <li>A restart resets the cache — the first post-restart poll that detects a
     *       change will correctly re-trigger disruption detection.</li>
     *   <li>Avoids an extra schema change.</li>
     * </ul>
     * </p>
     */
    private final Map<Long, TrainStatus> lastKnownStatus = new HashMap<>();

    /**
     * Cycle statistics — exposed for testing and observability.
     * Volatile so tests on different threads see the latest values.
     */
    private volatile int lastCycleJourneys      = 0;
    private volatile int lastCycleSchedules     = 0;
    private volatile int lastCycleChangesFound  = 0;
    private volatile Instant lastCycleAt        = null;

    // ──────────────────────────────────────────────────────────────────────
    // Scheduler entry point
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Main monitoring loop. Runs every {@code monitoring.interval-ms} milliseconds
     * after the previous execution completes.
     */
    @Scheduled(fixedDelayString = "${monitoring.interval-ms:30000}")
    public void runMonitoringCycle() {
        if (!monitoringEnabled) {
            log.debug("[TrainMonitor] Monitoring is disabled — skipping cycle.");
            return;
        }

        Instant cycleStart = Instant.now();
        log.info("[TrainMonitor] ── Monitoring cycle START ──────────────────────────────────────");

        List<Journey> activeJourneys = journeyRepository
                .findActiveJourneysForMonitoring(LocalDate.now());

        if (activeJourneys.isEmpty()) {
            log.info("[TrainMonitor] No active journeys to monitor. Cycle complete.");
            updateCycleStats(cycleStart, 0, 0, 0);
            return;
        }

        log.info("[TrainMonitor] {} active journey/journeys loaded for monitoring.",
                activeJourneys.size());

        // Collect unique schedule IDs across all journeys (avoid repeated polling)
        Set<Long> scheduleIds = collectScheduleIds(activeJourneys);
        log.info("[TrainMonitor] Evaluating {} unique schedule(s).", scheduleIds.size());

        int changesDetected = 0;
        for (Long scheduleId : scheduleIds) {
            try {
                if (evaluateSchedule(scheduleId)) {
                    changesDetected++;
                }
            } catch (Exception ex) {
                // Do NOT let one schedule failure terminate the cycle
                log.error("[TrainMonitor] Error evaluating schedule id={}: {} — continuing cycle.",
                        scheduleId, ex.getMessage(), ex);
            }
        }

        updateCycleStats(cycleStart, activeJourneys.size(), scheduleIds.size(), changesDetected);
        log.info("[TrainMonitor] ── Monitoring cycle END — journeys={} schedules={} changes={} elapsed={}ms ──",
                activeJourneys.size(), scheduleIds.size(), changesDetected,
                java.time.Duration.between(cycleStart, Instant.now()).toMillis());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Core evaluation logic
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Evaluates a single schedule.
     *
     * @return true if a status change was detected and delegated
     */
    boolean evaluateSchedule(Long scheduleId) {
        SimulatedTrainStatusResponse current;
        try {
            current = trainStatusPort.getLatestStatus(scheduleId);
        } catch (Exception ex) {
            log.warn("[TrainMonitor] Could not retrieve status for schedule {}: {}",
                    scheduleId, ex.getMessage());
            return false;
        }

        TrainStatus newStatus = current.getStatus();

        // First observation — record and continue without triggering detection
        if (!lastKnownStatus.containsKey(scheduleId)) {
            lastKnownStatus.put(scheduleId, newStatus);
            log.debug("[TrainMonitor] First observation for schedule {} — status={}. Cached.",
                    scheduleId, newStatus);
            return false;
        }

        TrainStatus previousStatus = lastKnownStatus.get(scheduleId);

        // No change — skip
        if (previousStatus == newStatus) {
            log.debug("[TrainMonitor] Schedule {} status unchanged: {}.", scheduleId, newStatus);
            return false;
        }

        // Status changed — build change event and delegate
        log.info("[TrainMonitor] Status change on schedule {} ({}): {} → {}",
                scheduleId, current.getTrainNumber(), previousStatus, newStatus);

        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(scheduleId)
                .trainNumber(current.getTrainNumber())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .delayMinutes(current.getDelayMinutes() != null ? current.getDelayMinutes() : 0)
                .platform(current.getPlatform())
                .detectedAt(Instant.now())
                .build();

        // Update cache before delegating (so a detection failure doesn't re-trigger)
        lastKnownStatus.put(scheduleId, newStatus);

        // Delegate — no recommendation/rebooking logic here
        disruptionDetectionService.handle(change);

        return true;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Collects all distinct schedule IDs from confirmed bookings linked to the
     * supplied journeys. Standalone bookings (no journey) are not included.
     */
    private Set<Long> collectScheduleIds(List<Journey> journeys) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Journey j : journeys) {
            List<Booking> bookings = bookingRepository.findByJourney(j);
            for (Booking b : bookings) {
                if (b.getStatus() == BookingStatus.CONFIRMED) {
                    ids.add(b.getSchedule().getId());
                }
            }
        }
        return ids;
    }

    private void updateCycleStats(Instant start, int journeys, int schedules, int changes) {
        lastCycleAt       = start;
        lastCycleJourneys = journeys;
        lastCycleSchedules = schedules;
        lastCycleChangesFound = changes;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Accessors for tests / actuator
    // ──────────────────────────────────────────────────────────────────────

    public int getLastCycleJourneys()     { return lastCycleJourneys; }
    public int getLastCycleSchedules()    { return lastCycleSchedules; }
    public int getLastCycleChangesFound() { return lastCycleChangesFound; }
    public Instant getLastCycleAt()       { return lastCycleAt; }
    public Map<Long, TrainStatus> getLastKnownStatusSnapshot() {
        return Collections.unmodifiableMap(lastKnownStatus);
    }

    /** Test/admin hook — clears the in-memory status cache. */
    public void clearStatusCache() {
        lastKnownStatus.clear();
        log.info("[TrainMonitor] Status cache cleared.");
    }
}

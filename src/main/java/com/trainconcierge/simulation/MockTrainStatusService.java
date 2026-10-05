package com.trainconcierge.simulation;

import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.train.TrainStatus;
import com.trainconcierge.train.TrainStatusHistory;
import com.trainconcierge.train.TrainStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Mock implementation of {@link TrainStatusPort}.
 *
 * <p><strong>SIMULATED SERVICE</strong> — no real railway data provider is
 * contacted. All status changes are driven exclusively by admin API calls.
 * Status is never changed autonomously or randomly.</p>
 *
 * <p>Isolation guarantee: this service has no dependency on the recommendation
 * or monitoring packages. The disruption engine interacts with it only through
 * the {@link TrainStatusPort} interface.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockTrainStatusService implements TrainStatusPort {

    private final TrainScheduleRepository scheduleRepository;
    private final TrainStatusHistoryRepository historyRepository;

    // ──────────────────────────────────────────────────────────────────────
    // Read operations
    // ──────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SimulatedTrainStatusResponse getLatestStatus(Long scheduleId) {
        TrainSchedule schedule = loadSchedule(scheduleId);

        List<TrainStatusHistory> history =
                historyRepository.findByScheduleOrderByRecordedAtDesc(schedule);

        Optional<TrainStatusHistory> latest = history.isEmpty()
                ? Optional.empty()
                : Optional.of(history.get(0));

        TrainStatus currentStatus = latest
                .map(TrainStatusHistory::getStatus)
                .orElse(toTrainStatus(schedule.getScheduleStatus()));

        String platform = latest
                .map(TrainStatusHistory::getPlatform)
                .filter(p -> p != null && !p.isBlank())
                .orElse(schedule.getPlatform());

        Integer delay = latest
                .map(TrainStatusHistory::getDelayMinutes)
                .orElse(schedule.getDelayMinutes());

        Instant lastUpdatedAt = latest
                .map(TrainStatusHistory::getRecordedAt)
                .orElse(schedule.getUpdatedAt());

        log.debug("[SIMULATED] getLatestStatus scheduleId={} status={}", scheduleId, currentStatus);

        return SimulatedTrainStatusResponse.builder()
                .scheduleId(scheduleId)
                .trainNumber(schedule.getTrain().getTrainNumber())
                .trainName(schedule.getTrain().getTrainName())
                .scheduledDate(schedule.getScheduledDate())
                .scheduledDeparture(schedule.getScheduledDeparture())
                .scheduledArrival(schedule.getScheduledArrival())
                .status(currentStatus)
                .delayMinutes(delay)
                .platform(platform)
                .message(latest.map(TrainStatusHistory::getMessage).orElse(null))
                .lastUpdatedAt(lastUpdatedAt)
                .hasHistory(!history.isEmpty())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulatedStatusHistoryEntry> getStatusHistory(Long scheduleId) {
        TrainSchedule schedule = loadSchedule(scheduleId);

        return historyRepository.findByScheduleOrderByRecordedAtDesc(schedule)
                .stream()
                .map(h -> SimulatedStatusHistoryEntry.builder()
                        .id(h.getId())
                        .status(h.getStatus())
                        .delayMinutes(h.getDelayMinutes())
                        .platform(h.getPlatform())
                        .message(h.getMessage())
                        .recordedAtStation(h.getRecordedAtStation())
                        .recordedAt(h.getRecordedAt())
                        .simulated(h.isSimulated())
                        .build())
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Write operation (admin-only — never automatic)
    // ──────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SimulatedTrainStatusResponse updateStatus(Long scheduleId,
                                                      UpdateSimulatedStatusRequest request) {
        TrainSchedule schedule = loadSchedule(scheduleId);

        TrainStatus newStatus = request.getStatus();
        validate(newStatus, request);

        int delayMinutes = resolveDelay(newStatus, request);
        String platform = resolvePlatform(newStatus, request, schedule);

        // Persist the history entry (append-only)
        TrainStatusHistory historyEntry = TrainStatusHistory.builder()
                .schedule(schedule)
                .status(newStatus)
                .delayMinutes(delayMinutes)
                .platform(platform)
                .message(request.getMessage())
                .recordedAt(Instant.now())
                .simulated(true)
                .build();
        historyRepository.save(historyEntry);

        // Sync the live TrainSchedule fields so other modules see the latest state
        schedule.setScheduleStatus(toScheduleStatus(newStatus));
        schedule.setDelayMinutes(delayMinutes);
        schedule.setCancelled(newStatus == TrainStatus.CANCELLED);
        if (platform != null) {
            schedule.setPlatform(platform);
        }
        scheduleRepository.save(schedule);

        log.info("[SIMULATED] Admin updated schedule id={} to status={} delay={}min platform={} " +
                        "(NO real railway provider contacted)",
                scheduleId, newStatus, delayMinutes, platform);

        return getLatestStatus(scheduleId);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private TrainSchedule loadSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TrainSchedule", "id", scheduleId));
    }

    private void validate(TrainStatus status, UpdateSimulatedStatusRequest req) {
        if (status == TrainStatus.DELAYED) {
            if (req.getDelayMinutes() == null || req.getDelayMinutes() < 1) {
                throw new BadRequestException(
                        "delayMinutes must be >= 1 when status is DELAYED.",
                        ErrorCode.SIMULATION_INVALID_DELAY);
            }
        }
        if (status == TrainStatus.PLATFORM_CHANGED) {
            if (req.getPlatform() == null || req.getPlatform().isBlank()) {
                throw new BadRequestException(
                        "platform must be provided when status is PLATFORM_CHANGED.",
                        ErrorCode.SIMULATION_INVALID_STATUS);
            }
        }
    }

    private int resolveDelay(TrainStatus status, UpdateSimulatedStatusRequest req) {
        return switch (status) {
            case DELAYED -> req.getDelayMinutes() != null ? req.getDelayMinutes() : 0;
            case ON_TIME, PLATFORM_CHANGED -> 0;
            default -> req.getDelayMinutes() != null ? req.getDelayMinutes() : 0;
        };
    }

    private String resolvePlatform(TrainStatus status,
                                    UpdateSimulatedStatusRequest req,
                                    TrainSchedule schedule) {
        if (req.getPlatform() != null && !req.getPlatform().isBlank()) {
            return req.getPlatform();
        }
        return schedule.getPlatform();
    }

    private TrainStatus toTrainStatus(ScheduleStatus ss) {
        return switch (ss) {
            case ON_TIME -> TrainStatus.ON_TIME;
            case DELAYED -> TrainStatus.DELAYED;
            case CANCELLED -> TrainStatus.CANCELLED;
            case DIVERTED -> TrainStatus.DIVERTED;
            default -> TrainStatus.UNKNOWN;
        };
    }

    private ScheduleStatus toScheduleStatus(TrainStatus ts) {
        return switch (ts) {
            case ON_TIME -> ScheduleStatus.ON_TIME;
            case DELAYED -> ScheduleStatus.DELAYED;
            case CANCELLED -> ScheduleStatus.CANCELLED;
            case PLATFORM_CHANGED -> ScheduleStatus.ON_TIME; // still on time, just different platform
            case DIVERTED -> ScheduleStatus.DIVERTED;
            default -> ScheduleStatus.SCHEDULED;
        };
    }
}

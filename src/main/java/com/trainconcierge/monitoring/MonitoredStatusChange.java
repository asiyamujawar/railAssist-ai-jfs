package com.trainconcierge.monitoring;

import com.trainconcierge.train.TrainStatus;
import lombok.*;

import java.time.Instant;

/**
 * Represents a detected status change for a single train schedule during
 * one monitoring cycle.
 *
 * <p>Passed from {@link TrainMonitoringScheduler} to
 * {@link DisruptionDetectionService} so that detection logic is fully
 * decoupled from the scheduler loop.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoredStatusChange {

    /** ID of the TrainSchedule that changed status. */
    private Long scheduleId;

    /** Train number (for log/notification clarity). */
    private String trainNumber;

    /** Status recorded during the previous monitoring cycle (or ON_TIME if first run). */
    private TrainStatus previousStatus;

    /** Freshly obtained status from TrainStatusPort. */
    private TrainStatus newStatus;

    /** Delay minutes from the current status response. */
    private int delayMinutes;

    /** Platform from the current status response. */
    private String platform;

    /** Timestamp when the monitor detected this change. */
    private Instant detectedAt;

    /**
     * Returns true when the status warrants disruption processing.
     * Platform changes alone are informational but still trigger notifications.
     */
    public boolean isDisruptive() {
        return newStatus == TrainStatus.DELAYED
                || newStatus == TrainStatus.CANCELLED
                || newStatus == TrainStatus.DIVERTED;
    }
}

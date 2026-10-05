package com.trainconcierge.simulation;

import com.trainconcierge.train.TrainStatus;

import java.util.List;

/**
 * Port (interface) for querying and updating the operational status of a
 * scheduled train run.
 *
 * <p>The {@link MockTrainStatusService} is the only current implementation.
 * A future real railway-data adapter (e.g. NTES, National Rail, Amtrak) MUST
 * implement this interface so the disruption engine never needs changing.</p>
 *
 * <p>Design rules:
 * <ol>
 *   <li>Never generate random status changes autonomously.</li>
 *   <li>Always persist every status change to the database.</li>
 *   <li>Keep this interface free of any UI / recommendation concerns.</li>
 * </ol>
 * </p>
 */
public interface TrainStatusPort {

    /**
     * Returns the latest simulated status for a given schedule.
     *
     * @param scheduleId the ID of the {@code TrainSchedule}
     * @return response DTO bearing a SIMULATED disclaimer
     */
    SimulatedTrainStatusResponse getLatestStatus(Long scheduleId);

    /**
     * Returns the full status history for a given schedule, newest first.
     *
     * @param scheduleId the ID of the {@code TrainSchedule}
     * @return list of history entries
     */
    List<SimulatedStatusHistoryEntry> getStatusHistory(Long scheduleId);

    /**
     * Admin-controlled status update — the only way status changes are made.
     * Persists a new {@code TrainStatusHistory} row and updates the live
     * {@code TrainSchedule} fields (delayMinutes, platform, cancelled,
     * scheduleStatus).
     *
     * @param scheduleId the ID of the {@code TrainSchedule}
     * @param request    the desired new status with supporting details
     * @return the updated status response
     */
    SimulatedTrainStatusResponse updateStatus(Long scheduleId,
                                               UpdateSimulatedStatusRequest request);
}

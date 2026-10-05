package com.trainconcierge.admin.simulation;

import com.trainconcierge.admin.simulation.dto.*;
import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.simulation.SimulatedTrainStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only REST controller for Phase 19 simulation control operations.
 *
 * <p>All endpoints are under {@code /api/admin/simulation/control} which is
 * protected at the URL level by {@code SecurityConfig} ({@code /api/admin/**}
 * requires {@code ROLE_ADMIN}) and additionally guarded by
 * {@code @PreAuthorize("hasRole('ADMIN')")} for defence-in-depth.</p>
 *
 * <p><strong>SIMULATED SERVICE</strong> — no real railway provider is ever contacted.
 * Every response body contains a disclaimer to that effect.</p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /trigger-delay}          — Simulate a train delay</li>
 *   <li>{@code POST /trigger-cancellation}   — Simulate a train cancellation</li>
 *   <li>{@code POST /restore-normal}         — Restore schedule to ON_TIME</li>
 *   <li>{@code POST /monitoring-cycle}       — Trigger one monitoring cycle</li>
 *   <li>{@code DELETE /monitoring-cache}     — Clear in-memory status cache</li>
 *   <li>{@code POST /disruption-evaluation}  — Evaluate a specific journey for disruption</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/simulation/control")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Simulation Control [ADMIN][SIMULATED]", description = "High-level admin control for triggering delays, cancellations, monitoring cycles, and disruption evaluation. Requires ROLE_ADMIN.")
public class AdminSimulationControlController {

    private final AdminSimulationControlService service;

    // ──────────────────────────────────────────────────────────────────────
    // [SIMULATED] Trigger Delay
    // ──────────────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/simulation/control/trigger-delay
     *
     * <p>Simulates a train delay for a given schedule. Persists a
     * {@code TrainStatusHistory} row and updates the live {@code TrainSchedule}.
     * Does NOT automatically trigger disruption detection — use the monitoring
     * cycle or disruption-evaluation endpoints for that.</p>
     */
    @Operation(summary = "Trigger train delay [ADMIN][SIMULATED]", description = "Simulates a train delay. Updates TrainSchedule and persists a TrainStatusHistory row. Does NOT auto-trigger disruption detection.")
    @PostMapping("/trigger-delay")
    public ResponseEntity<ApiResponse<SimulatedTrainStatusResponse>> triggerDelay(
            @Valid @RequestBody TriggerDelayRequest request) {

        SimulatedTrainStatusResponse response = service.triggerDelay(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "[SIMULATED] Train delay applied to schedule " + request.getScheduleId()
                        + ". No real railway provider was contacted.",
                response));
    }

    // ──────────────────────────────────────────────────────────────────────
    // [SIMULATED] Trigger Cancellation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/simulation/control/trigger-cancellation
     *
     * <p>Simulates a train cancellation. The {@code TrainSchedule} is flagged
     * as cancelled in the database.</p>
     */
    @Operation(summary = "Trigger train cancellation [ADMIN][SIMULATED]", description = "Simulates a train cancellation and marks the schedule as cancelled in the DB.")
    @PostMapping("/trigger-cancellation")
    public ResponseEntity<ApiResponse<SimulatedTrainStatusResponse>> triggerCancellation(
            @Valid @RequestBody TriggerCancellationRequest request) {

        SimulatedTrainStatusResponse response = service.triggerCancellation(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "[SIMULATED] Train cancellation applied to schedule " + request.getScheduleId()
                        + ". No real railway provider was contacted.",
                response));
    }

    // ──────────────────────────────────────────────────────────────────────
    // [SIMULATED] Restore Normal Status
    // ──────────────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/simulation/control/restore-normal
     *
     * <p>Restores a previously delayed or cancelled schedule to ON_TIME status,
     * clearing delay minutes. Useful between Postman demo iterations.</p>
     */
    @Operation(summary = "Restore train to ON_TIME [ADMIN][SIMULATED]", description = "Resets a delayed/cancelled schedule to ON_TIME status. Use between demo runs to reset state.")
    @PostMapping("/restore-normal")
    public ResponseEntity<ApiResponse<SimulatedTrainStatusResponse>> restoreNormal(
            @Valid @RequestBody RestoreNormalStatusRequest request) {

        SimulatedTrainStatusResponse response = service.restoreNormal(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "[SIMULATED] Schedule " + request.getScheduleId()
                        + " restored to ON_TIME. No real railway provider was contacted.",
                response));
    }

    // ──────────────────────────────────────────────────────────────────────
    // [SIMULATED] Trigger Monitoring Cycle
    // ──────────────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/simulation/control/monitoring-cycle
     *
     * <p>Synchronously runs one full monitoring cycle and returns cycle stats.
     * This is the same logic that the {@code @Scheduled} job executes
     * automatically every {@code monitoring.interval-ms} milliseconds —
     * this endpoint just allows triggering it on demand for testing.</p>
     *
     * <p>Idempotent with respect to disruption creation: consecutive calls
     * without a status change in between will detect 0 changes.</p>
     */
    @Operation(summary = "Trigger monitoring cycle [ADMIN][SIMULATED]", description = "Synchronously runs one full monitoring cycle and returns stats. Idempotent — consecutive calls without a status change detect 0 changes.")
    @PostMapping("/monitoring-cycle")
    public ResponseEntity<ApiResponse<MonitoringCycleTriggerResponse>> triggerMonitoringCycle() {

        MonitoringCycleTriggerResponse response = service.triggerMonitoringCycle();
        return ResponseEntity.ok(ApiResponse.ok(
                "[SIMULATED] Manual monitoring cycle completed. "
                        + "No real railway provider was contacted.",
                response));
    }

    // ──────────────────────────────────────────────────────────────────────
    // [SIMULATED] Clear Monitoring Cache
    // ──────────────────────────────────────────────────────────────────────

    /**
     * DELETE /api/admin/simulation/control/monitoring-cache
     *
     * <p>Clears the in-memory "last known status" cache used by the monitoring
     * scheduler. The next cycle will treat every schedule as a first observation
     * and will not trigger disruption detection for that run.</p>
     *
     * <p>Use this between Postman demo scenarios to reset the monitoring baseline.</p>
     */
    @Operation(summary = "Clear monitoring cache [ADMIN]", description = "Clears the in-memory last-known-status cache. The next monitoring cycle treats all schedules as first observations. Use at the start of each demo run.")
    @DeleteMapping("/monitoring-cache")
    public ResponseEntity<ApiResponse<String>> clearMonitoringCache() {
        String message = service.clearMonitoringCache();
        return ResponseEntity.ok(ApiResponse.ok(message));
    }

    // ──────────────────────────────────────────────────────────────────────
    // [SIMULATED] Trigger Disruption Evaluation for a Journey
    // ──────────────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/simulation/control/disruption-evaluation
     *
     * <p>Forces the disruption detection engine to immediately evaluate the
     * current train status for a specific journey's confirmed bookings, bypassing
     * the normal monitoring-cycle wait time.</p>
     *
     * <p>The duplicate guard in {@link AdminSimulationControlService} prevents
     * re-creating disruption events if one is already open for the same
     * journey + schedule combination, ensuring no duplicate rebooking or alerts.</p>
     */
    @Operation(summary = "Force disruption evaluation for a journey [ADMIN][SIMULATED]", description = "Immediately evaluates a journey for disruption, bypassing the monitoring cycle. Includes duplicate guard — returns skipped=true if an open disruption event already exists.")
    @PostMapping("/disruption-evaluation")
    public ResponseEntity<ApiResponse<DisruptionEvaluationResponse>> triggerDisruptionEvaluation(
            @Valid @RequestBody TriggerDisruptionEvaluationRequest request) {

        DisruptionEvaluationResponse response = service.triggerDisruptionEvaluation(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "[SIMULATED] Disruption evaluation completed for journey " + request.getJourneyId()
                        + ". No real railway provider was contacted.",
                response));
    }
}

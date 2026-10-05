package com.trainconcierge.simulation;

import com.trainconcierge.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only public endpoints for retrieving SIMULATED train status.
 * Requires authentication (JWT Bearer) but no admin role.
 *
 * <strong>SIMULATED SERVICE</strong> — all data is produced by
 * {@link MockTrainStatusService}. No real railway provider is contacted.
 */
@RestController
@RequestMapping("/api/simulation/trains")
@RequiredArgsConstructor
@Tag(name = "Simulation — Train Status [SIMULATED]", description = "Read-only endpoints for simulated train status data. No real railway provider is contacted.")
public class TrainSimulationController {

    private final TrainStatusPort trainStatusPort;

    /**
     * GET /api/simulation/trains/{scheduleId}/status
     * Returns the latest simulated status for the given schedule.
     */
    @Operation(summary = "Get latest simulated train status [SIMULATED]", description = "Returns the current simulated status of a train schedule (ON_TIME, DELAYED, CANCELLED).")
    @GetMapping("/{scheduleId}/status")
    public ResponseEntity<ApiResponse<SimulatedTrainStatusResponse>> getLatestStatus(
            @PathVariable Long scheduleId) {

        SimulatedTrainStatusResponse response = trainStatusPort.getLatestStatus(scheduleId);
        return ResponseEntity.ok(ApiResponse.ok(
                "[SIMULATED] Latest train status retrieved. No real railway provider was contacted.",
                response));
    }

    /**
     * GET /api/simulation/trains/{scheduleId}/history
     * Returns all historical status entries for the given schedule, newest first.
     */
    @Operation(summary = "Get simulated train status history [SIMULATED]", description = "Returns all historical status entries for a train schedule, newest first.")
    @GetMapping("/{scheduleId}/history")
    public ResponseEntity<ApiResponse<List<SimulatedStatusHistoryEntry>>> getStatusHistory(
            @PathVariable Long scheduleId) {

        List<SimulatedStatusHistoryEntry> history = trainStatusPort.getStatusHistory(scheduleId);
        return ResponseEntity.ok(ApiResponse.ok(
                "[SIMULATED] Train status history retrieved. No real railway provider was contacted.",
                history));
    }
}

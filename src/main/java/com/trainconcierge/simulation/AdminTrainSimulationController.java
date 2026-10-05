package com.trainconcierge.simulation;

import com.trainconcierge.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only endpoint to control the simulated train status.
 *
 * Maps to /api/admin/simulation/trains/** which is protected by the
 * ROLE_ADMIN security rule in SecurityConfig (/api/admin/** → ADMIN only).
 *
 * <strong>SIMULATED SERVICE</strong> — status changes are stored in the DB
 * but no real railway provider is ever contacted.
 */
@RestController
@RequestMapping("/api/admin/simulation/trains")
@RequiredArgsConstructor
@Tag(name = "Admin — Simulation Status [SIMULATED]", description = "Low-level admin control for directly updating simulated train status")
public class AdminTrainSimulationController {

    private final TrainStatusPort trainStatusPort;

    /**
     * PUT /api/admin/simulation/trains/{scheduleId}/status
     * Admin-controlled update of the simulated train status.
     * Persists a TrainStatusHistory row and updates the live TrainSchedule.
     */
    @Operation(summary = "Update simulated train status [ADMIN][SIMULATED]", description = "Directly sets the simulated status of a schedule (e.g., DELAYED, CANCELLED, ON_TIME). Prefer the higher-level /api/admin/simulation/control endpoints.")
    @PutMapping("/{scheduleId}/status")
    public ResponseEntity<ApiResponse<SimulatedTrainStatusResponse>> updateStatus(
            @PathVariable Long scheduleId,
            @Valid @RequestBody UpdateSimulatedStatusRequest request) {

        SimulatedTrainStatusResponse response = trainStatusPort.updateStatus(scheduleId, request);
        return ResponseEntity.ok(ApiResponse.ok(
                "[SIMULATED] Train status updated successfully. No real railway provider was contacted.",
                response));
    }
}

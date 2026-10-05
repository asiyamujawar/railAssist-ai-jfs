package com.trainconcierge.admin;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.schedule.TrainScheduleService;
import com.trainconcierge.schedule.dto.CreateScheduleRequest;
import com.trainconcierge.schedule.dto.ScheduleResponse;
import com.trainconcierge.seat.SeatAvailabilityService;
import com.trainconcierge.seat.dto.SeatAvailabilityResponse;
import com.trainconcierge.seat.dto.SeatAvailabilityUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/schedules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Schedules", description = "Admin-only schedule creation and seat availability management")
public class AdminScheduleController {

    private final TrainScheduleService trainScheduleService;
    private final SeatAvailabilityService seatAvailabilityService;

    @Operation(summary = "Create a train schedule [ADMIN]", description = "Creates a new train schedule and initialises seat availability records. Requires ROLE_ADMIN.")
    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> createSchedule(
            @Valid @RequestBody CreateScheduleRequest request) {

        ScheduleResponse response = trainScheduleService.createSchedule(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Schedule created successfully.", response));
    }

    @Operation(summary = "Update seat availability [ADMIN]", description = "Adjusts available seat counts by class for a given schedule. Requires ROLE_ADMIN.")
    @PatchMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<SeatAvailabilityResponse>> updateAvailability(
            @PathVariable Long id,
            @Valid @RequestBody SeatAvailabilityUpdateRequest request) {

        SeatAvailabilityResponse response = seatAvailabilityService.updateAvailability(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Seat availability updated successfully.", response));
    }
}

package com.trainconcierge.schedule;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.schedule.dto.PaginatedScheduleResponse;
import com.trainconcierge.schedule.dto.ScheduleResponse;
import com.trainconcierge.schedule.dto.ScheduleSearchResult;
import com.trainconcierge.seat.SeatAvailabilityService;
import com.trainconcierge.seat.dto.SeatAvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedules", description = "Browse, search, and view seat availability for train schedules")
public class ScheduleController {

    private final TrainScheduleService trainScheduleService;
    private final SeatAvailabilityService seatAvailabilityService;

    @Operation(summary = "List all schedules", description = "Paginated list of train schedules, sorted by scheduled date ascending by default.")
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedScheduleResponse>> getAllSchedules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "scheduledDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        if (size < 1) size = 10;
        if (size > 100) size = 100;
        if (page < 0) page = 0;

        PaginatedScheduleResponse response = trainScheduleService.getAllSchedules(
                page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.ok("Schedules retrieved successfully.", response));
    }

    @Operation(summary = "Get schedule by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> getScheduleById(@PathVariable Long id) {
        ScheduleResponse response = trainScheduleService.getScheduleById(id);
        return ResponseEntity.ok(ApiResponse.ok("Schedule retrieved successfully.", response));
    }

    @Operation(summary = "Search schedules by route and date", description = "Returns schedules matching origin station, destination station, and journey date.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> searchSchedules(
            @RequestParam String originStation,
            @RequestParam String destinationStation,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate) {

        List<ScheduleSearchResult> results = trainScheduleService.searchSchedules(
                originStation, destinationStation, journeyDate);

        String message = results.isEmpty()
                ? "No schedules found for the specified route and date."
                : "Search completed successfully. Found " + results.size() + " schedule(s).";

        return ResponseEntity.ok(ApiResponse.ok(message, results));
    }

    @Operation(summary = "Get seat availability for a schedule", description = "Returns available seat counts by class (FIRST, SECOND, SLEEPER) for the given schedule.")
    @GetMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<List<SeatAvailabilityResponse>>> getAvailability(
            @PathVariable Long id) {

        List<SeatAvailabilityResponse> response = seatAvailabilityService.getAvailabilityByScheduleId(id);
        return ResponseEntity.ok(ApiResponse.ok(
                "Seat availability retrieved successfully. Found " + response.size() + " class(es).",
                response));
    }
}

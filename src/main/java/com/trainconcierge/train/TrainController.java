package com.trainconcierge.train;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.train.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trains")
@RequiredArgsConstructor
@Tag(name = "Trains", description = "Browse and search available trains")
public class TrainController {

    private final TrainService trainService;

    @Operation(summary = "List all trains", description = "Paginated list of trains. Set includeInactive=true to show deactivated trains.")
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedTrainResponse>> getAllTrains(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "trainNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "false") boolean includeInactive) {

        if (size < 1) size = 10;
        if (size > 100) size = 100;
        if (page < 0) page = 0;

        PaginatedTrainResponse response = trainService.getAllTrains(
                page, size, sortBy, sortDir, includeInactive);
        return ResponseEntity.ok(ApiResponse.ok("Trains retrieved successfully.", response));
    }

    @Operation(summary = "Get train by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TrainResponse>> getTrainById(@PathVariable Long id) {
        TrainResponse response = trainService.getTrainById(id);
        return ResponseEntity.ok(ApiResponse.ok("Train retrieved successfully.", response));
    }

    @Operation(summary = "Search trains by route and date", description = "Returns active trains serving the given origin→destination on the given date.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TrainSearchResult>>> searchTrains(
            @RequestParam String originStation,
            @RequestParam String destinationStation,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate) {

        List<TrainSearchResult> results = trainService.searchTrains(
                originStation, destinationStation, journeyDate);

        String message = results.isEmpty()
                ? "No trains found for the specified route and date."
                : "Search completed successfully. Found " + results.size() + " train(s).";

        return ResponseEntity.ok(ApiResponse.ok(message, results));
    }
}

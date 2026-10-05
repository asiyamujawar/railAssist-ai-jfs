package com.trainconcierge.admin;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.train.TrainService;
import com.trainconcierge.train.dto.CreateTrainRequest;
import com.trainconcierge.train.dto.TrainResponse;
import com.trainconcierge.train.dto.UpdateTrainRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/trains")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Trains", description = "Admin-only train master data management")
public class AdminTrainController {

    private final TrainService trainService;

    @Operation(summary = "Create a train [ADMIN]", description = "Creates a new train master record. Requires ROLE_ADMIN.")
    @PostMapping
    public ResponseEntity<ApiResponse<TrainResponse>> createTrain(
            @Valid @RequestBody CreateTrainRequest request) {

        TrainResponse response = trainService.createTrain(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Train created successfully.", response));
    }

    @Operation(summary = "Update a train [ADMIN]", description = "Updates train details by ID. Requires ROLE_ADMIN.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TrainResponse>> updateTrain(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTrainRequest request) {

        TrainResponse response = trainService.updateTrain(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Train updated successfully.", response));
    }

    @Operation(summary = "Deactivate a train [ADMIN]", description = "Marks a train as inactive, hiding it from public search results.")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<TrainResponse>> deactivateTrain(@PathVariable Long id) {
        TrainResponse response = trainService.deactivateTrain(id);
        return ResponseEntity.ok(ApiResponse.ok("Train deactivated successfully.", response));
    }
}

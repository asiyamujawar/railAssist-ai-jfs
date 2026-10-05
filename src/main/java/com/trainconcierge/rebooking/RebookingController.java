package com.trainconcierge.rebooking;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.rebooking.dto.RebookRequest;
import com.trainconcierge.rebooking.dto.RebookingHistoryResponse;
import com.trainconcierge.rebooking.dto.RebookingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for executing simulated train rebookings and retrieving rebooking history.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Rebooking", description = "Execute passenger rebooking onto an alternative train after a disruption")
public class RebookingController {

    private final RebookingService rebookingService;

    /**
     * POST /api/disruptions/{id}/rebook
     * Rebooks a disrupted passenger onto a selected alternative train recommendation.
     */
    @Operation(summary = "Rebook onto alternative train", description = "Rebooks a disrupted passenger onto a selected recommendation. Can be triggered manually or via autonomous rebooking.")
    @PostMapping("/api/disruptions/{id}/rebook")
    public ResponseEntity<ApiResponse<RebookingResponse>> rebook(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long disruptionId,
            @Valid @RequestBody RebookRequest request) {

        RebookingResponse response = rebookingService.rebook(disruptionId, request, userDetails.getUsername());
        String msg = response.isAutonomous()
                ? "Simulated auto-rebooking executed successfully."
                : "Passenger rebooking onto alternative train confirmed successfully.";

        return ResponseEntity.ok(ApiResponse.ok(msg, response));
    }

    /**
     * GET /api/journeys/{id}/rebooking-history
     * Retrieves all rebooking history audit records for a given journey.
     */
    @Operation(summary = "Get rebooking history for a journey", description = "Returns all rebooking audit records for the given journey. Access is restricted to the journey owner.")
    @GetMapping("/api/journeys/{id}/rebooking-history")
    public ResponseEntity<ApiResponse<List<RebookingHistoryResponse>>> getRebookingHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long journeyId) {

        List<RebookingHistoryResponse> history = rebookingService.getRebookingHistory(journeyId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}

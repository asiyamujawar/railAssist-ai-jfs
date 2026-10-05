package com.trainconcierge.coordination;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.exception.BusinessRuleException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.rebooking.RebookingHistory;
import com.trainconcierge.rebooking.RebookingHistoryRepository;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for travel coordination workflow operations.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Travel Coordination", description = "Hotel and cab coordination triggered after a train rebooking")
public class TravelCoordinationController {

    private final TravelCoordinationService coordinationService;
    private final RebookingHistoryRepository rebookingHistoryRepository;
    private final JourneyRepository journeyRepository;

    /**
     * POST /api/journeys/{id}/coordination/trigger
     * Manually triggers travel coordination for a journey's latest train rebooking.
     */
    @Operation(summary = "Trigger travel coordination", description = "Triggers hotel and cab rescheduling for a journey's latest rebooking. Requires a prior completed rebooking.")
    @PostMapping("/api/journeys/{id}/coordination/trigger")
    public ResponseEntity<ApiResponse<TravelCoordinationResponse>> triggerCoordination(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long journeyId) {

        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey", "id", journeyId));

        List<RebookingHistory> histories = rebookingHistoryRepository.findByJourney(journey);
        if (histories.isEmpty()) {
            throw new BusinessRuleException(
                    "No completed rebooking history found for this journey. Travel coordination requires a prior train rebooking.",
                    ErrorCode.INVALID_OPERATION);
        }

        RebookingHistory latestHistory = histories.get(histories.size() - 1);
        TravelCoordinationResponse response = coordinationService.coordinateTravelForRebooking(latestHistory.getId());

        return ResponseEntity.ok(ApiResponse.ok("Travel coordination workflow executed successfully.", response));
    }

    /**
     * POST /api/coordination/{id}/retry
     * Retries a failed or partial travel coordination record.
     */
    @Operation(summary = "Retry failed travel coordination", description = "Retries a previously failed or partial coordination record. Access restricted to the journey owner.")
    @PostMapping("/api/coordination/{id}/retry")
    public ResponseEntity<ApiResponse<TravelCoordinationResponse>> retryCoordination(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long recordId) {

        TravelCoordinationResponse response = coordinationService.retryCoordination(recordId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Travel coordination retry executed successfully.", response));
    }

    /**
     * GET /api/journeys/{id}/coordination
     * Retrieves all travel coordination audit records for a given journey.
     */
    @Operation(summary = "Get coordination history for a journey", description = "Returns all travel coordination records (hotel + cab) for the given journey. Access restricted to the owner.")
    @GetMapping("/api/journeys/{id}/coordination")
    public ResponseEntity<ApiResponse<List<TravelCoordinationResponse>>> getCoordinationHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long journeyId) {

        List<TravelCoordinationResponse> history = coordinationService.getCoordinationHistoryForJourney(journeyId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}

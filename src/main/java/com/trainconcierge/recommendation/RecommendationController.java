package com.trainconcierge.recommendation;

import com.trainconcierge.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for generating and retrieving alternative train recommendations.
 */
@RestController
@RequestMapping("/api/disruptions/{id}/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Generate and retrieve alternative train options for disrupted journeys")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * POST /api/disruptions/{id}/recommendations
     * Generates and ranks alternative train recommendations for a disruption event.
     */
    @Operation(summary = "Generate alternative recommendations", description = "Runs the ranking algorithm to find and score alternative trains for a disruption event.")
    @PostMapping
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> generateRecommendations(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long disruptionId) {

        List<RecommendationResponse> recommendations = recommendationService.generateRecommendations(disruptionId, userDetails.getUsername());
        String message = recommendations.isEmpty()
                ? "No eligible alternative train schedules found for this disruption."
                : "Generated " + recommendations.size() + " ranked recommendation(s) successfully.";

        return ResponseEntity.ok(ApiResponse.ok(message, recommendations));
    }

    /**
     * GET /api/disruptions/{id}/recommendations
     * Retrieves existing alternative train recommendations for a disruption event.
     */
    @Operation(summary = "Get existing recommendations", description = "Returns previously generated recommendations for a disruption event without re-ranking.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> getRecommendations(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long disruptionId) {

        List<RecommendationResponse> recommendations = recommendationService.getRecommendationsForDisruption(disruptionId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(recommendations));
    }
}

package com.trainconcierge.disruption;

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
 * REST controller for retrieving disruption events affecting user journeys.
 */
@RestController
@RequestMapping("/api/disruptions")
@RequiredArgsConstructor
@Tag(name = "Disruptions", description = "View train disruption events for user journeys")
public class DisruptionController {

    private final DisruptionQueryService queryService;

    /**
     * GET /api/disruptions/my
     * Returns all disruption events linked to the authenticated user's journeys.
     */
    @Operation(summary = "Get my disruptions", description = "Returns all disruption events (DELAY, CANCELLATION) linked to the authenticated user's journeys.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<DisruptionEventResponse>>> getMyDisruptions(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<DisruptionEventResponse> disruptions = queryService.getMyDisruptions(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(disruptions));
    }

    /**
     * GET /api/disruptions/{id}
     * Returns a specific disruption event by ID. Access restricted to journey owner or admin.
     */
    @Operation(summary = "Get disruption by ID", description = "Returns a specific disruption event. Access is restricted to the journey owner.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisruptionEventResponse>> getDisruptionById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        DisruptionEventResponse response = queryService.getDisruptionById(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

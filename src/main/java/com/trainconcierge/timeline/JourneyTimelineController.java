package com.trainconcierge.timeline;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.timeline.dto.JourneyTimelineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for retrieving unified journey timeline and audit history.
 *
 * <p>Phase 18 — Journey Timeline & Audit History Module.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Journey Timeline", description = "Unified chronological audit timeline for a journey (booking → disruption → rebook → coordination)")
public class JourneyTimelineController {

    private final JourneyTimelineService journeyTimelineService;

    /**
     * GET /api/journeys/{id}/timeline
     *
     * <p>Retrieves a unified chronological timeline for a specific journey,
     * explaining what happened and why across all 11 audit event types.</p>
     *
     * @param userDetails authenticated user details
     * @param id          journey identifier
     * @return 200 OK with unified timeline DTO
     */
    @Operation(summary = "Get journey timeline", description = "Returns a unified, chronological audit timeline for a journey, covering booking, disruption, recommendation, rebooking, coordination, and notification events.")
    @GetMapping("/api/journeys/{id}/timeline")
    public ResponseEntity<ApiResponse<JourneyTimelineResponse>> getJourneyTimeline(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long id) {

        log.info("REST request to fetch timeline for journey id={} by user={}", id, userDetails.getUsername());
        JourneyTimelineResponse response = journeyTimelineService.getJourneyTimeline(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Journey timeline retrieved successfully.", response));
    }
}

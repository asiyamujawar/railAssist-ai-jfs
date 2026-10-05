package com.trainconcierge.seat;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.seat.dto.CreateSeatAlertRequest;
import com.trainconcierge.seat.dto.SeatAlertSubscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the Smart Seat Availability Alert module.
 *
 * <p>All endpoints require an authenticated user (JWT Bearer token).
 * Users can only manage their own subscriptions.</p>
 */
@RestController
@RequestMapping("/api/seat-alerts")
@RequiredArgsConstructor
@Tag(name = "Seat Alerts", description = "Subscribe to and manage smart seat availability alerts")
public class SeatAlertController {

    private final SeatAlertSubscriptionService subscriptionService;

    /**
     * POST /api/seat-alerts
     * Subscribe to seat availability alerts for a specific schedule and seat class.
     */
    @Operation(summary = "Subscribe to seat alerts", description = "Creates an alert subscription for a specific schedule and seat class. Notified when available count reaches threshold.")
    @PostMapping
    public ResponseEntity<ApiResponse<SeatAlertSubscriptionResponse>> subscribe(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateSeatAlertRequest request) {

        SeatAlertSubscriptionResponse response =
                subscriptionService.subscribe(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        "Seat alert subscription created. You will be notified when the " +
                        "available seat count reaches or drops to " + request.getThreshold() + ".",
                        response));
    }

    /**
     * GET /api/seat-alerts/my
     * Returns all alert subscriptions (active and inactive) for the authenticated user.
     */
    @Operation(summary = "Get my seat alerts", description = "Returns all alert subscriptions (active and inactive) for the authenticated user.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<SeatAlertSubscriptionResponse>>> getMyAlerts(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<SeatAlertSubscriptionResponse> alerts =
                subscriptionService.getMyAlerts(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(alerts));
    }

    /**
     * PATCH /api/seat-alerts/{id}/deactivate
     * Deactivate an active alert subscription. Only the owning user may do this.
     */
    @Operation(summary = "Deactivate a seat alert", description = "Deactivates an active subscription. Only the owning user may call this.")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<SeatAlertSubscriptionResponse>> deactivate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {

        SeatAlertSubscriptionResponse response =
                subscriptionService.deactivate(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.ok(
                "Alert subscription deactivated successfully.", response));
    }
}

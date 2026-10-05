package com.trainconcierge.cab;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.cab.dto.CabBookingResponse;
import com.trainconcierge.cab.dto.CreateCabBookingRequest;
import com.trainconcierge.cab.dto.RescheduleCabRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cabs")
@RequiredArgsConstructor
@Tag(name = "Cab Bookings", description = "Simulated cab reservations linked to journeys (no real provider contacted)")
public class CabBookingController {

    private final CabBookingService cabBookingService;

    @Operation(summary = "Create a cab booking [SIMULATED]", description = "Simulates a cab pickup reservation for a journey. No real cab provider is contacted.")
    @PostMapping
    public ResponseEntity<ApiResponse<CabBookingResponse>> createCabBooking(
            @Valid @RequestBody CreateCabBookingRequest request) {

        CabBookingResponse response = cabBookingService.createCabBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        "Cab reservation simulated successfully and linked to the journey.",
                        response));
    }

    @Operation(summary = "Get cab bookings for a journey")
    @GetMapping("/journey/{journeyId}")
    public ResponseEntity<ApiResponse<List<CabBookingResponse>>> getCabsForJourney(
            @PathVariable Long journeyId) {

        List<CabBookingResponse> response =
                cabBookingService.getCabsForJourney(journeyId);

        return ResponseEntity.ok(ApiResponse.ok(
                "Cab bookings for journey retrieved successfully.",
                response));
    }

    @Operation(summary = "Reschedule a cab booking [SIMULATED]", description = "Updates the scheduled pickup time of a cab booking. Simulated only.")
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<CabBookingResponse>> rescheduleCab(
            @PathVariable Long id,
            @RequestBody RescheduleCabRequest request) {

        CabBookingResponse response = cabBookingService.rescheduleCab(id, request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Cab reservation scheduled pickup time rescheduled (simulated) and change recorded.",
                response));
    }
}

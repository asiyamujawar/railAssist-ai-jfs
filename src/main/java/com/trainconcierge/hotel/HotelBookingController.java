package com.trainconcierge.hotel;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.hotel.dto.CreateHotelBookingRequest;
import com.trainconcierge.hotel.dto.HotelBookingResponse;
import com.trainconcierge.hotel.dto.RescheduleHotelRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@Tag(name = "Hotel Bookings", description = "Simulated hotel reservations linked to journeys (no real provider contacted)")
public class HotelBookingController {

    private final HotelBookingService hotelBookingService;

    @Operation(summary = "Create a hotel booking [SIMULATED]", description = "Simulates a hotel reservation for a journey. No real hotel provider is contacted.")
    @PostMapping
    public ResponseEntity<ApiResponse<HotelBookingResponse>> createHotelBooking(
            @Valid @RequestBody CreateHotelBookingRequest request) {

        HotelBookingResponse response = hotelBookingService.createHotelBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        "Hotel reservation simulated successfully and linked to the journey.",
                        response));
    }

    @Operation(summary = "Get hotel bookings for a journey")
    @GetMapping("/journey/{journeyId}")
    public ResponseEntity<ApiResponse<List<HotelBookingResponse>>> getHotelsForJourney(
            @PathVariable Long journeyId) {

        List<HotelBookingResponse> response =
                hotelBookingService.getHotelsForJourney(journeyId);

        return ResponseEntity.ok(ApiResponse.ok(
                "Hotel bookings for journey retrieved successfully.",
                response));
    }

    @Operation(summary = "Reschedule a hotel booking [SIMULATED]", description = "Updates the check-in/check-out dates of a hotel booking. Simulated only.")
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<HotelBookingResponse>> rescheduleHotel(
            @PathVariable Long id,
            @RequestBody RescheduleHotelRequest request) {

        HotelBookingResponse response = hotelBookingService.rescheduleHotel(id, request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Hotel reservation dates rescheduled (simulated) and change recorded.",
                response));
    }
}

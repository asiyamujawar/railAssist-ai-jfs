package com.trainconcierge.booking;

import com.trainconcierge.booking.dto.BookTrainRequest;
import com.trainconcierge.booking.dto.BookingResponse;
import com.trainconcierge.booking.dto.PaginatedBookingResponse;
import com.trainconcierge.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Train Bookings", description = "Create, retrieve, and cancel train bookings")
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Create a train booking", description = "Books seats on a train schedule for the authenticated user. Decrements seat availability atomically.")
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody BookTrainRequest request) {

        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Booking created successfully.", response));
    }

    @Operation(summary = "Get my bookings", description = "Returns a paginated list of all bookings belonging to the authenticated user.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PaginatedBookingResponse>> getMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        PaginatedBookingResponse response =
                bookingService.getMyBookings(page, size, sortBy, sortDir);

        return ResponseEntity.ok(
                ApiResponse.ok("Booking history retrieved successfully.", response));
    }

    @Operation(summary = "Get booking by ID", description = "Retrieves a specific booking. Access is restricted to the booking owner.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingById(@PathVariable Long id) {
        BookingResponse response = bookingService.getBookingById(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Booking retrieved successfully.", response));
    }

    @Operation(summary = "Cancel a booking", description = "Cancels a CONFIRMED booking and releases the reserved seat inventory back to the pool.")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(@PathVariable Long id) {
        BookingResponse response = bookingService.cancelBooking(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Booking cancelled successfully. Seats have been released.",
                        response));
    }
}

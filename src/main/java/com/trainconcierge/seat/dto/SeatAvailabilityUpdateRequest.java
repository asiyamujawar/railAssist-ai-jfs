package com.trainconcierge.seat.dto;

import com.trainconcierge.seat.SeatClass;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatAvailabilityUpdateRequest {

    @NotNull(message = "Seat class is required")
    private SeatClass seatClass;

    @Min(value = 0, message = "Total seats must not be negative")
    private Integer totalSeats;

    @Min(value = 0, message = "Available seats must not be negative")
    private Integer availableSeats;

    @Min(value = 0, message = "Booked seats must not be negative")
    private Integer bookedSeats;

    @PositiveOrZero(message = "Fare must be zero or positive")
    private BigDecimal fare;
}

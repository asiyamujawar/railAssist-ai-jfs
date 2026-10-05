package com.trainconcierge.booking.dto;

import com.trainconcierge.seat.SeatClass;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookTrainRequest {

    @NotNull(message = "Schedule ID is required.")
    private Long scheduleId;

    @NotNull(message = "Seat class is required.")
    private SeatClass seatClass;

    @NotNull(message = "Number of passengers is required.")
    @Min(value = 1, message = "At least 1 passenger is required.")
    @Max(value = 9, message = "Maximum 9 passengers per booking.")
    private Integer passengerCount;

    @Size(max = 3, message = "Currency code must be ISO 4217 format (max 3 chars).")
    @Builder.Default
    private String currency = "INR";
}

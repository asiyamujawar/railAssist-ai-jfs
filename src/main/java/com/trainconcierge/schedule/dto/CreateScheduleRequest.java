package com.trainconcierge.schedule.dto;

import com.trainconcierge.seat.SeatClass;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleRequest {

    @NotNull(message = "Train ID is required")
    private Long trainId;

    @NotNull(message = "Scheduled date is required")
    private LocalDate scheduledDate;

    @NotNull(message = "Scheduled departure time is required")
    private LocalTime scheduledDeparture;

    @NotNull(message = "Scheduled arrival time is required")
    private LocalTime scheduledArrival;

    @Size(max = 10, message = "Platform must not exceed 10 characters")
    private String platform;

    @PositiveOrZero(message = "Base fare must be zero or positive")
    private BigDecimal baseFare;

    @NotEmpty(message = "At least one seat class configuration is required")
    private List<SeatClassConfig> seatClasses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatClassConfig {

        @NotNull(message = "Seat class is required")
        private SeatClass seatClass;

        @NotNull(message = "Total seats is required")
        @Min(value = 0, message = "Total seats must not be negative")
        private Integer totalSeats;

        @NotNull(message = "Fare is required")
        @PositiveOrZero(message = "Fare must be zero or positive")
        private BigDecimal fare;
    }
}

package com.trainconcierge.hotel.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHotelBookingRequest {

    @NotNull(message = "Journey ID is required.")
    private Long journeyId;

    @NotBlank(message = "Hotel name is required.")
    @Size(max = 150, message = "Hotel name must be at most 150 characters.")
    private String hotelName;

    @NotBlank(message = "City is required.")
    @Size(max = 100, message = "City must be at most 100 characters.")
    private String city;

    @NotBlank(message = "Hotel address is required.")
    @Size(max = 200, message = "Hotel address must be at most 200 characters.")
    private String hotelAddress;

    @NotNull(message = "Check-in date is required.")
    private LocalDate checkInDate;

    @NotNull(message = "Check-out date is required.")
    private LocalDate checkOutDate;

    @PositiveOrZero(message = "Number of nights, if supplied, must be non-negative.")
    private Integer numberOfNights;

    @DecimalMin(value = "0.00", inclusive = true, message = "Total cost must be 0.00 or higher.")
    private BigDecimal totalCost;

    @Size(max = 3, message = "Currency code must be ISO 4217 format (max 3 characters).")
    @Builder.Default
    private String currency = "GBP";
}

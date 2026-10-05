package com.trainconcierge.cab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCabBookingRequest {

    @NotNull(message = "journeyId is required")
    private Long journeyId;

    @NotBlank(message = "pickupAddress is required")
    private String pickupAddress;

    @NotBlank(message = "dropoffAddress is required")
    private String dropoffAddress;

    @NotNull(message = "scheduledPickupTime is required")
    private LocalDateTime scheduledPickupTime;

    private String cabType;

    private BigDecimal estimatedFare;

    private String currency;

    private String provider;
}

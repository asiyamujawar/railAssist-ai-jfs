package com.trainconcierge.cab.dto;

import com.trainconcierge.cab.CabBookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabBookingResponse {

    private Long id;
    private Long userId;
    private Long journeyId;
    private String pickupAddress;
    private String dropoffAddress;
    private LocalDateTime scheduledPickupTime;
    private LocalDateTime actualPickupTime;
    private String cabType;
    private BigDecimal estimatedFare;
    private BigDecimal actualFare;
    private String currency;
    private String provider;
    private CabBookingStatus status;
    private String cabBookingReference;
    private String externalBookingRef;
    private Instant confirmedAt;
    private Instant rescheduledAt;
    private List<CabModificationResponse> modifications;
    private Instant createdAt;
    private Instant updatedAt;
}

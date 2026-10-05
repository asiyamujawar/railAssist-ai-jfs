package com.trainconcierge.hotel.dto;

import com.trainconcierge.hotel.HotelBookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelBookingResponse {

    private Long id;
    private Long userId;
    private Long journeyId;

    private String hotelName;
    private String city;
    private String hotelAddress;

    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer numberOfNights;

    private BigDecimal totalCost;
    private String currency;

    private HotelBookingStatus status;
    private String hotelBookingReference;
    private String externalBookingRef;

    private Instant confirmedAt;
    private Instant rescheduledAt;

    private List<HotelModificationResponse> modifications;

    private Instant createdAt;
    private Instant updatedAt;
}

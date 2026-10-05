package com.trainconcierge.booking.dto;

import com.trainconcierge.journey.JourneyStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingJourneyResponse {

    private Long id;
    private String originStation;
    private String destinationStation;
    private LocalDate travelDate;
    private JourneyStatus status;
    private BigDecimal totalCost;
    private String currency;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}

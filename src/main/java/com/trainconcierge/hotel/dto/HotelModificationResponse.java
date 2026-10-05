package com.trainconcierge.hotel.dto;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelModificationResponse {

    private LocalDate oldCheckInDate;
    private LocalDate newCheckInDate;
    private LocalDate oldCheckOutDate;
    private LocalDate newCheckOutDate;
    private Instant rescheduledAt;
    private String simulationProvider;
}

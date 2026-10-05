package com.trainconcierge.seat.dto;

import com.trainconcierge.seat.SeatClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatAvailabilityResponse {

    private Long id;

    private Long scheduleId;

    private SeatClass seatClass;

    private Integer totalSeats;

    private Integer availableSeats;

    private Integer bookedSeats;

    private BigDecimal fare;

    private Instant updatedAt;
}

package com.trainconcierge.booking.dto;

import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.seat.SeatClass;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private String bookingReference;

    private Long userId;
    private String passengerName;

    private Long scheduleId;
    private Long trainId;
    private String trainNumber;
    private String trainName;
    private String originStation;
    private String destinationStation;
    private LocalDate journeyDate;
    private LocalTime scheduledDeparture;
    private LocalTime scheduledArrival;
    private String platform;

    private SeatClass seatClass;
    private Integer numberOfSeats;

    private BigDecimal baseFare;
    private BigDecimal totalFare;
    private String currency;

    private BookingStatus status;
    private Instant confirmedAt;
    private Instant cancelledAt;

    private String seatNumbers;
    private BookingJourneyResponse journey;

    private Instant createdAt;
    private Instant updatedAt;
}

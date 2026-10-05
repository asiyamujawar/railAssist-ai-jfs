package com.trainconcierge.schedule.dto;

import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.seat.dto.SeatAvailabilityResponse;
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
public class ScheduleSearchResult {

    private Long scheduleId;

    private Long trainId;

    private String trainNumber;

    private String trainName;

    private String operatorName;

    private String originStation;

    private String destinationStation;

    private LocalDate scheduledDate;

    private LocalTime scheduledDeparture;

    private LocalTime scheduledArrival;

    private Integer delayMinutes;

    private String platform;

    private boolean cancelled;

    private ScheduleStatus scheduleStatus;

    private BigDecimal baseFare;

    private List<SeatAvailabilityResponse> seatAvailability;
}

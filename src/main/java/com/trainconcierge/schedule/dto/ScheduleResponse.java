package com.trainconcierge.schedule.dto;

import com.trainconcierge.schedule.ScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {

    private Long id;

    private Long trainId;

    private String trainNumber;

    private String trainName;

    private String originStation;

    private String destinationStation;

    private LocalDate scheduledDate;

    private LocalTime scheduledDeparture;

    private LocalTime scheduledArrival;

    private LocalTime actualDeparture;

    private LocalTime actualArrival;

    private Integer delayMinutes;

    private String platform;

    private boolean cancelled;

    private ScheduleStatus scheduleStatus;

    private BigDecimal baseFare;

    private Instant createdAt;

    private Instant updatedAt;
}

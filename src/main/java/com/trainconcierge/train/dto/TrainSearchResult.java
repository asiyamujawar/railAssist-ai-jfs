package com.trainconcierge.train.dto;

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
public class TrainSearchResult {

    private Long trainId;

    private String trainNumber;

    private String trainName;

    private String operatorName;

    private String originStation;

    private String destinationStation;

    private Integer totalSeats;

    private LocalDate journeyDate;

    private LocalTime scheduledDeparture;

    private LocalTime scheduledArrival;

    private Integer delayMinutes;

    private String platform;

    private boolean cancelled;

    private List<AvailableClass> availableClasses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailableClass {
        private String seatClass;
        private Integer availableSeats;
        private BigDecimal fare;
    }
}

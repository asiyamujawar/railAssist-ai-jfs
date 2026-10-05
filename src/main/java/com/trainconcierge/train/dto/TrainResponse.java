package com.trainconcierge.train.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainResponse {

    private Long id;

    private String trainNumber;

    private String trainName;

    private String operatorName;

    private String originStation;

    private String destinationStation;

    private Integer totalSeats;

    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;
}

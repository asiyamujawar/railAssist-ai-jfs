package com.trainconcierge.train.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTrainRequest {

    @Size(max = 150, message = "Train name must not exceed 150 characters")
    private String trainName;

    @Size(max = 100, message = "Operator name must not exceed 100 characters")
    private String operatorName;

    @Size(max = 100, message = "Origin station must not exceed 100 characters")
    private String originStation;

    @Size(max = 100, message = "Destination station must not exceed 100 characters")
    private String destinationStation;

    @Min(value = 1, message = "Total seats must be at least 1")
    @Max(value = 2000, message = "Total seats must not exceed 2000")
    private Integer totalSeats;

    private Boolean active;
}

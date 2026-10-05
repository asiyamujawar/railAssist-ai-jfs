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
public class CreateTrainRequest {

    @NotBlank(message = "Train number is required")
    @Size(max = 20, message = "Train number must not exceed 20 characters")
    private String trainNumber;

    @NotBlank(message = "Train name is required")
    @Size(max = 150, message = "Train name must not exceed 150 characters")
    private String trainName;

    @Size(max = 100, message = "Operator name must not exceed 100 characters")
    private String operatorName;

    @NotBlank(message = "Origin station is required")
    @Size(max = 100, message = "Origin station must not exceed 100 characters")
    private String originStation;

    @NotBlank(message = "Destination station is required")
    @Size(max = 100, message = "Destination station must not exceed 100 characters")
    private String destinationStation;

    @NotNull(message = "Total seats is required")
    @Min(value = 1, message = "Total seats must be at least 1")
    @Max(value = 2000, message = "Total seats must not exceed 2000")
    private Integer totalSeats;
}

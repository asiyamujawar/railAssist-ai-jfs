package com.trainconcierge.simulation;

import com.trainconcierge.train.TrainStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSimulatedStatusRequest {

    @NotNull(message = "status is required")
    private TrainStatus status;

    /**
     * Required when status is DELAYED — must be >= 1.
     * Ignored for ON_TIME, CANCELLED, PLATFORM_CHANGED.
     */
    @Min(value = 0, message = "delayMinutes must be 0 or greater")
    private Integer delayMinutes;

    /**
     * Required when status is PLATFORM_CHANGED.
     */
    private String platform;

    /**
     * Human-readable operator note attached to this status change.
     */
    private String message;
}

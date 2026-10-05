package com.trainconcierge.admin.simulation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body for the admin "trigger cancellation" simulation endpoint.
 *
 * <p><strong>SIMULATED SERVICE</strong> — no real railway provider is contacted.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerCancellationRequest {

    /** ID of the TrainSchedule to cancel. */
    @NotNull(message = "scheduleId is required")
    private Long scheduleId;

    /** Optional human-readable operator note. */
    private String reason;
}

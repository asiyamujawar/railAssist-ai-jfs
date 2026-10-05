package com.trainconcierge.admin.simulation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body for admin-triggered disruption evaluation of a specific journey.
 *
 * <p>Forces the disruption detection engine to evaluate the current train status
 * for all bookings linked to the given journey — useful for testing the
 * recommendation + rebooking pipeline without waiting for the scheduler.</p>
 *
 * <p><strong>Duplicate guard:</strong> If the journey already has an open
 * (non-resolved) disruption event, evaluation is skipped to prevent
 * duplicate alerts and rebooking attempts.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerDisruptionEvaluationRequest {

    /** ID of the Journey to evaluate for disruption. */
    @NotNull(message = "journeyId is required")
    private Long journeyId;
}

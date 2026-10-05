package com.trainconcierge.rebooking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request DTO for initiating a rebooking via
 * {@code POST /api/disruptions/{id}/rebook}.
 *
 * <p>The caller selects one of the previously generated recommendations.
 * If {@code autoMode} is true the system acts as a controlled demo
 * auto-rebooking; otherwise it proceeds as a user-approved rebooking.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RebookRequest {

    /** ID of the selected {@link com.trainconcierge.recommendation.Recommendation}. */
    @NotNull(message = "recommendationId is required")
    private Long recommendationId;

    /**
     * If true, treat this as a controlled demo auto-rebooking.
     * If false (default), treat as a user-approved rebooking.
     */
    @Builder.Default
    private boolean autoMode = false;
}

package com.trainconcierge.admin.simulation.dto;

import com.trainconcierge.disruption.DisruptionSeverity;
import com.trainconcierge.disruption.DisruptionStatus;
import com.trainconcierge.disruption.DisruptionType;
import lombok.*;

import java.time.Instant;

/**
 * Response returned after an admin-triggered disruption evaluation for a single journey.
 *
 * <p>If a disruption event was created, the event details are populated.
 * If evaluation was skipped (e.g. duplicate guard fired), {@code skipped = true}
 * and a {@code skipReason} is set.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisruptionEvaluationResponse {

    /** ID of the journey that was evaluated. */
    private Long journeyId;

    /** ID of the schedule that was evaluated. */
    private Long scheduleId;

    /** Whether evaluation was skipped (e.g. duplicate open disruption). */
    private boolean skipped;

    /** Reason for skip, if applicable. */
    private String skipReason;

    /** ID of the newly created DisruptionEvent (null if skipped). */
    private Long disruptionEventId;

    /** Type of disruption detected (null if skipped). */
    private DisruptionType disruptionType;

    /** Severity of the disruption (null if skipped). */
    private DisruptionSeverity severity;

    /** Current disruption status (null if skipped). */
    private DisruptionStatus disruptionStatus;

    /** Whether the recommendation workflow was triggered. */
    private boolean recommendationTriggered;

    /** Timestamp of this evaluation. */
    private Instant evaluatedAt;

    /** Human-readable outcome summary. */
    private String summary;
}

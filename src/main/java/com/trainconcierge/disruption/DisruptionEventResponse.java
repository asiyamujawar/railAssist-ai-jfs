package com.trainconcierge.disruption;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for disruption event API endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisruptionEventResponse {

    private Long id;
    private Long scheduleId;
    private Long journeyId;
    private String trainNumber;
    private String originStation;
    private String destinationStation;
    private DisruptionType type;
    private DisruptionSeverity severity;
    private DisruptionStatus status;
    private Instant detectedAt;
    private Instant resolvedAt;
    private String description;
    private String operatorNotes;
    private Integer estimatedDelayMinutes;
    private String platform;
    private boolean resolved;
    private boolean rebookingTriggered;
    private boolean eligibleForRecommendation;

    public static DisruptionEventResponse fromEntity(DisruptionEvent event) {
        String origin = null;
        String dest = null;
        if (event.getSchedule() != null && event.getSchedule().getTrain() != null) {
            origin = event.getSchedule().getTrain().getOriginStation();
            dest = event.getSchedule().getTrain().getDestinationStation();
        } else if (event.getJourney() != null) {
            origin = event.getJourney().getOriginStation();
            dest = event.getJourney().getDestinationStation();
        }

        boolean eligible = event.getType() != DisruptionType.PLATFORM_CHANGE
                && (event.getType() == DisruptionType.CANCELLATION
                || event.getType() == DisruptionType.DIVERSION
                || (event.getEstimatedDelayMinutes() != null && event.getEstimatedDelayMinutes() >= 15));

        return DisruptionEventResponse.builder()
                .id(event.getId())
                .scheduleId(event.getSchedule() != null ? event.getSchedule().getId() : null)
                .journeyId(event.getJourney() != null ? event.getJourney().getId() : null)
                .trainNumber(event.getTrainNumber() != null ? event.getTrainNumber() :
                        (event.getSchedule() != null && event.getSchedule().getTrain() != null ? event.getSchedule().getTrain().getTrainNumber() : null))
                .originStation(origin)
                .destinationStation(dest)
                .type(event.getType())
                .severity(event.getSeverity())
                .status(event.getStatus())
                .detectedAt(event.getDetectedAt())
                .resolvedAt(event.getResolvedAt())
                .description(event.getDescription())
                .operatorNotes(event.getOperatorNotes())
                .estimatedDelayMinutes(event.getEstimatedDelayMinutes())
                .platform(event.getPlatform())
                .resolved(event.isResolved())
                .rebookingTriggered(event.isRebookingTriggered())
                .eligibleForRecommendation(eligible)
                .build();
    }
}

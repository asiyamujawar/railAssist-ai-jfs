package com.trainconcierge.disruption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Triggers the recommendation workflow for eligible disruption events.
 *
 * <p><strong>Design Constraint:</strong> This service initiates recommendation
 * processing for eligible disruptions without performing any booking, hotel,
 * or cab updates directly, maintaining strict isolation of concerns.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationWorkflowTrigger {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Triggers the recommendation workflow for an eligible disruption event.
     *
     * @param event the disruption event detected by the detection service
     */
    public void triggerWorkflow(DisruptionEvent event) {
        log.info("[RecommendationTrigger] Triggering recommendation workflow for DisruptionEvent id={} " +
                 "scheduleId={} journeyId={} type={} severity={}",
                event.getId(),
                event.getSchedule() != null ? event.getSchedule().getId() : null,
                event.getJourney() != null ? event.getJourney().getId() : null,
                event.getType(),
                event.getSeverity());

        // Publish Spring ApplicationEvent for recommendation consumers
        eventPublisher.publishEvent(new DisruptionDetectedEvent(this, event));
    }
}

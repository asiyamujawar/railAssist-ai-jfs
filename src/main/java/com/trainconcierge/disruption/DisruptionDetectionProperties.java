package com.trainconcierge.disruption;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configurable properties for the disruption detection engine.
 */
@Component
@ConfigurationProperties(prefix = "disruption.detection")
@Getter
@Setter
public class DisruptionDetectionProperties {

    /**
     * Minimum delay in minutes to consider a train DELAYED status as a disruption event.
     * Delays below this threshold are ignored or logged as non-disruptive.
     */
    private int minDelayMinutes = 15;

    /**
     * Threshold in minutes for MEDIUM severity delay.
     */
    private int mediumDelayMinutes = 15;

    /**
     * Threshold in minutes for HIGH severity delay.
     */
    private int highDelayMinutes = 60;

    /**
     * Disruption types eligible to trigger the recommendation workflow.
     */
    private List<String> eligibleForRecommendationTypes = List.of("CANCELLATION", "DELAY", "DIVERSION");
}

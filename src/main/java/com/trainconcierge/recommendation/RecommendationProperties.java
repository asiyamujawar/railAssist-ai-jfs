package com.trainconcierge.recommendation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable weights and rules for the Alternative Train Recommendation Engine.
 */
@Component
@ConfigurationProperties(prefix = "disruption.recommendation")
@Getter
@Setter
public class RecommendationProperties {

    /** Weight for arrival time proximity criteria (default 0.35). */
    private double weightArrival = 0.35;

    /** Weight for travel duration criteria (default 0.25). */
    private double weightDuration = 0.25;

    /** Weight for fare difference criteria (default 0.20). */
    private double weightFare = 0.20;

    /** Weight for seat availability criteria (default 0.20). */
    private double weightSeats = 0.20;

    /** Minimum available seats required for an alternative schedule to be eligible. */
    private int minSeatsAvailable = 1;

    /** Maximum window in hours to search for candidate alternative schedules. */
    private int maxSearchWindowHours = 24;
}

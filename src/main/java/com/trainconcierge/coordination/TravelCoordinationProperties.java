package com.trainconcierge.coordination;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration rules for Travel Coordination Workflow.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "coordination")
public class TravelCoordinationProperties {

    /** Buffer in minutes added to new train arrival time to calculate hotel check-in time (default: 45 min). */
    private int hotelBufferMinutes = 45;

    /** Buffer in minutes added to new train arrival time to calculate cab pickup time (default: 15 min). */
    private int cabBufferMinutes = 15;
}

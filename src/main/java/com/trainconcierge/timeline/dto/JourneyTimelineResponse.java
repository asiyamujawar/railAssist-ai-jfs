package com.trainconcierge.timeline.dto;

import com.trainconcierge.journey.JourneyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Unified timeline response DTO for a Journey, designed for React timeline components.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyTimelineResponse {

    private Long journeyId;
    private String originStation;
    private String destinationStation;
    private LocalDate travelDate;
    private JourneyStatus status;
    private Integer totalEvents;
    private List<JourneyTimelineEventResponse> events;
}

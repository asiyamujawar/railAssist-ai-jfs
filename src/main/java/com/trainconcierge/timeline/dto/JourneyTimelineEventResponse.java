package com.trainconcierge.timeline.dto;

import com.trainconcierge.timeline.TimelineEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Single event entry in a journey timeline, formatted for a React timeline component.
 * Explains what happened and why.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyTimelineEventResponse {

    /** Unique event identifier suitable for React key prop (e.g. "evt-booking-1"). */
    private String id;

    /** Type of timeline event. */
    private TimelineEventType eventType;

    /** Concise title suitable for header node in timeline. */
    private String title;

    /** Short summary for collapsed timeline view. */
    private String summary;

    /** Contextual narrative explaining what happened and why. */
    private String explanation;

    /** Exact timestamp of the event for chronological sorting and display. */
    private Instant timestamp;

    /** Entity category (e.g. "BOOKING", "HOTEL_BOOKING", "DISRUPTION_EVENT"). */
    private String entityType;

    /** Primary ID or reference code of the underlying domain entity. */
    private String entityId;

    /** Recommended UI badge/icon color tag for React (e.g. "info", "success", "warning", "danger"). */
    private String uiBadgeColor;

    /** Key-value metadata containing domain-specific details. */
    private Map<String, Object> metadata;
}

package com.trainconcierge.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.trainconcierge.notification.NotificationChannel;
import com.trainconcierge.notification.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Read-only DTO returned by the notification REST API.
 *
 * <p>Follows the same envelope pattern used by all other TrainConcierge
 * responses — wrapped in {@link com.trainconcierge.common.ApiResponse}.</p>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {

    private final Long id;

    private final NotificationType type;

    private final NotificationChannel channel;

    private final String title;

    private final String body;

    /**
     * Optional deep-link reference (booking ID, disruption ID, etc.)
     * provided as a plain string so the client can navigate to the item.
     */
    private final String referenceId;

    private final String referenceType;

    /** Whether the notification has been read by the user. */
    private final boolean read;

    private final Instant readAt;

    private final Instant createdAt;
}

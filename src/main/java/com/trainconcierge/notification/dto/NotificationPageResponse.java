package com.trainconcierge.notification.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Paginated wrapper for a page of {@link NotificationResponse} items.
 *
 * <p>Mirrors the paginated response pattern used elsewhere in the application
 * (e.g. PaginatedBookingResponse) so clients receive consistent metadata.</p>
 */
@Getter
@Builder
public class NotificationPageResponse {

    private final List<NotificationResponse> notifications;

    private final int page;

    private final int size;

    private final long totalElements;

    private final int totalPages;

    private final boolean first;

    private final boolean last;

    /** Total count of unread notifications for the authenticated user. */
    private final long unreadCount;
}

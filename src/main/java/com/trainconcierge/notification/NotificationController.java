package com.trainconcierge.notification;

import com.trainconcierge.common.ApiResponse;
import com.trainconcierge.notification.dto.NotificationPageResponse;
import com.trainconcierge.notification.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller exposing the Phase 17 in-app notification APIs.
 *
 * <p>All endpoints require a valid JWT bearer token. Users can only access
 * their own notifications; cross-user access is blocked by
 * {@link NotificationService#markAsRead(Long)} via ownership check.</p>
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET    /api/notifications/my              — paginated list (newest first)
 *   GET    /api/notifications/unread-count    — count of unread items
 *   PATCH  /api/notifications/{id}/read       — mark single notification as read
 *   PATCH  /api/notifications/read-all        — mark all as read
 * </pre>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications — list, count unread, and mark as read")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Returns a paginated, newest-first list of the authenticated user's
     * notifications, along with the current unread count.
     *
     * @param page 0-indexed page number (default 0)
     * @param size page size, capped at 100 (default 20)
     */
    @Operation(summary = "Get my notifications", description = "Paginated list of all notifications for the authenticated user, newest first. Includes unread count.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        NotificationPageResponse response = notificationService.getMyNotifications(page, size);
        String msg = response.getTotalElements() == 0
                ? "No notifications found."
                : response.getTotalElements() + " notification(s) retrieved. Unread: " + response.getUnreadCount() + ".";
        return ResponseEntity.ok(ApiResponse.ok(msg, response));
    }

    /**
     * Returns only the count of unread notifications for the authenticated user.
     * Useful for badge rendering on the client without fetching full payloads.
     */
    @Operation(summary = "Get unread notification count", description = "Lightweight endpoint for badge rendering — returns only the count of unread items.")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount() {
        long count = notificationService.getUnreadCount();
        return ResponseEntity.ok(ApiResponse.ok(
                "Unread notification count retrieved.",
                Map.of("unreadCount", count)));
    }

    /**
     * Marks a single notification as read.
     *
     * <p>Returns 403 Forbidden if the notification does not belong to the
     * authenticated user. Returns 404 if not found.</p>
     *
     * @param id notification DB id
     */
    @Operation(summary = "Mark notification as read", description = "Marks a single notification as read. Returns 403 if the notification belongs to another user.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable Long id) {

        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok("Notification marked as read.", response));
    }

    /**
     * Marks ALL unread notifications of the authenticated user as read.
     *
     * @return number of notifications updated
     */
    @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications of the authenticated user as read in one call.")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead() {
        int updated = notificationService.markAllAsRead();
        String msg = updated == 0
                ? "No unread notifications to mark."
                : updated + " notification(s) marked as read.";
        return ResponseEntity.ok(ApiResponse.ok(msg, Map.of("updatedCount", updated)));
    }
}
